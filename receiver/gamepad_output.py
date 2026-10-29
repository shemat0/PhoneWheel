"""Lazy Xbox360 output: importing this module never connects to a driver."""
import importlib
import math


DRIVER_HELP = ('Не удалось создать Xbox 360 геймпад. Установите официальный '
               'ViGEmBus 1.22.0, перезагрузите ПК, затем включите управление снова. '
               'Инструкция: README.md. Клавиатурного режима нет.')


def axis_value(steer):
    return round(steer * (32767 if steer >= 0 else 32768))


def response_curve(steer, exponent=.75):
    if not math.isfinite(steer) or not -1 <= steer <= 1:
        raise ValueError('Ось должна находиться между -1 и 1')
    if not math.isfinite(exponent) or not .5 <= exponent <= 1.5:
        raise ValueError('Отклик должен находиться между 0,5 и 1,5')
    return math.copysign(abs(steer) ** exponent, steer)


class GamepadOutput:
    def __init__(self, loader=None):
        self.loader = loader or (lambda: importlib.import_module('vgamepad'))
        self.pad = None
        self.module = None
        self.response_exponent = .75
        self.last_steer = 0.

    def ensure_ready(self):
        if self.pad is not None:
            return
        try:
            module = self.loader()
            pad = module.VX360Gamepad()
            self.module, self.pad = module, pad
            # Force a changed report before neutral: some fresh ViGEm targets
            # otherwise expose a stale initial XInput axis until the first move.
            self.pad.left_joystick(x_value=1, y_value=0)
            self.pad.update()
            self.neutral()
        except Exception as exc:
            try:
                self.neutral()
            except OSError:
                pass
            raise OSError(DRIVER_HELP) from exc

    def apply(self, steer, gas, brake, up, down, nitro=False, handbrake=False, buttons=None):
        if self.pad is None:
            raise OSError(DRIVER_HELP)
        try:
            self.pad.reset()
            adjusted = response_curve(steer, self.response_exponent)
            self.pad.left_joystick(x_value=axis_value(adjusted), y_value=0)
            self.pad.right_trigger(value=255 if gas else 0)
            self.pad.left_trigger(value=255 if brake else 0)
            if buttons is not None:
                if buttons:
                    self.pad.press_button(button=buttons)
            else:
                if up:
                    self.pad.press_button(button=self.module.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER)
                if down:
                    self.pad.press_button(button=self.module.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER)
                if nitro:
                    self.pad.press_button(button=self.module.XUSB_BUTTON.XUSB_GAMEPAD_B)
                if handbrake:
                    self.pad.press_button(button=self.module.XUSB_BUTTON.XUSB_GAMEPAD_A)
            self.pad.update()
            self.last_steer = adjusted
        except Exception as exc:
            raise OSError('Геймпад не принял команду. Проверьте ViGEmBus и перезапустите приёмник.') from exc

    def neutral(self):
        if self.pad is None:
            return
        try:
            self.pad.reset()
            self.pad.update()
            self.last_steer = 0.
        except Exception as exc:
            raise OSError('Не удалось обнулить геймпад; повторяю сброс. Проверьте ViGEmBus.') from exc

    def close(self):
        try:
            self.neutral()
        finally:
            # vgamepad target destructor removes only this client's virtual pad.
            self.pad = None
            self.module = None
            self.last_steer = 0.
