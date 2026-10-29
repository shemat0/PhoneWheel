import com.orchestr.phonewheel.Joystick;
import com.orchestr.phonewheel.ControlModel;

public final class JoystickTest {
 private static int checks;
 private static void yes(boolean v){checks++;if(!v)throw new AssertionError("check "+checks);}
 private static void near(double a,double b){checks++;if(Math.abs(a-b)>1e-6)throw new AssertionError(a+" != "+b);}
 public static void main(String[] args){
  near(Joystick.normalize(0),0);near(Joystick.normalize(.05),0);near(Joystick.normalize(-.05),0);
  near(Joystick.normalize(.2875),.25);near(Joystick.normalize(.525),.5);near(Joystick.normalize(1),1);near(Joystick.normalize(-.2875),-.25);near(Joystick.normalize(-.525),-.5);near(Joystick.normalize(-1),-1);near(Joystick.normalize(2),1);near(Joystick.normalize(-2),-1);
  Joystick j=new Joystick();yes(j.down(5,200,150,400,300));near(j.value(),0);float r=j.geometry(400,300)[2];near(r,114);
  j.move(5,200,250);near(j.value(),0);j.move(9,314,150);near(j.value(),0);yes(!j.down(9,200,150,400,300));j.up(9);yes(j.owns(5));
  j.move(5,200+r*.525f,150);near(j.value(),.5);j.up(5);near(j.value(),0);yes(!j.owns(5));
  yes(j.down(9,1,1,400,300));near(j.geometry(400,300)[2],r);near(j.value(),0);j.move(9,1+r,1);near(j.value(),1);j.cancel();near(j.value(),0);yes(!j.down(-1,10,10,400,300));
  ControlModel m=new ControlModel();m.gyro(.9);m.setMode(ControlModel.STICK);near(m.state(0).steer,0);m.gyro(1);near(m.state(0).steer,0);
  yes(m.stickDown(5,200,150,800,300));r=m.stickGeometry(800,300)[2];m.stickMove(5,200+r*.525f,999);near(m.state(0).steer,.5);
  m.press(1,0);m.press(2,4);yes(m.state(0).gas&&(m.state(0).buttons&0x2000)!=0);m.release(1,0);yes(!m.state(0).gas);near(m.state(0).steer,.5);m.stickUp(5);near(m.state(0).steer,0);yes((m.state(0).buttons&0x2000)!=0);
  m.clear();yes(m.state(0).buttons==0);yes(m.stickDown(5,200,150,800,300));m.stickMove(5,400,0);m.block(true);near(m.state(0).steer,0);yes(!m.stickDown(6,200,150,800,300));m.block(false);near(m.state(0).steer,0);
  yes(m.stickDown(5,200,150,800,300));m.stickMove(5,400,0);m.setSlot(2,0,0);near(m.state(0).steer,0);yes(!m.stickOwns(5));yes(!m.stickDown(7,600,150,800,300));
  int old=m.slot(0,0);m.setSlot(2,0,ControlModel.X);yes(m.slot(0,0)==old);m.setMode(0);m.setMode(2);yes(m.slot(2,0)==ControlModel.X);
  for(int[] size:new int[][]{{640,360},{960,540},{1920,1080}}){int w=size[0],h=size[1];yes(m.cells()==8&&m.columns()==2);yes(m.cellAt(w*.25f,h*.5f,w,h)==-1);for(int i=0;i<8;i++){float[] b=m.bounds(i,w,h);yes(b[0]>=w*.5f&&b[2]<=w&&b[1]>=h*.25f&&b[3]<=h);yes(m.cellAt((b[0]+b[2])/2,(b[1]+b[3])/2,w,h)==i);}}
  System.out.println("Joystick production math/pointers/layout: "+checks+" checks passed");
 }
}
