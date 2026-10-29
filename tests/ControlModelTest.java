import com.orchestr.phonewheel.ControlModel;
import com.orchestr.phonewheel.XboxDiagram;
import java.util.Arrays;

public final class ControlModelTest {
 private static int checks;
 private static void yes(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
 private static void neutral(ControlModel m){ControlModel.State s=m.state(0);yes(s.steer==0&&!s.gas&&!s.brake&&s.buttons==0);}
 public static void main(String[] args){
  ControlModel m=new ControlModel();yes(m.cells()==8);yes(m.action(1)==ControlModel.GAS&&m.action(2)==ControlModel.BRAKE);
  for(int mode=0;mode<2;mode++)for(int slot=0;slot<8;slot++)for(int action=0;action<=18;action++){
   if(mode==0&&(action==1||action==2))continue;
   m.setSlot(mode,slot,action);yes(m.slot(mode,slot)==action);
  }
  m.setMode(0);m.setSlot(0,0,ControlModel.GAS);m.setSlot(0,1,ControlModel.GAS);m.press(1,0);m.press(2,1);yes(m.state(0).gas);m.release(1,0);yes(m.state(0).gas);m.release(2,0);neutral(m);
  m.setSlot(0,0,ControlModel.EMPTY);yes(!m.press(1,0));m.setSlot(0,0,ControlModel.HAND);m.press(1,0);m.block(true);neutral(m);m.gyro(1);neutral(m);m.block(false);neutral(m);
  m.setMode(1);m.setSlot(1,0,ControlModel.LEFT);m.setSlot(1,1,ControlModel.RIGHT);m.press(100,0);yes(m.state(0).steer==-1);m.gyro(.7);yes(m.state(0).steer==-1);m.press(3,1);yes(m.state(0).steer==0);m.release(100,0);yes(m.state(0).steer==1);m.release(3,0);neutral(m);
  m.setSlot(1,4,ControlModel.DPLEFT);m.press(3,4);yes(m.state(0).buttons==4&&m.state(0).steer==0);m.clear();
  int[] masks={0x100,0x200,0x2000,0x1000,0x4000,0x8000,0x20,0x10,0x40,0x80,1,2,4,8};
  for(int a=5;a<=18;a++){m.setSlot(1,0,a);m.press(1,0);yes(m.state(0).buttons==masks[a-5]);m.clear();neutral(m);}
  m.setSlot(1,5,ControlModel.UP);m.press(1,5);m.release(1,10);yes((m.state(109).buttons&0x200)!=0&&m.state(110).buttons==0);m.clear();neutral(m);
  m.press(1,5);m.release(1,20);m.block(true);m.block(false);neutral(m);m.press(3,5);yes(m.state(1000).up);m.setMode(0);neutral(m);
  yes(Arrays.equals(ControlModel.migrate(0,new int[]{5,6,7,8}),new int[]{5,3,4,6,7,8,0,0}));
  yes(Arrays.equals(ControlModel.migrate(1,new int[]{0,7,8,5}),new int[]{1,2,3,4,0,7,8,5}));
  yes(ControlModel.label(3).equals("RT")&&ControlModel.label(4).equals("LT"));
  for(int mode=0;mode<2;mode++){m.setMode(mode);for(int[] size:new int[][]{{640,360},{960,540},{1920,1080},{2560,1200}}){int w=size[0],h=size[1];yes(m.cellAt(1,h*.24f,w,h)==-1);for(int cell=0;cell<m.cells();cell++){float[] b=m.bounds(cell,w,h);yes(b[0]>=0&&b[1]>=h*.25f&&b[2]<=w&&b[3]<=h&&b[2]>b[0]&&b[3]>b[1]);yes(m.cellAt((b[0]+b[2])/2,(b[1]+b[3])/2,w,h)==cell);}}}
  for(int i=0;i<XboxDiagram.ACTIONS.length;i++){float[] b=XboxDiagram.BOXES[i];yes(XboxDiagram.at((b[0]+b[2])/2,(b[1]+b[3])/2)==XboxDiagram.ACTIONS[i]);for(int j=0;j<i;j++){float[] other=XboxDiagram.BOXES[j];yes(b[2]<=other[0]||b[0]>=other[2]||b[3]<=other[1]||b[1]>=other[3]);}}
  boolean rejected=false;try{m.setSlot(0,0,ControlModel.LEFT);}catch(IllegalArgumentException e){rejected=true;}yes(rejected);
  System.out.println("ControlModel production8slots/migration/diagram: "+checks+" checks passed");
 }
}
