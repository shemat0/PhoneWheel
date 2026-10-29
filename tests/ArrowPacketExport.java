import com.orchestr.phonewheel.ControlModel;

public final class ArrowPacketExport {
 private static boolean first=true;
 private static void emit(ControlModel m,String name){ControlModel.State s=m.state(0);if(!first)System.out.print(",");first=false;System.out.print("{\"name\":\""+name+"\",\"steer\":"+s.steer+",\"gas\":"+s.gas+",\"brake\":"+s.brake+",\"up\":"+s.up+",\"down\":"+s.down+",\"nitro\":"+s.nitro+",\"handbrake\":"+s.handbrake+"}");}
 public static void main(String[] args){ControlModel m=new ControlModel();m.setMode(ControlModel.ARROWS);System.out.print("[");m.press(1,0);emit(m,"left");m.press(2,1);emit(m,"both");m.release(1,0);emit(m,"right");m.release(2,0);emit(m,"released");m.press(4,0);m.block(true);emit(m,"menu-neutral");System.out.println("]");}
}
