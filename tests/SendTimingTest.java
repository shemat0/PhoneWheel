import com.orchestr.phonewheel.SendTiming;

public final class SendTimingTest {
 private static void check(long actual,long expected){if(actual!=expected)throw new AssertionError(actual+" != "+expected);}
 public static void main(String[] args){
  check(SendTiming.delay(100,100,true),3);
  check(SendTiming.delay(102,100,true),1);
  check(SendTiming.delay(103,100,true),0);
  check(SendTiming.delay(106,100,true),0);
  check(SendTiming.delay(100,100,false),20);
  check(SendTiming.delay(119,100,false),1);
  check(SendTiming.delay(120,100,false),0);
  check(SendTiming.delay(150,100,false),0);
  check(SendTiming.delay(99,100,true),3);
  check(SendTiming.delay(100,100,true,false),10);check(SendTiming.delay(109,100,true,false),1);check(SendTiming.delay(110,100,true,false),0);
  System.out.println("SendTiming production rate gate: 12 checks passed");
 }
}
