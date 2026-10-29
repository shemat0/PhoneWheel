package com.orchestr.phonewheel;

/** Normalized native chooser hitboxes; buttons are Xbox controls, not game roles. */
public final class XboxDiagram {
 public static final int[] ACTIONS={4,3,5,6,13,14,11,12,10,8,9,7,15,16,17,18};
 public static final float[][] BOXES={
  {.14f,.02f,.32f,.12f},{.68f,.02f,.86f,.12f},
  {.10f,.15f,.34f,.25f},{.66f,.15f,.90f,.25f},
  {.22f,.30f,.37f,.47f},{.55f,.65f,.70f,.82f},
  {.41f,.39f,.50f,.49f},{.53f,.39f,.63f,.49f},
  {.73f,.29f,.81f,.41f},{.73f,.53f,.81f,.65f},
  {.64f,.41f,.73f,.53f},{.81f,.41f,.90f,.53f},
  {.25f,.52f,.33f,.62f},{.25f,.72f,.33f,.82f},
  {.17f,.62f,.25f,.72f},{.33f,.62f,.41f,.72f}
 };
 public static int at(float x,float y){for(int i=0;i<ACTIONS.length;i++){float[] b=BOXES[i];if(x>=b[0]&&x<b[2]&&y>=b[1]&&y<b[3])return ACTIONS[i];}return 0;}
 private XboxDiagram(){}
}
