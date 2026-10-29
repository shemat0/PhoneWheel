package com.orchestr.phonewheel;

/** Pure steering math shared by the phone and host regression checks. */
public final class Steering {
 private Steering() {}
 public static double delta(double angle, double center) {
  double value=(angle-center)%360;
  if(value>180)value-=360;
  if(value< -180)value+=360;
  return value;
 }
 public static double target(double angle, double center) {
  double offset=delta(angle,center);
  double magnitude=Math.abs(offset);
  if(magnitude<=10)return 0;
  return Math.copySign(Math.min(1,(magnitude-10)/80),offset);
 }
}
