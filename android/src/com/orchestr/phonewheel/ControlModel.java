package com.orchestr.phonewheel;

import java.util.HashMap;

/** Android-independent layout and multi-pointer input. All methods are synchronized. */
public final class ControlModel {
 public static final int GYRO=0, ARROWS=1, STICK=2;
 public static final int EMPTY=0,LEFT=1,RIGHT=2,GAS=3,BRAKE=4,DOWN=5,UP=6,NITRO=7,HAND=8;
 private int mode=GYRO;
 public static final int X=9,Y=10,BACK=11,START=12,L3=13,R3=14,DPUP=15,DPDOWN=16,DPLEFT=17,DPRIGHT=18;
 private final int[][] slots={{DOWN,GAS,BRAKE,UP,NITRO,HAND,X,Y},{LEFT,RIGHT,GAS,BRAKE,DOWN,UP,NITRO,HAND},{GAS,BRAKE,DOWN,UP,NITRO,HAND,X,Y}};
 private final Joystick joystick=new Joystick();
 private final HashMap<Integer,Integer> pointers=new HashMap<>();
 private boolean blocked;
 private double gyro;
 private long upUntil,downUntil;
 public synchronized int mode(){return mode;}
 public synchronized int slot(int mode,int slot){return slots[mode][slot];}
 public synchronized void setSlot(int mode,int slot,int action){
  if(mode<0||mode>2||slot<0||slot>7||action<0||action>18||(mode!=ARROWS&&(action==LEFT||action==RIGHT)))throw new IllegalArgumentException();
  clear();slots[mode][slot]=action;
 }
 public synchronized void setMode(int value){if(value!=GYRO&&value!=ARROWS&&value!=STICK)throw new IllegalArgumentException();clear();mode=value;}
 public synchronized void block(boolean value){clear();blocked=value;}
 public synchronized void clear(){pointers.clear();gyro=0;joystick.cancel();upUntil=downUntil=0;}
 public synchronized int columns(){return mode==STICK?2:4;}
 public synchronized int cells(){return 8;}
 public synchronized int action(int cell){
  if(cell<0||cell>=cells())return EMPTY;
  return slots[mode][cell];
 }
 public synchronized float[] bounds(int cell,float width,float height){
  int cols=columns();float gap=Math.min(width,height)*.014f;
  float left=mode==STICK?width*.5f:0,cw=(width-left)/cols,rh=height*.75f/(mode==STICK?4:2);
  return new float[]{left+cell%cols*cw+gap,height*.25f+cell/cols*rh+gap,left+(cell%cols+1)*cw-gap,height*.25f+(cell/cols+1)*rh-gap};
 }
 public synchronized int cellAt(float x,float y,float width,float height){
  if(x<0||x>=width||y<height*.25f||y>=height)return -1;
  for(int i=0;i<cells();i++){float[] b=bounds(i,width,height);if(x>=b[0]&&x<b[2]&&y>=b[1]&&y<b[3])return i;}
  return -1;
 }
 public synchronized boolean press(int pointer,int cell){
  if(blocked||action(cell)==EMPTY)return false;
  pointers.put(pointer,cell);return true;
 }
 public synchronized void release(int pointer,long now){
  Integer cell=pointers.remove(pointer);if(cell==null)return;
  int a=action(cell);if(a==UP)upUntil=now+100;if(a==DOWN)downUntil=now+100;
 }
 public synchronized void move(int pointer,int cell,long now){Integer old=pointers.get(pointer);if(old!=null&&old!=cell)release(pointer,now);}
 public synchronized boolean held(int action){for(int cell:pointers.values())if(action(cell)==action)return true;return false;}
 public synchronized void gyro(double value){if(!blocked&&mode==GYRO)gyro=Math.max(-1,Math.min(1,value));}
 public synchronized boolean stickDown(int id,float x,float y,float width,float surfaceHeight){return !blocked&&mode==STICK&&joystick.down(id,x,y,width*.5f,surfaceHeight);}
 public synchronized boolean stickOwns(int id){return mode==STICK&&joystick.owns(id);}
 public synchronized void stickMove(int id,float x,float y){if(!blocked&&mode==STICK)joystick.move(id,x,y);}
 public synchronized void stickUp(int id){joystick.up(id);}
 public synchronized float[] stickGeometry(float width,float surfaceHeight){return joystick.geometry(width*.5f,surfaceHeight);}
 public synchronized State state(long now){
  if(blocked)return new State(0,false,false,false,false,false,false,0);
  double axis=mode==GYRO?gyro:mode==STICK?joystick.value():(held(RIGHT)?1:0)-(held(LEFT)?1:0);
  int mask=0;for(int a=5;a<=18;a++)if(held(a)||(a==UP&&now<upUntil)||(a==DOWN&&now<downUntil))mask|=buttonMask(a);
  return new State(axis,held(GAS),held(BRAKE),held(UP)||now<upUntil,held(DOWN)||now<downUntil,held(NITRO),held(HAND),mask);
 }
 public static int buttonMask(int action){switch(action){case DOWN:return 0x100;case UP:return 0x200;case NITRO:return 0x2000;case HAND:return 0x1000;case X:return 0x4000;case Y:return 0x8000;case BACK:return 0x20;case START:return 0x10;case L3:return 0x40;case R3:return 0x80;case DPUP:return 1;case DPDOWN:return 2;case DPLEFT:return 4;case DPRIGHT:return 8;default:return 0;}}
 public static String label(int action){switch(action){case LEFT:return "РУЛЬ ←";case RIGHT:return "РУЛЬ →";case GAS:return "RT";case BRAKE:return "LT";case UP:return "RB";case DOWN:return "LB";case NITRO:return "B";case HAND:return "A";case X:return "X";case Y:return "Y";case BACK:return "BACK";case START:return "START";case L3:return "L3";case R3:return "R3";case DPUP:return "D-pad ↑";case DPDOWN:return "D-pad ↓";case DPLEFT:return "D-pad ←";case DPRIGHT:return "D-pad →";default:return "Пусто";}}
 /** Migration from the four optional1.6slots; trigger positions remain movable afterwards. */
 public static int[] migrate(int mode,int[] old){if(old.length!=4)throw new IllegalArgumentException();return mode==ARROWS?new int[]{LEFT,RIGHT,GAS,BRAKE,old[0],old[1],old[2],old[3]}:new int[]{old[0],GAS,BRAKE,old[1],old[2],old[3],EMPTY,EMPTY};}
 public static final class State {
  public final int buttons;public final double steer;public final boolean gas,brake,up,down,nitro,handbrake;
  State(double s,boolean g,boolean b,boolean u,boolean d,boolean n,boolean h,int mask){buttons=mask;steer=s;gas=g;brake=b;up=u;down=d;nitro=n;handbrake=h;}
 }
}
