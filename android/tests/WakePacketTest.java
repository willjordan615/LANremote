package com.lanremote.bluetooth;
import java.net.*;
public class WakePacketTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  byte[] expected={0x20,0x12,0x34,0x56,0x78,(byte)0x90};
  byte[] packet=WakePacket.create("201234567890");check(packet.length==102);
  for(int i=0;i<6;i++)check(packet[i]==(byte)255);
  for(int i=6;i<packet.length;i++)check(packet[i]==expected[(i-6)%6]);
  check(java.util.Arrays.equals(packet,WakePacket.create("20:12:34:56:78:90")));
  try{WakePacket.create("00:00:00:00:00:00");throw new AssertionError();}catch(IllegalArgumentException expectedError){}
  check(WakePacket.broadcast(InetAddress.getByName("192.168.68.12"),24).getHostAddress().equals("192.168.68.255"));
  check(WakePacket.broadcast(InetAddress.getByName("192.168.68.12"),23).getHostAddress().equals("192.168.69.255"));
  System.out.println("Wake packet and subnet broadcast tests passed");
 }
}
