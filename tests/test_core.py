import json
import time
import sys
import unittest
from pathlib import Path
from types import SimpleNamespace
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'receiver'))
from core import Controller
from transport import Framer,SerialPump
from gamepad_output import GamepadOutput,axis_value,DRIVER_HELP,response_curve

class FakePad:
 def __init__(self):self.reports=[];self.reset();self.fail=False
 def reset(self):self.report=dict(x=0,y=0,rt=0,lt=0,buttons=set())
 def left_joystick(self,x_value,y_value):self.report.update(x=x_value,y=y_value)
 def right_trigger(self,value):self.report['rt']=value
 def left_trigger(self,value):self.report['lt']=value
 def press_button(self,button):self.report['buttons'].add(button)
 def update(self):
  if self.fail:raise RuntimeError('injected driver failure')
  self.reports.append(dict(self.report,buttons=set(self.report['buttons'])))

def make_output():
 pad=FakePad();module=SimpleNamespace(VX360Gamepad=lambda:pad,XUSB_BUTTON=SimpleNamespace(XUSB_GAMEPAD_RIGHT_SHOULDER='RB',XUSB_GAMEPAD_LEFT_SHOULDER='LB',XUSB_GAMEPAD_B='B',XUSB_GAMEPAD_A='A'))
 output=GamepadOutput(lambda:module);output.response_exponent=1.0
 return output,pad

class Tests(unittest.TestCase):
 def setUp(self):
  self.now=1.;self.output,self.pad=make_output();self.c=Controller('ABC',self.output,lambda:self.now);self.c.arm(True)
  self.p=dict(token='ABC',session='session-123',seq=1,steer=0,gas=False,brake=False,up=False,down=False)
 def send(self,**kwargs):self.p.update(kwargs);return self.c.receive(json.dumps(self.p).encode(),'bluetooth')
 def test_proportional_axis_not_buttons(self):
  for seq,value in enumerate((0,.125,.5,1,-1),1):
   self.assertTrue(self.send(seq=seq,steer=value)['ok'])
   self.assertEqual(self.pad.reports[-1]['x'],{0:0,.125:4096,.5:16384,1:32767,-1:-32768}[value])
   self.assertFalse(self.pad.reports[-1]['buttons'])
 def test_pedals_both_and_release(self):
  self.send(gas=True,brake=True);self.assertEqual((self.pad.report['rt'],self.pad.report['lt']),(255,255))
  self.send(seq=2,gas=False,brake=False);self.assertEqual((self.pad.report['rt'],self.pad.report['lt']),(0,0))
 def test_timeout_neutral(self):
  self.send(steer=1,gas=True,up=True);self.now+=.401;self.assertTrue(self.c.tick());self.assertEqual(self.pad.report['x'],0);self.assertEqual(self.pad.report['rt'],0);self.assertFalse(self.pad.report['buttons'])
 def test_disarm(self):
  self.send(steer=-1,gas=True);self.c.arm(False);self.assertEqual(self.pad.report['x'],0);self.assertFalse(self.send(seq=2)['ok'])
 def test_auth_sequence_and_session(self):
  self.assertFalse(self.send(token='bad')['ok']);self.assertTrue(self.send(token='ABC')['ok']);self.assertFalse(self.send()['ok'])
  self.assertFalse(self.send(session='new-session',seq=0)['ok']);self.now+=.5;self.assertTrue(self.send()['ok'])
 def test_malformed(self):
  for raw in (b'broken',b'[]',b'{}',b'\xff',b'['*1000+b']'*1000):self.assertFalse(self.c.receive(raw,'bluetooth')['ok'])
  for value in (2,10**400,float('nan'),'0',True):self.assertFalse(self.send(steer=value)['ok'])
  self.assertFalse(self.send(steer=0,gas=1)['ok'])
 def test_gear_pulses_rising_only(self):
  self.send(up=True,down=True);self.assertEqual(self.pad.report['buttons'],{'RB','LB'})
  self.now+=.081;self.c.tick();self.assertFalse(self.pad.report['buttons'])
  self.send(seq=2);self.assertFalse(self.pad.report['buttons'])
  self.send(seq=3,up=False,down=False);self.send(seq=4,up=True);self.assertEqual(self.pad.report['buttons'],{'RB'})
 def test_failed_neutral_is_retried(self):
  self.send(steer=1,gas=True);self.pad.fail=True
  with self.assertRaises(OSError):self.c.arm(False)
  self.assertFalse(self.c.armed);self.assertTrue(self.c.pending_neutral)
  self.pad.fail=False;self.c.tick();self.assertFalse(self.c.pending_neutral);self.assertEqual(self.pad.report['x'],0);self.assertEqual(self.pad.report['rt'],0)
 def test_failed_apply_requests_neutral(self):
  self.pad.fail=True
  with self.assertRaises(OSError):self.send(steer=.5)
  self.assertTrue(self.c.pending_neutral);self.c.armed=False;self.pad.fail=False;self.c.tick();self.assertEqual(self.pad.report['x'],0)
 def test_driver_lazy_and_clear_error(self):
  calls=[]
  def missing():calls.append(1);raise RuntimeError('VIGEM_ERROR_BUS_NOT_FOUND')
  output=GamepadOutput(missing);c=Controller('ABC',output);self.assertFalse(calls)
  with self.assertRaisesRegex(OSError,'ViGEmBus 1.22.0'):c.arm(True)
  self.assertFalse(c.armed);self.assertEqual(len(calls),1)
 def test_generic_buttons_supersede_roles_and_hold(self):
  self.send(buttons=0xF30F,up=True,down=True,nitro=True,handbrake=True,gas=True,brake=True)
  self.assertEqual(self.pad.report['buttons'],{0xF30F});self.assertEqual((self.pad.report['rt'],self.pad.report['lt']),(255,255))
  self.now+=.1;self.c.tick();self.assertEqual(self.pad.report['buttons'],{0xF30F})
  self.send(seq=2,buttons=0,up=True,down=True,nitro=True,handbrake=True);self.assertFalse(self.pad.report['buttons'])
  self.c.arm(False);self.assertFalse(self.pad.report['buttons'])
 def test_generic_mask_validation(self):
  for value in (True,-1,0x10000,0x400,'1',None):self.assertFalse(self.send(buttons=value)['ok'])
 def test_nitro_handbrake_with_pedals_and_release(self):
  self.send(gas=True,brake=True,nitro=True,handbrake=True)
  self.assertEqual(self.pad.report['buttons'],{'A','B'});self.assertEqual((self.pad.report['rt'],self.pad.report['lt']),(255,255))
  self.send(seq=2,nitro=False,handbrake=False);self.assertFalse(self.pad.report['buttons'])
  self.send(seq=3,nitro=True,handbrake=True);self.now+=.401;self.c.tick();self.assertFalse(self.pad.report['buttons']);self.assertEqual(self.pad.report['rt'],0)
 def test_optional_buttons_old_packet_and_strict_type(self):
  self.assertTrue(self.send()['ok']);self.assertFalse(self.c.state['nitro']);self.assertFalse(self.c.state['handbrake'])
  for name in ('nitro','handbrake'):
   for value in (1,'true',None):
    packet=dict(self.p,seq=2);packet[name]=value;self.assertFalse(self.c.receive(json.dumps(packet),'bluetooth')['ok'])
 def test_startup_forces_changed_report_then_neutral(self):
  output,pad=make_output();output.ensure_ready()
  self.assertEqual([r['x'] for r in pad.reports],[1,0])
  self.assertTrue(all(r['rt']==0 and r['lt']==0 and not r['buttons'] for r in pad.reports))
 def test_startup_failure_retries_neutral(self):
  output,pad=make_output();pad.fail=True;c=Controller('ABC',output)
  with self.assertRaises(OSError):c.arm(True)
  self.assertFalse(c.armed);self.assertTrue(c.pending_neutral)
  pad.fail=False;c.tick();self.assertFalse(c.pending_neutral);self.assertEqual(pad.reports[-1]['x'],0)

class CurveTests(unittest.TestCase):
 def test_default_curve_and_preview_value(self):
  self.assertAlmostEqual(response_curve(.30),.4053600464421103)
  self.assertAlmostEqual(response_curve(.46),.5585581251971565)
  output,pad=make_output();output.response_exponent=.75;output.ensure_ready();output.apply(.3,False,False,False,False)
  self.assertAlmostEqual(output.last_steer,response_curve(.3));self.assertEqual(pad.report['x'],axis_value(response_curve(.3)))
  output.neutral();self.assertEqual(output.last_steer,0)
 def test_bounds_monotonic_symmetry_and_linear(self):
  for exponent in (.5,.75,1.,1.5):
   previous=0
   for i in range(1001):
    x=i/1000;value=response_curve(x,exponent)
    self.assertTrue(previous<=value<=1);self.assertEqual(response_curve(-x,exponent),-value);previous=value
   self.assertEqual(response_curve(0,exponent),0);self.assertEqual(response_curve(1,exponent),1)
  for x in (-1,-.5,0,.125,.5,1):self.assertEqual(response_curve(x,1.),x)
 def test_less_late_amplification(self):
  self.assertGreater(response_curve(.3,.75),.3)
  self.assertLess(response_curve(.9,.75)-response_curve(.8,.75),.1)
  self.assertGreater(response_curve(.3,.5),response_curve(.3,1.5))
 def test_invalid_curve_values(self):
  for x,gamma in ((2,.75),(float('nan'),1),(0,float('inf')),(0,.49),(0,1.51)):
   with self.assertRaises(ValueError):response_curve(x,gamma)

class FakeSerial:
 def __init__(self):self.incoming=bytearray();self.outgoing=bytearray();self.closed=False
 @property
 def in_waiting(self):return len(self.incoming)
 def read(self,n):data=bytes(self.incoming[:n]);del self.incoming[:n];return data
 def write(self,data):self.outgoing.extend(data);return len(data)
 def reset_input_buffer(self):self.incoming.clear()
 def reset_output_buffer(self):self.outgoing.clear()
 def close(self):self.closed=True

class StreamTests(unittest.TestCase):
 def setUp(self):
  self.output,self.pad=make_output();self.c=Controller('ABC',self.output);self.c.arm(True);self.stream=FakeSerial();self.pump=SerialPump(self.stream,self.c);self.addCleanup(self.pump.close)
  self.packet=dict(token='ABC',session='stream-123',seq=1,steer=.125,gas=True,brake=False,up=False,down=False)
 def raw(self):return (json.dumps(self.packet)+'\n').encode()
 def test_fragmented_ack(self):
  raw=self.raw();self.stream.incoming.extend(raw[:20]);self.assertIsNone(self.pump.poll());self.assertEqual(self.pad.report['x'],0)
  self.stream.incoming.extend(raw[20:]);self.assertTrue(self.pump.poll()['ok']);self.assertEqual(self.pad.report['x'],4096)
  deadline=time.monotonic()+1
  while not self.stream.outgoing and time.monotonic()<deadline:time.sleep(.001)
  ack=json.loads(self.stream.outgoing);self.assertEqual(ack['seq'],1);self.assertEqual(ack['session'],'stream-123')
 def test_latest_only(self):
  first=self.raw();self.packet.update(seq=2,steer=0,gas=False);self.stream.incoming.extend(first+self.raw());self.pump.poll();self.assertEqual(self.pad.report['x'],0);self.assertEqual(self.pad.report['rt'],0)
 def test_oversize(self):
  with self.assertRaises(ValueError):Framer().feed(b'x'*2049)
  self.stream.incoming.extend(b'x'*8193)
  with self.assertRaises(ValueError):self.pump.poll()
 def test_disconnect_neutral_and_reconnect(self):
  self.stream.incoming.extend(self.raw());self.pump.poll();self.pump.close();self.assertEqual(self.pad.report['x'],0);self.assertIsNone(self.c.peer);self.assertTrue(self.stream.closed)
  self.packet['session']='new-stream';self.packet['seq']=0;self.assertTrue(self.c.receive(json.dumps(self.packet),'new-com')['ok'])
 def test_flush_drops_partial_and_buffered_controls(self):
  self.stream.incoming.extend(self.raw()[:30]);self.pump.poll();self.stream.incoming.extend(self.raw());self.pump.flush();self.assertEqual(self.stream.in_waiting,0);self.assertFalse(self.pump.framer.pending);self.assertEqual(self.pad.report['rt'],0)
 def test_blank_and_multiple_frames(self):self.assertEqual(Framer().feed(b'\n{}\n[]\n'),[b'{}',b'[]'])

if __name__=='__main__':unittest.main()
