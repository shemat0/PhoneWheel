"""Single-owner control/serial thread. No tkinter calls or objects live here."""
from collections import deque
import threading
import time

from core import Controller
from gamepad_output import GamepadOutput, response_curve
from transport import SerialPump


def open_serial(port):
    import serial
    return serial.Serial(port, 115200, timeout=.001, write_timeout=.2)


class ControlWorker:
    def __init__(self, token, output_factory=GamepadOutput, serial_factory=open_serial):
        self.token = token
        self.output_factory, self.serial_factory = output_factory, serial_factory
        self.lock = threading.Lock()
        self.wake = threading.Event()
        self.stop_event = threading.Event()
        self.commands = deque()
        self.snapshot_data = dict(armed=False, busy=False, stopped=False, closing=False,
                                  status='Управление выключено', steer=0., output=0.,
                                  gas=False, brake=False, nitro=False, handbrake=False, buttons=0)
        self.thread = threading.Thread(target=self._run, name='PhoneWheel-control', daemon=False)
        self.thread.start()

    def command(self, kind, value=None):
        if kind not in ('arm', 'disarm', 'curve', 'close'):
            raise ValueError('Unknown worker command')
        with self.lock:
            if kind == 'close':
                self.stop_event.set()  # Cannot be dropped even during slow arm/init.
                self.commands.clear()
                self.snapshot_data['closing'] = True
            elif not self.stop_event.is_set():
                group = ('arm', 'disarm') if kind in ('arm', 'disarm') else ('curve',)
                self.commands = deque(c for c in self.commands if c[0] not in group)
                self.commands.append((kind, value))  # At most one arm/disarm + one curve.
                if kind in ('arm', 'disarm'):
                    self.snapshot_data['busy'] = True
            else:
                return False
        self.wake.set()
        return True

    def snapshot(self):
        with self.lock:
            return dict(self.snapshot_data)

    def stop(self, timeout=3):
        self.command('close')
        self.thread.join(timeout)
        return not self.thread.is_alive()

    def _run(self):
        output = None
        controller = None
        pump = None
        status = 'Управление выключено'
        busy = False

        def publish(stopped=False):
            state = controller.state if controller else {}
            with self.lock:
                self.snapshot_data.update(armed=bool(controller and controller.armed), busy=busy,
                                          stopped=stopped, status=status,
                                          steer=state.get('steer', 0.),
                                          output=output.last_steer if output else 0.,
                                          gas=state.get('gas', False), brake=state.get('brake', False),
                                          nitro=state.get('nitro', False), handbrake=state.get('handbrake', False), buttons=state.get('buttons') or 0)

        def close_stream():
            nonlocal pump
            previous, pump = pump, None
            if previous:
                previous.close()

        def fail(exc):
            nonlocal status, busy
            status, busy = str(exc), False
            if controller:
                controller.armed = False
            try:
                close_stream()
            except OSError:
                pass
            if controller:
                try:
                    controller.release()
                except OSError:
                    pass  # pending_neutral retries on the same owner thread.

        try:
            output = self.output_factory()
            controller = Controller(self.token, output)
            while not self.stop_event.is_set():
                self.wake.clear()
                with self.lock:
                    commands = list(self.commands)
                    self.commands.clear()
                for kind, value in commands:
                    if self.stop_event.is_set():
                        break
                    try:
                        if kind == 'curve':
                            exponent = float(value)
                            response_curve(0., exponent)
                            output.response_exponent = exponent
                            if controller.armed and controller.peer:
                                controller.pending_neutral = True
                                output.apply(**controller.state)
                                controller.pending_neutral = False
                        else:
                            busy = True
                            publish()
                            controller.arm(False)
                            close_stream()
                            if kind == 'arm':
                                status = 'Подготовка геймпада и Bluetooth…'
                                publish()
                                controller.arm(True)
                                if not self.stop_event.is_set():
                                    stream = self.serial_factory(value)
                                    pump = SerialPump(stream, controller)
                                    pump.flush()
                                    status = 'Ожидаю телефон по Bluetooth'
                            else:
                                status = 'Управление выключено'
                            busy = False
                    except (OSError, ValueError) as exc:
                        fail(exc)
                if self.stop_event.is_set():
                    break
                if self.wake.is_set():
                    publish()
                    continue  # Process a newer disarm/curve before reading controls.
                try:
                    if controller.tick():
                        if pump:
                            pump.flush()
                        status = 'Связь потеряна — геймпад обнулён'
                    if pump:
                        reply = pump.poll(block=True)
                        if reply:
                            status = ('Телефон подключён по Bluetooth' if reply['ok'] else
                                      reply.get('error', 'Ошибка команды'))
                    else:
                        self.wake.wait(.02)
                except (OSError, ValueError) as exc:
                    fail(exc)
                publish()
        except Exception as exc:
            fail('Ошибка приёмника: ' + str(exc))
        finally:
            if controller:
                controller.armed = False
            try:
                close_stream()
            except OSError:
                pass
            if controller:
                for _ in range(5):
                    try:
                        controller.release()
                        break
                    except OSError:
                        time.sleep(.02)
            if output:
                try:
                    output.close()
                except Exception:
                    pass  # Target removal still runs in GamepadOutput.close finally.
            busy = False
            status = 'Приёмник остановлен'
            publish(stopped=True)
