package com.lanremote.bluetooth;

final class IdlePolicy {
 static final long TV_IDLE=60000;
 static long discoveryDelay(int failures){return Math.min(120000,15000L << Math.max(0,Math.min(3,failures-1)));}
 static final long UPDATE_INTERVAL=6L*60*60*1000;
 // Successful input also counts as a health check.
 static long relayDelay(boolean connected,int failures){
  if(connected)return 30000;
  return Math.min(60000,5000L << Math.max(0,Math.min(4,failures-1)));
 }
}
