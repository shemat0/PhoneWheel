import com.orchestr.phonewheel.AckLineReader;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public final class AckLineReaderTest {
 private static int checks;
 private static void yes(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
 public static void main(String[] args)throws Exception{
  List<String> lines=Collections.synchronizedList(new ArrayList<String>());AtomicInteger failures=new AtomicInteger();
  PipedInputStream input=new PipedInputStream();PipedOutputStream output=new PipedOutputStream(input);AtomicBoolean active=new AtomicBoolean(true);
  Thread reader=new Thread(new AckLineReader(input,()->active.get(),line->lines.add(line),error->failures.incrementAndGet()));reader.start();
  output.write("part".getBytes("UTF-8"));output.flush();Thread.sleep(10);yes(lines.isEmpty());output.write("ial\nnext\n".getBytes("UTF-8"));output.close();reader.join(1000);yes(!reader.isAlive());yes(lines.size()==2&&lines.get(0).equals("partial")&&lines.get(1).equals("next"));yes(failures.get()==1);
  failures.set(0);PipedInputStream oldInput=new PipedInputStream();PipedOutputStream oldOutput=new PipedOutputStream(oldInput);AtomicReference<String> session=new AtomicReference<>("old");
  Thread old=new Thread(new AckLineReader(oldInput,()->session.get().equals("old"),line->lines.add(line),error->failures.incrementAndGet()));old.start();Thread.sleep(10);session.set("new");oldOutput.close();old.join(1000);yes(!old.isAlive());yes(failures.get()==0);yes(session.get().equals("new"));
  byte[] large=new byte[2049];Arrays.fill(large,(byte)'x');new AckLineReader(new ByteArrayInputStream(large),()->true,line->lines.add(line),error->failures.incrementAndGet()).run();yes(failures.get()==1);
  lines.clear();failures.set(0);new AckLineReader(new ByteArrayInputStream("partial\nvalid\n".getBytes("UTF-8")),()->true,line->{if(line.equals("partial"))throw new AckLineReader.InvalidLine();lines.add(line);},error->failures.incrementAndGet()).run();yes(lines.size()==1&&lines.get(0).equals("valid"));yes(failures.get()==1);
  System.out.println("AckLineReader production blocking/lifecycle: "+checks+" checks passed");
 }
}
