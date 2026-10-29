package com.orchestr.phonewheel;

/** Event-driven rate limit, based only on a monotonic millisecond clock. */
public final class SendTiming {
 private SendTiming() {}
 public static long delay(long now,long lastSent,boolean changed) {
  return delay(now,lastSent,changed,true);
 }
 public static long delay(long now,long lastSent,boolean changed,boolean touch){
  long period=changed?(touch?3:10):20;
  return Math.max(0,period-Math.max(0,now-lastSent));
 }
}
