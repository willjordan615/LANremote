package com.lanremote.bluetooth;
import java.io.*;
import java.util.Arrays;
public class RelayWireTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  byte[] key=RelayWire.random(32),challenge=RelayWire.random(16);
  RelayWire.Invite invite=new RelayWire.Invite("192.168.1.20",key);
  check(Arrays.equals(key,RelayWire.Invite.parse(invite.encode()).key));
  check(RelayWire.identity(key).matches("[0-9a-f]{32}"));
  check(RelayWire.identity(key).equals(RelayWire.identity(invite.key)));
  check(!RelayWire.identity(key).equals(RelayWire.identity(RelayWire.random(32))));
  check(!RelayWire.privateIP("8.8.8.8"));check(!RelayWire.privateIP("192.168.001.1"));
  byte[] cmd=RelayWire.key(3,1);ByteArrayOutputStream buffer=new ByteArrayOutputStream();
  RelayWire.write(new DataOutputStream(buffer),key,challenge,1,cmd);
  byte[] frame=buffer.toByteArray();
  check(Arrays.equals(cmd,RelayWire.read(new DataInputStream(new ByteArrayInputStream(frame)),key,challenge,1)));
  for(int i=0;i<4;i++){try{byte[] changed=frame.clone();if(i==3)changed[changed.length-1]^=1;RelayWire.read(new DataInputStream(new ByteArrayInputStream(changed)),i==0?RelayWire.random(32):key,i==1?RelayWire.random(16):challenge,i==2?2:1);throw new AssertionError("Accepted unauthenticated frame");}catch(javax.crypto.AEADBadTagException expected){}}
  check(!RelayWire.allowed(1,0x65));check(!RelayWire.allowed(2,0xffff));check(RelayWire.text("Hello").length==6);
  System.out.println("Relay invitation, allowed commands, authentication, replay and tamper tests passed");
 }
}
