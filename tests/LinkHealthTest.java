import com.orchestr.phonewheel.LinkHealth;

public final class LinkHealthTest {
 private static int checks;
 private static void yes(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
 public static void main(String[] args){
  LinkHealth link=new LinkHealth("current");yes(!link.fresh(0));link.sent(0,10);yes(!link.ack("old-session",0,20));yes(!link.fresh(20));yes(link.ack("current",0,30));yes(link.rtt()==20);
  yes(link.fresh(430));yes(!link.fresh(431));yes(!link.ack("current",0,432));yes(!link.fresh(432));
  link.sent(1,500);yes(link.ack("current",1,510));link.close();yes(!link.fresh(511));link.sent(2,520);yes(!link.ack("current",2,530));
  LinkHealth newer=new LinkHealth("newer");newer.sent(0,600);yes(newer.ack("newer",0,610));link.close();yes(newer.fresh(611));
  System.out.println("LinkHealth production freshness/session: "+checks+" checks passed");
 }
}
