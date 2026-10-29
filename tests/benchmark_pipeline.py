"""Fake serial/driver throughput and deterministic scheduling model, NOT Bluetooth latency."""
import json
import time
from test_core import FakeSerial, make_output, Controller, SerialPump

output, pad = make_output()
now = [0.]
controller = Controller('TEST', output, lambda: now[0])
controller.arm(True)
stream = FakeSerial()
pump = SerialPump(stream, controller)
packet = dict(token='TEST', session='benchmark-stream', seq=0, steer=.5,
              gas=False, brake=False, up=False, down=False)
start = time.perf_counter()
delays = []
for seq in range(10000):
    packet['seq'] = seq
    raw = (json.dumps(packet) + '\n').encode()
    # Sender model 10ms; each frame arrives in two pieces 1ms apart.
    sent_ms = seq * 10
    now[0] = sent_ms / 1000
    stream.incoming.extend(raw[:20])
    assert pump.poll() is None
    stream.incoming.extend(raw[20:])
    received_ms = sent_ms + 1
    polled_ms = ((received_ms + 3) // 4) * 4
    now[0] = polled_ms / 1000
    assert pump.poll()['ok']
    delays.append(polled_ms - received_ms)
    stream.outgoing.clear()
elapsed = time.perf_counter() - start
assert pad.report['x'] == 16384
controller.arm(False)
pump.close()
print(json.dumps(dict(frames=10000, cpu_elapsed_seconds=round(elapsed, 4),
                     simulated_sender_ms=10, simulated_poll_ms=4,
                     simulated_after_last_byte_ms=[min(delays), max(delays)],
                     physical_bluetooth_latency_measured=False)))
