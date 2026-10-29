"""Bounded newline JSON framing for a Bluetooth serial stream."""
import json
from ack_writer import LatestAckWriter

class Framer:
    def __init__(self):
        self.pending = bytearray()

    def feed(self, data):
        frames = []
        for byte in data:
            if byte == 10:
                if self.pending:
                    frames.append(bytes(self.pending))
                    self.pending.clear()
            else:
                self.pending.append(byte)
                if len(self.pending) > 2048:
                    self.pending.clear()
                    raise ValueError('Слишком длинный Bluetooth-пакет')
        return frames

class SerialPump:
    def __init__(self, stream, controller):
        self.stream, self.controller = stream, controller
        self.framer = Framer()
        self.writer = LatestAckWriter(stream)

    def flush(self):
        self.controller.disconnect()
        self.stream.reset_input_buffer()
        self.framer = Framer()
        self.writer.clear()

    def poll(self, block=False):
        problem = self.writer.problem()
        if problem:
            raise OSError(problem)
        count = self.stream.in_waiting
        if count > 8192:
            raise ValueError('Очередь Bluetooth переполнена — подключитесь снова')
        if not count:
            if not block:
                return None
            first = self.stream.read(1)  # COM timeout bounds command/stop latency.
            if not first:
                return None
            count = self.stream.in_waiting
            if count > 8192:
                raise ValueError('Очередь Bluetooth переполнена — подключитесь снова')
            raw = first + self.stream.read(count) if count else first
        else:
            raw = self.stream.read(count)
        frames = self.framer.feed(raw)
        if not frames:
            return None
        # Old queued controls are never replayed into a game.
        reply = self.controller.receive(frames[-1], 'bluetooth-com')
        encoded = (json.dumps(reply, ensure_ascii=True) + '\n').encode('ascii')
        self.writer.submit(encoded)
        return reply

    def close(self):
        try:
            self.controller.disconnect()
        finally:
            self.framer = Framer()
            try:
                self.writer.close()
            finally:
                self.stream.close()
