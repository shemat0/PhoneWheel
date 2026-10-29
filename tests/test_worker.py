import json
from pathlib import Path
import sys
import threading
import time
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'receiver'))
from control_worker import ControlWorker
from gamepad_output import response_curve


def wait_for(predicate, timeout=2):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        if predicate():
            return
        time.sleep(.003)
    raise AssertionError('Worker condition timed out')


class Output:
    def __init__(self, gate=None):
        self.response_exponent = .75
        self.last_steer = 0.
        self.state = {}
        self.calls = []
        self.ready = threading.Event()
        self.gate = gate
        self.closed = False
        self.fail_resets = 0

    def note(self, name):
        self.calls.append((name, threading.get_ident()))

    def ensure_ready(self):
        self.note('ready'); self.ready.set()
        if self.gate and not self.gate.wait(2):
            raise OSError('Injected slow driver')

    def apply(self, **state):
        self.note('apply')
        self.state = dict(state)
        self.last_steer = response_curve(state['steer'], self.response_exponent)

    def neutral(self):
        self.note('neutral')
        if self.fail_resets:
            self.fail_resets -= 1
            raise OSError('Injected reset failure')
        self.last_steer = 0.
        self.state = dict(steer=0., gas=False, brake=False, nitro=False, handbrake=False)

    def close(self):
        self.note('close'); self.closed = True; self.neutral()


class Stream:
    def __init__(self):
        self.condition = threading.Condition()
        self.incoming = bytearray()
        self.outgoing = bytearray()
        self.closed = False
        self.error = False

    @property
    def in_waiting(self):
        with self.condition:
            if self.error:
                raise OSError('Injected Bluetooth disconnect')
            return len(self.incoming)

    def feed(self, data):
        with self.condition:
            self.incoming.extend(data); self.condition.notify_all()

    def read(self, count):
        with self.condition:
            if count and not self.incoming:
                self.condition.wait(.003)
            data = bytes(self.incoming[:count]); del self.incoming[:count]
            return data

    def write(self, data):
        with self.condition:
            self.outgoing.extend(data)
            return len(data)

    def reset_input_buffer(self):
        with self.condition:
            self.incoming.clear()

    def reset_output_buffer(self):
        with self.condition:
            self.outgoing.clear()

    def close(self):
        with self.condition:
            self.closed = True; self.condition.notify_all()


class WorkerTests(unittest.TestCase):
    def setUp(self):
        self.output = Output()
        self.stream = Stream()
        self.worker = ControlWorker('TEST', lambda: self.output, lambda port: self.stream)

    def tearDown(self):
        self.assertTrue(self.worker.stop())
        self.assertTrue(self.output.closed)
        self.assertTrue(all(owner == self.worker.thread.ident for _, owner in self.output.calls))

    def arm(self):
        self.worker.command('arm', 'TEST-COM')
        wait_for(lambda: self.worker.snapshot()['armed'] and not self.worker.snapshot()['busy'])

    def feed(self):
        packet = dict(token='TEST', session='worker-stream-123', seq=1, steer=.5,
                      gas=True, brake=False, up=False, down=False, nitro=True, handbrake=True)
        self.stream.feed((json.dumps(packet) + '\n').encode())

    def test_control_and_timeout_run_without_ui_poll(self):
        self.arm(); self.feed()
        wait_for(lambda: self.output.state.get('nitro', False))
        self.assertTrue(self.output.state['handbrake']); self.assertTrue(self.output.state['gas'])
        wait_for(lambda: not self.output.state.get('nitro', False), timeout=1)
        self.assertEqual(self.output.last_steer, 0)

    def test_curve_and_disarm_are_owned_and_serialized(self):
        self.arm(); self.feed(); wait_for(lambda: self.output.state.get('gas', False))
        self.worker.command('curve', 1.)
        wait_for(lambda: self.output.last_steer == .5)
        self.worker.command('disarm')
        wait_for(lambda: not self.worker.snapshot()['armed'] and not self.worker.snapshot()['busy'])
        self.assertEqual(self.output.last_steer, 0); self.assertFalse(self.output.state['nitro'])
        self.assertTrue(self.stream.closed)

    def test_serial_error_resets_and_worker_can_stop(self):
        self.arm(); self.feed(); wait_for(lambda: self.output.state.get('gas', False))
        self.stream.error = True
        wait_for(lambda: not self.worker.snapshot()['armed'])
        self.assertEqual(self.output.last_steer, 0); self.assertFalse(self.output.state['handbrake'])

    def test_close_retries_reset_and_detaches(self):
        self.arm(); self.feed(); wait_for(lambda: self.output.state.get('gas', False))
        self.output.fail_resets = 2
        self.assertTrue(self.worker.stop())
        self.assertEqual(self.output.last_steer, 0); self.assertFalse(self.output.state['nitro'])

    def test_latest_snapshot_is_a_copy(self):
        snapshot = self.worker.snapshot(); snapshot['armed'] = True
        self.assertFalse(self.worker.snapshot()['armed'])


class SlowArmTests(unittest.TestCase):
    def test_close_priority_coalesced_queue_and_no_orphan(self):
        gate = threading.Event(); output = Output(gate); opened = []
        worker = ControlWorker('TEST', lambda: output, lambda port: opened.append(port))
        try:
            worker.command('arm', 'TEST-COM'); self.assertTrue(output.ready.wait(1))
            # The UI can read/update commands while driver initialization blocks.
            for i in range(1000):
                worker.command('curve', .75); worker.command('disarm')
            with worker.lock:
                self.assertLessEqual(len(worker.commands), 2)
            self.assertTrue(worker.snapshot()['busy'])
            worker.command('close'); gate.set()
            self.assertTrue(worker.stop())
            self.assertFalse(opened); self.assertTrue(output.closed)
            self.assertFalse(worker.thread.is_alive())
            self.assertFalse(worker.command('arm', 'AFTER-CLOSE'))
        finally:
            gate.set(); self.assertTrue(worker.stop())


if __name__ == '__main__':
    unittest.main()
