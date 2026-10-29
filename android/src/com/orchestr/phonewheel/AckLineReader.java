package com.orchestr.phonewheel;

import java.io.*;

/** Blocking bounded framing, independent of Android and sender scheduling. */
public final class AckLineReader implements Runnable {
 public interface Line {void accept(String line) throws Exception;}
 public interface Failure {void accept(Exception error);}
 public interface Active {boolean getAsBoolean();}
 public static final class InvalidLine extends Exception {public InvalidLine(){super();}}
 private final InputStream input;private final Active active;private final Line line;private final Failure failure;
 public AckLineReader(InputStream input,Active active,Line line,Failure failure){this.input=input;this.active=active;this.line=line;this.failure=failure;}
 public void run(){
  ByteArrayOutputStream pending=new ByteArrayOutputStream();byte[] buffer=new byte[512];
  try{while(active.getAsBoolean()){
   int count=input.read(buffer);if(count<0)throw new EOFException("Связь закрыта ПК");
   for(int i=0;i<count&&active.getAsBoolean();i++){int value=buffer[i]&255;if(value==10){if(pending.size()>0){String valueLine=new String(pending.toByteArray(),"UTF-8");pending.reset();try{line.accept(valueLine);}catch(InvalidLine malformed){/* Partial ACK after serial timeout: wait for next delimited frame. */}}}else{pending.write(value);if(pending.size()>2048)throw new IOException("Слишком длинное подтверждение");}}
  }}catch(Exception error){if(active.getAsBoolean())failure.accept(error);}
 }
}
