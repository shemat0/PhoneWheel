package com.orchestr.phonewheel;

/** One captured pointer, horizontal drag relative to DOWN, fixed area sensitivity. */
public final class Joystick {
 private int pointer=-1;
 private float downX,originX,originY,radius;
 private double axis;
 public synchronized boolean down(int id,float x,float y,float width,float height){
  if(pointer!=-1||id<0||!(width>0)||!(height>0)||Float.isInfinite(width)||Float.isInfinite(height)||Float.isNaN(x)||Float.isNaN(y)||x<0||x>=width||y<0||y>=height)return false;
  radius=Math.min(width,height)*.38f;
  pointer=id;downX=x;originX=Math.max(radius,Math.min(width-radius,x));originY=Math.max(radius,Math.min(height-radius,y));axis=0;
  return true;
 }
 public synchronized boolean owns(int id){return pointer>=0&&pointer==id;}
 public synchronized void move(int id,float x,float y){if(pointer==id)axis=normalize((x-downX)/radius);}
 public synchronized void up(int id){if(pointer==id)cancel();}
 public synchronized void cancel(){pointer=-1;axis=0;}
 public synchronized double value(){return axis;}
 public synchronized float[] geometry(float width,float height){
  float r=pointer<0?Math.min(width,height)*.38f:radius;
  float x=pointer<0?width/2:originX,y=pointer<0?height/2:originY;
  return new float[]{x,y,r,x+(float)axis*r};
 }
 public static double normalize(double value){double magnitude=Math.min(1,Math.abs(value));return magnitude<=.05?0:Math.copySign((magnitude-.05)/.95,value);}
}
