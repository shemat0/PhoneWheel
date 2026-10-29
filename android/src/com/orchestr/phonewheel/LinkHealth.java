package com.orchestr.phonewheel;

/** Session-bound freshness/RTT; only a matched successful ACK can turn green. */
public final class LinkHealth {
 private final String session;
 private final AckTracker tracker=new AckTracker();
 private long last=-1,rtt=-1;
 private boolean active=true;
 public LinkHealth(String session){this.session=session;}
 public synchronized void sent(long seq,long now){if(active)tracker.sent(seq,now);}
 public synchronized boolean ack(String session,long seq,long now){
  if(!active||!this.session.equals(session))return false;
  long elapsed=tracker.acknowledge(seq,now);if(elapsed<0)return false;
  last=now;rtt=elapsed;return true;
 }
 public synchronized boolean fresh(long now){return active&&last>=0&&now-last<=400;}
 public synchronized long last(){return last;}
 public synchronized long rtt(){return rtt;}
 public synchronized void close(){active=false;last=-1;}
}
