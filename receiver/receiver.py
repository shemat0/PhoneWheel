import secrets
from serial.tools import list_ports
import tkinter as tk
from tkinter import ttk, messagebox
from gamepad_output import GamepadOutput
from control_worker import ControlWorker


def main():
    root = tk.Tk()
    root.title('Phone Wheel 1.7 • Аналоговый геймпад')
    root.geometry('680x720')
    root.configure(bg='#101826')
    token = secrets.token_hex(4).upper()
    worker = ControlWorker(token)
    closing = False
    frame = ttk.Frame(root, padding=24); frame.pack(fill='both', expand=True)
    ttk.Label(frame, text='PHONE WHEEL', font=('Segoe UI', 24, 'bold')).pack(anchor='w')
    ttk.Label(frame, text='1. Сопрягите телефон и ПК в настройках Bluetooth.\n2. Создайте входящий Bluetooth COM-порт Windows.\n3. Выберите его здесь, откройте и подключите телефон.', wraplength=540).pack(anchor='w', pady=12)
    ports_row = ttk.Frame(frame); ports_row.pack(fill='x')
    port = ttk.Combobox(ports_row, state='readonly', width=12)
    port.pack(side='left')
    def refresh():
        values = [p.device for p in list_ports.comports()]
        port['values'] = values
        if values and port.get() not in values: port.set(values[0])
    refresh()
    ttk.Button(ports_row, text='Обновить порты', command=refresh).pack(side='left', padx=6)
    ttk.Label(frame, text=f'Код подключения: {token}', font=('Consolas', 18, 'bold')).pack(anchor='w', pady=12)
    ttk.Label(frame, text='Виртуальный Xbox 360: поворот — левый стик X\nГаз — RT · тормоз — LT · Все Xbox-кнопки назначаются в8слотах телефона', wraplength=580).pack(anchor='w', pady=10)
    preview = tk.StringVar(value='Вход: +0% → геймпад: +0%')
    ttk.Label(frame, textvariable=preview, font=('Consolas', 16)).pack(anchor='w', pady=8)
    curve_label = tk.StringVar(value='Отклик руля: 0.75 (1 — линейно)')
    ttk.Label(frame, textvariable=curve_label).pack(anchor='w')
    def set_curve(value):
        worker.command('curve', float(value))
        curve_label.set(f'Отклик руля: {float(value):.2f} (1 — линейно)')
    curve = tk.Scale(frame, from_=.5, to=1.5, resolution=.05, orient='horizontal', command=set_curve, showvalue=False)
    curve.set(.75); curve.pack(fill='x')
    ttk.Label(frame, text='Меньше — сильнее в начале, спокойнее ближе к краю.').pack(anchor='w')
    def driver_guide():
        messagebox.showinfo('Драйвер геймпада', 'Установите ViGEmBus_1.22.0_x64_x86_arm64.exe от Nefarius Software Solutions e.U., затем перезагрузите ПК. После включения управления откройте joy.cpl и проверьте Xbox 360 Controller. Программа не устанавливает драйвер автоматически. Подробнее в README.md.')
    ttk.Button(frame, text='Как установить драйвер', command=driver_guide).pack(anchor='w')
    def show_licenses():
        from pathlib import Path
        import sys
        folder = Path(getattr(sys, '_MEIPASS', Path(__file__).resolve().parents[1] / 'dist')) / 'licenses'
        notice = '\n\n'.join(p.name + '\n' + p.read_text(encoding='utf-8') for p in sorted(folder.glob('*.txt')))
        window = tk.Toplevel(root); window.title('Лицензии сторонних компонентов')
        text = tk.Text(window, wrap='word', width=90, height=30)
        text.pack(fill='both', expand=True); text.insert('1.0', notice or 'Лицензии: vgamepad MIT, ViGEmClient BSD-3-Clause.'); text.configure(state='disabled')
    ttk.Button(frame, text='Лицензии компонентов', command=show_licenses).pack(anchor='w', pady=4)
    status = tk.StringVar(value='Управление выключено')
    button_text = tk.StringVar(value='Включить управление')
    def toggle():
        snapshot = worker.snapshot()
        if snapshot['busy'] or closing:
            return
        if snapshot['armed']:
            worker.command('disarm')
        elif not port.get():
            messagebox.showerror('Bluetooth', 'Выберите входящий Bluetooth COM-порт.')
        else:
            worker.command('arm', port.get())
    toggle_button = ttk.Button(frame, textvariable=button_text, command=toggle)
    toggle_button.pack(fill='x', pady=12)
    ttk.Label(frame, textvariable=status, wraplength=580).pack(anchor='w')
    ttk.Label(frame, text='При потере команд геймпад обнуляется через 0,4 секунды.\nНужен Bluetooth Classic SPP; BLE не подходит.\nНастройте в игре управление геймпадом, ось руля — левый стик X.\nНастройка входящего COM-порта описана в README.', wraplength=580).pack(anchor='w', pady=16)
    def poll():
        snapshot = worker.snapshot()
        preview.set(f"Вход: {snapshot['steer']:+.0%} → геймпад: {snapshot['output']:+.0%}\nГаз: {int(snapshot['gas'])*100}%  Тормоз: {int(snapshot['brake'])*100}%  Ручник: {'ВКЛ' if snapshot['handbrake'] else 'выкл'}  Нитро: {'ВКЛ' if snapshot['nitro'] else 'выкл'}")
        status.set(snapshot['status'])
        button_text.set('Подождите…' if snapshot['busy'] else ('Выключить управление' if snapshot['armed'] else 'Включить управление'))
        toggle_button.configure(state='disabled' if snapshot['busy'] or closing or snapshot['stopped'] else 'normal')
        port.configure(state='disabled' if snapshot['busy'] or snapshot['armed'] or closing else 'readonly')
        if closing and snapshot['stopped']:
            root.destroy()
            return
        root.after(100, poll)
    def close():
        nonlocal closing
        closing = True
        toggle_button.configure(state='disabled')
        status.set('Останавливаю управление…')
        worker.command('close')
    root.protocol('WM_DELETE_WINDOW', close)
    root.after(100, poll)
    try:
        root.mainloop()
    finally:
        worker.stop()

if __name__ == '__main__':
    import sys
    if len(sys.argv) == 3 and sys.argv[1] == '--check-driver-result':
        # Explicit diagnostic: no installation, GUI, Bluetooth or game commands.
        import json
        from pathlib import Path
        output = GamepadOutput()
        try:
            output.ensure_ready()
            output.neutral()
            result = {'available': True}
        except OSError as exc:
            result = {'available': False, 'message': str(exc), 'cause': str(exc.__cause__)}
        Path(sys.argv[2]).write_text(json.dumps(result, ensure_ascii=False), encoding='utf-8')
    else:
        main()
