import json
import threading
import time
import unittest
from test_worker import Stream, Output, wait_for
from control_worker import ControlWorker
from ack_writer import LatestAckWriter


class BackpressureStream(Stream):
    def __init__(self):
        super().__init__()
        self.entered = threading.Event()
        self.cancelled = threading.Event()
        self.block = True
        self.writes = 0
        self.reset_threads = []
        self.short_once = False

    def write(self, data):
        self.writes += 1
        self.entered.set()
        if self.block:
            self.cancelled.wait(.25)
            self.cancelled.clear()
            raise OSError('Write timeout (injected)')
        if self.short_once:
            self.short_once = False
            super().write(data[:9])
            return 9
        return super().write(data)

    def cancel_write(self):
        self.cancelled.set()

    def reset_output_buffer(self):
        self.reset_threads.append(threading.get_ident())
        super().reset_output_buffer()


class AckWriterTests(unittest.TestCase):
    def test_backpressure_latest_only_control_continues_and_neutral(self):
        output, stream = Output(), BackpressureStream()
        worker = ControlWorker('TEST', lambda: output, lambda port: stream)
        try:
            worker.command('arm', 'TEST-COM')
            wait_for(lambda: worker.snapshot()['armed'] and not worker.snapshot()['busy'])
            packet = dict(token='TEST', session='backpressure-17', seq=0, steer=.1,
                          gas=False, brake=False, buttons=0)
            stream.feed((json.dumps(packet)+'\n').encode())
            self.assertTrue(stream.entered.wait(1))
            # The ACK thread is blocked250ms; reads/reports must keep moving.
            for seq in range(1,51):
                packet.update(seq=seq, steer=seq/100)
                stream.feed((json.dumps(packet)+'\n').encode())
                time.sleep(.001)
            wait_for(lambda: output.state.get('steer') == .5)
            self.assertTrue(worker.snapshot()['armed'])
            # Input loss still neutralizes independently of ACK output.
            wait_for(lambda: output.last_steer == 0, timeout=1)
        finally:
            self.assertTrue(worker.stop())
        self.assertFalse(any(t.name=='PhoneWheel-ACK' and t.is_alive() for t in threading.enumerate()))

    def test_short_write_resync_recovery_and_bounded_queue(self):
        stream=BackpressureStream();stream.block=False;stream.short_once=True
        writer=LatestAckWriter(stream)
        try:
            writer.submit(b'{"ok":true,"seq":0}\n')
            wait_for(lambda: stream.writes>=1)
            for seq in range(1,501):writer.submit((json.dumps(dict(ok=True,seq=seq))+'\n').encode())
            wait_for(lambda: b'500' in stream.outgoing)
            self.assertIsNone(writer.problem());self.assertGreater(writer.replacements,0)
            # Recovery flush belongs to the writer, not the control thread.
            self.assertTrue(all(owner==writer.thread.ident for owner in stream.reset_threads))
            lines=[json.loads(line) for line in bytes(stream.outgoing).splitlines() if line]
            self.assertEqual(lines[-1]['seq'],500)
            self.assertTrue(bytes(stream.outgoing).startswith(b'\n'))
        finally:writer.close()
        self.assertFalse(writer.thread.is_alive())

    def test_prolonged_fault_stops_control_and_close_cancels_write(self):
        output,stream=Output(),BackpressureStream();worker=ControlWorker('TEST',lambda:output,lambda port:stream)
        try:
            worker.command('arm','TEST-COM');wait_for(lambda:worker.snapshot()['armed'])
            packet=dict(token='TEST',session='fault-stream-17',seq=0,steer=.8,gas=True,brake=False,buttons=0x1000)
            end=time.monotonic()+1.7
            while time.monotonic()<end:
                packet['seq']+=1;stream.feed((json.dumps(packet)+'\n').encode());time.sleep(.01)
                if not worker.snapshot()['armed']:break
            wait_for(lambda:not worker.snapshot()['armed'])
            self.assertEqual(output.last_steer,0);self.assertFalse(output.state['gas'])
            self.assertIn('подтверждения',worker.snapshot()['status'])
        finally:self.assertTrue(worker.stop())

    def test_cancel_unblocks_writer_without_full_timeout(self):
        stream=BackpressureStream();writer=LatestAckWriter(stream)
        writer.submit(b'{}\n');self.assertTrue(stream.entered.wait(1))
        writer.close();self.assertFalse(writer.thread.is_alive())


if __name__=='__main__':unittest.main()
