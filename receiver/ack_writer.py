"""Bounded, rate-limited ACK output. Never runs in the control/read thread."""
import threading
import time


class LatestAckWriter:
    def __init__(self, stream):
        self.stream = stream
        self.condition = threading.Condition()
        self.pending = None
        self.generation = 0
        self.reset_requested = False
        self.stopping = False
        self.fault = None
        self.error_since = None
        self.replacements = 0
        self.thread = threading.Thread(target=self._run, name='PhoneWheel-ACK', daemon=False)
        self.thread.start()

    def submit(self, data):
        with self.condition:
            if self.stopping:
                return
            if self.pending is not None:
                self.replacements += 1
            self.pending = (self.generation, data)
            self.condition.notify_all()

    def _cancel(self):
        cancel = getattr(self.stream, 'cancel_write', None)
        if cancel:
            try:
                cancel()
            except OSError:
                pass

    def clear(self):
        with self.condition:
            self.generation += 1
            self.pending = None
            self.reset_requested = True
            self.condition.notify_all()
        self._cancel()

    def problem(self):
        with self.condition:
            return self.fault

    def close(self):
        with self.condition:
            self.stopping = True
            self.pending = None
            self.condition.notify_all()
        self._cancel()
        self.thread.join(.8)
        if self.thread.is_alive():
            # Closing the handle is the documented escape for a stuck serial write.
            self.stream.close()
            self.thread.join(1)
        if self.thread.is_alive():
            raise OSError('Windows не завершила Bluetooth-запись. Закройте приёмник после восстановления адаптера.')

    def _run(self):
        next_write = 0.
        while True:
            with self.condition:
                while not self.stopping and self.pending is None and not self.reset_requested:
                    self.condition.wait()
                if self.stopping:
                    return
                delay = next_write - time.monotonic()
                if delay > 0:
                    self.condition.wait(delay)
                    continue
                reset = self.reset_requested
                self.reset_requested = False
                item, self.pending = self.pending, None
            try:
                if reset:
                    self.stream.reset_output_buffer()
                if item is None:
                    continue
                generation, data = item
                # Leading newline terminates any partial previous ACK after timeout.
                encoded = b'\n' + data
                if self.stream.write(encoded) != len(encoded):
                    raise OSError('Неполная запись подтверждения Bluetooth')
                with self.condition:
                    if generation == self.generation:
                        self.error_since = None
                        self.fault = None
            except OSError:
                with self.condition:
                    if self.stopping:
                        return
                    if item is not None and item[0] != self.generation:
                        continue
                    now = time.monotonic()
                    if self.error_since is None:
                        self.error_since = now
                    if now - self.error_since >= .9:
                        self.fault = 'Bluetooth не отправляет подтверждения более0,9с. Управление остановлено; переподключитесь.'
                    self.reset_requested = True
                    if self.pending is None and item is not None:
                        self.pending = item
            finally:
                next_write = time.monotonic() + .02  # At most50 ACKs/s, latest only.
