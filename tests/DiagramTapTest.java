import com.orchestr.phonewheel.DiagramTap;
public class DiagramTapTest {
 private static int checks;
 private static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
 public static void main(String[] args){
  DiagramTap tap=new DiagramTap();
  tap.begin(1,770,590,1000,1000,12);check(tap.end(1,772,592)==8);
  tap.begin(1,500,900,1000,1000,12);check(tap.end(1,770,590)==0);
  tap.begin(1,770,590,1000,1000,12);tap.move(1,800,590);check(tap.end(1,770,590)==0);
  tap.begin(1,770,590,1000,1000,12);tap.cancel();check(tap.end(1,770,590)==0);
  tap.begin(1,770,590,1000,1000,12);check(tap.end(2,770,590)==0);
  check(tap.end(1,770,590)==0);
  tap.begin(1,770,590,0,0,12);check(tap.end(1,770,590)==0);
  tap.begin(1,770,590,1000,1000,12);check(tap.end(1,650,470)==0);
  System.out.println("Diagram tap: "+checks+" checks passed");
 }
}
