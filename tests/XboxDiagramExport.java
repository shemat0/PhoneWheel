import com.orchestr.phonewheel.XboxDiagram;
import com.orchestr.phonewheel.ControlModel;

public final class XboxDiagramExport {
 public static void main(String[] args){System.out.print("[");for(int i=0;i<XboxDiagram.ACTIONS.length;i++){if(i>0)System.out.print(",");float[] b=XboxDiagram.BOXES[i];int a=XboxDiagram.ACTIONS[i];System.out.print("{\"action\":"+a+",\"label\":\""+ControlModel.label(a)+"\",\"rect\":["+b[0]+","+b[1]+","+b[2]+","+b[3]+"]}");}System.out.println("]");}
}
