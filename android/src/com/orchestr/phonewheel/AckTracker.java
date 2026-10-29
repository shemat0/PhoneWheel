package com.orchestr.phonewheel;

/** Bounded sequence/timestamp matching. Caller validates the Bluetooth session. */
public final class AckTracker {
 private final long[] sequences=new long[512], times=new long[512];
 private long latest=-1;
 public AckTracker(){java.util.Arrays.fill(sequences,-1);}
 public void sent(long sequence,long now){int slot=(int)(sequence&511);sequences[slot]=sequence;times[slot]=now;}
 public long acknowledge(long sequence,long now){
  int slot=(int)(sequence&511);
  if(sequence<0||sequence<=latest||sequences[slot]!=sequence||now-times[slot]>1000||now<times[slot])return -1;
  latest=sequence;sequences[slot]=-1;
  return Math.max(0,now-times[slot]);
 }
}
