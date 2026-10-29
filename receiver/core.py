"""Phone Wheel protocol and analog gamepad state machine (no OS dependencies)."""
import json
import math
import time

TIMEOUT = .4


class Controller:
    def __init__(self, token, output, clock=time.monotonic):
        self.token, self.output, self.clock = token, output, clock
        self.armed = False
        self.peer = None
        self.seq = -1
        self.last = 0
        self.state = dict(steer=0., gas=False, brake=False, up=False, down=False, nitro=False, handbrake=False, buttons=0)
        self.last_gears = (False, False)
        self.pulses = {}
        self.pending_neutral = False

    def release(self):
        self.state = dict(steer=0., gas=False, brake=False, up=False, down=False, nitro=False, handbrake=False, buttons=0)
        self.last_gears = (False, False)
        self.pulses.clear()
        self.pending_neutral = True
        self.output.neutral()
        self.pending_neutral = False

    def arm(self, enabled):
        self.armed = False
        self.release()
        if enabled:
            try:
                self.output.ensure_ready()
            except OSError:
                self.pending_neutral = True
                raise
        self.armed = enabled
        self.peer = None
        self.seq = -1

    def disconnect(self):
        self.peer = None
        self.seq = -1
        self.release()

    def tick(self):
        if self.pending_neutral:
            self.release()
        expired = [name for name, deadline in self.pulses.items() if self.clock() >= deadline]
        if expired:
            for name in expired:
                self.state[name] = False
                del self.pulses[name]
            self.pending_neutral = True
            self.output.apply(**self.state)
            self.pending_neutral = False
        if self.peer and self.clock() - self.last > TIMEOUT:
            self.release()
            self.peer = None
            self.seq = -1
            return True
        return False

    def receive(self, raw, address):
        self.tick()
        try:
            data = json.loads(raw)
            if not isinstance(data, dict):
                raise ValueError()
            if data.get('token') != self.token:
                return {'ok': False, 'error': 'Неверный код подключения'}
            session = data['session']
            seq = data['seq']
            steer = data['steer']
            if not isinstance(session, str) or not 8 <= len(session) <= 64:
                raise ValueError()
            if type(seq) is not int or not 0 <= seq < 2**53:
                raise ValueError()
            if type(steer) not in (int, float) or not -1 <= steer <= 1 or not math.isfinite(steer):
                raise ValueError()
            generic = 'buttons' in data
            mask = data.get('buttons')
            if generic and (type(mask) is not int or mask < 0 or mask & ~0xF3FF):
                raise ValueError()
            for name in ('gas', 'brake') + (() if generic else ('up', 'down')):
                if type(data[name]) is not bool:
                    raise ValueError()
            if any(type(data.get(name, False)) is not bool for name in ('nitro', 'handbrake')):
                raise ValueError()
            if not self.armed:
                return {'ok': False, 'error': 'Включите управление на ПК'}
            peer = (address, session)
            if self.peer and self.peer != peer:
                return {'ok': False, 'error': 'Другой телефон уже подключён'}
            if self.peer == peer and seq <= self.seq:
                return {'ok': False, 'error': 'Устаревший пакет'}
            self.peer, self.seq, self.last = peer, seq, self.clock()
            if generic:
                self.pulses.clear()
                self.last_gears = (False, False)
                self.state = dict(steer=float(steer), gas=data['gas'], brake=data['brake'],
                                  up=False, down=False, nitro=False, handbrake=False, buttons=mask)
            else:
                gears = (data['up'], data['down'])
                for i, name in enumerate(('up', 'down')):
                    if gears[i] and not self.last_gears[i]:
                        self.pulses[name] = self.clock() + .08
                self.last_gears = gears
                self.state = dict(steer=float(steer), gas=data['gas'], brake=data['brake'],
                                  up='up' in self.pulses, down='down' in self.pulses,
                                  nitro=data.get('nitro', False), handbrake=data.get('handbrake', False), buttons=None)
            self.pending_neutral = True
            self.output.apply(**self.state)
            self.pending_neutral = False
            return {'ok': True, 'seq': seq, 'session': session}
        except (ValueError, KeyError, TypeError, UnicodeDecodeError, RecursionError):
            return {'ok': False, 'error': 'Некорректный пакет'}
