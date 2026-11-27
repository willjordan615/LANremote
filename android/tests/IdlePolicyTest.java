package com.lanremote.bluetooth;
public class IdlePolicyTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  check(IdlePolicy.relayDelay(true,0)==30000);
  check(IdlePolicy.relayDelay(false,1)==5000);
  check(IdlePolicy.relayDelay(false,2)==10000);
  check(IdlePolicy.relayDelay(false,3)==20000);
  check(IdlePolicy.relayDelay(false,10)==60000);
  check(IdlePolicy.UPDATE_INTERVAL==21600000);
  check(IdlePolicy.TV_IDLE==60000);
  check(IdlePolicy.discoveryDelay(1)==15000);
  check(IdlePolicy.discoveryDelay(2)==30000);
  check(IdlePolicy.discoveryDelay(3)==60000);
  check(IdlePolicy.discoveryDelay(99)==120000);
  System.out.println("Idle polling and retry policy tests passed");
 }
}
