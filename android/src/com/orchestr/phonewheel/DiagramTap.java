package com.orchestr.phonewheel;

/** A diagram assignment requires one short tap on the same control. */
public final class DiagramTap {
 private int pointer=-1, action;
 private float startX,startY,width,height,slop;
 public void cancel(){pointer=-1;action=0;}
 public void begin(int id,float x,float y,float w,float h,float threshold){
  cancel();if(w<=0||h<=0)return;
  pointer=id;startX=x;startY=y;width=w;height=h;slop=threshold;
  action=XboxDiagram.at(x/w,y/h);
 }
 public void move(int id,float x,float y){
  if(id!=pointer)return;
  float dx=x-startX,dy=y-startY;
  if(dx*dx+dy*dy>slop*slop)cancel();
 }
 public int end(int id,float x,float y){
  move(id,x,y);
  int result=id==pointer&&action!=0&&XboxDiagram.at(x/width,y/height)==action?action:0;
  cancel();return result;
 }
}
