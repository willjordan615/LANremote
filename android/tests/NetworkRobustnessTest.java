package com.lanremote.bluetooth;
import java.io.*;
import java.util.*;
public final class NetworkRobustnessTest{
 interface Attempt{void run()throws Exception;}
 static void reject(Attempt a)throws Exception{try{a.run();throw new AssertionError("Accepted invalid data");}catch(Exception expected){}}
 public static void main(String[] args)throws Exception{
  byte[] key=RelayWire.random(32),challenge=RelayWire.random(16);ByteArrayOutputStream bytes=new ByteArrayOutputStream();RelayWire.write(new DataOutputStream(bytes),key,challenge,1,RelayWire.key(1,0x28));byte[] frame=bytes.toByteArray();
  reject(()->RelayWire.read(new DataInputStream(new ByteArrayInputStream(frame)),RelayWire.random(32),challenge,1));reject(()->RelayWire.read(new DataInputStream(new ByteArrayInputStream(frame)),key,RelayWire.random(16),1));reject(()->RelayWire.read(new DataInputStream(new ByteArrayInputStream(frame)),key,challenge,2));
  for(int i=0;i<frame.length;i++){byte[] truncated=Arrays.copyOf(frame,i);reject(()->RelayWire.read(new DataInputStream(new ByteArrayInputStream(truncated)),key,challenge,1));}
  reject(()->RelayWire.readFrame(new DataInputStream(new ByteArrayInputStream(new byte[0])),key,challenge,1,65535));reject(()->RelayWire.Invite.parse("a".repeat(257)));reject(()->RelayWire.Invite.parse("LANREMOTE1:8.8.8.8:"+Base64.getUrlEncoder().withoutPadding().encodeToString(key)));
  reject(()->AtvWire.parse(new byte[]{0}));reject(()->AtvWire.parse(new byte[]{10,127}));reject(()->AtvWire.read(new ByteArrayInputStream(new byte[]{(byte)128,(byte)128,4})));
  reject(()->RelayPairWire.field(new DataInputStream(new ByteArrayInputStream(new byte[]{1,1})),1,256));
  System.out.println("Malformed, unauthenticated, replayed and oversized frame rejection passed");
 }
}
