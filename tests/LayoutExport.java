import com.orchestr.phonewheel.ControlModel;

/** Diagnostic preview data from the production model, not an Android screenshot. */
public final class LayoutExport {
 public static void main(String[] args){ControlModel m=new ControlModel();System.out.print("[");for(int mode=0;mode<2;mode++){m.setMode(mode);if(mode>0)System.out.print(",");System.out.print("{\"mode\":"+mode+",\"width\":1280,\"height\":720,\"cells\":[");for(int i=0;i<m.cells();i++){if(i>0)System.out.print(",");float[] b=m.bounds(i,1280,720);System.out.print("{\"action\":"+m.action(i)+",\"label\":\""+ControlModel.label(m.action(i))+"\",\"rect\":["+b[0]+","+b[1]+","+b[2]+","+b[3]+"]}");}System.out.print("]}");}System.out.println("]");}
}
