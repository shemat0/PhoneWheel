import com.orchestr.phonewheel.ControlModel;

/** Diagnostic data from the production model, not an Android screenshot. */
public final class StickLayoutExport {
 public static void main(String[] args){ControlModel m=new ControlModel();m.setMode(ControlModel.STICK);float[] g=m.stickGeometry(1280,540);System.out.print("{\"width\":1280,\"height\":720,\"stick\":["+g[0]+","+g[1]+","+g[2]+","+g[3]+"],\"cells\":[");for(int i=0;i<8;i++){if(i>0)System.out.print(",");float[] b=m.bounds(i,1280,720);System.out.print("{\"label\":\""+ControlModel.label(m.action(i))+"\",\"rect\":["+b[0]+","+b[1]+","+b[2]+","+b[3]+"]}");}System.out.println("]}");}
}
