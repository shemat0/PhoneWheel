import com.orchestr.phonewheel.AckTracker;

public final class AckTrackerTest {
 private static void check(long actual,long expected){if(actual!=expected)throw new AssertionError(actual+" != "+expected);}
 public static void main(String[] args){
  AckTracker tracker=new AckTracker();check(tracker.acknowledge(-1,100),-1);check(tracker.acknowledge(0,100),-1);
  tracker.sent(0,100);check(tracker.acknowledge(0,119),19);check(tracker.acknowledge(0,120),-1);
  tracker.sent(1,121);tracker.sent(2,125);check(tracker.acknowledge(2,130),5);check(tracker.acknowledge(1,131),-1);
  tracker.sent(3,140);tracker.sent(515,150);check(tracker.acknowledge(3,160),-1);check(tracker.acknowledge(515,164),14);
  tracker.sent(516,1000);check(tracker.acknowledge(516,1250),250);tracker.sent(517,1300);check(tracker.acknowledge(517,2301),-1);
  System.out.println("AckTracker production RTT matching: 10 checks passed");
 }
}
