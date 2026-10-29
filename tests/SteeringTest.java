import com.orchestr.phonewheel.Steering;

public final class SteeringTest {
 private static int checks;
 private static void near(double actual,double expected){checks++;if(Math.abs(actual-expected)>1e-9)throw new AssertionError(actual+" != "+expected);}
 private static void yes(boolean value){checks++;if(!value)throw new AssertionError("condition false");}
 public static void main(String[] args){
  near(Steering.target(0,0),0);
  near(Steering.target(10,0),0);near(Steering.target(-10,0),0);
  near(Steering.target(50,0),.5);near(Steering.target(-50,0),-.5);
  near(Steering.target(90,0),1);near(Steering.target(-90,0),-1);
  near(Steering.target(180,0),1);near(Steering.target(-180,0),-1);
  near(Steering.delta(-179,179),2);near(Steering.delta(179,-179),-2);
  near(Steering.delta(725,5),0);near(Steering.target(20,20),0);
  near(Steering.target(-179,179),0);near(Steering.target(179,-179),0);
  yes(Steering.target(24,0)<.18);yes(Steering.target(25,0)>.18);
   double previous=0;
   for(int degrees=0;degrees<=180;degrees++){
    double value=Steering.target(degrees,0);
    yes(value>=previous&&value>=0&&value<=1);
    near(Steering.target(-degrees,0),-value);previous=value;
   }
  System.out.println("Steering production math: "+checks+" checks passed");
 }
}
