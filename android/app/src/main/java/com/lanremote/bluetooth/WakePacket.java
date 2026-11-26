package com.lanremote.bluetooth;

import java.net.*;
import java.util.Arrays;

final class WakePacket {
 static byte[] create(String address){
  String value=WakeAddress.normalize(address);
  if(value.isEmpty())throw new IllegalArgumentException("Invalid wake address.");
  byte[] packet=new byte[102];Arrays.fill(packet,0,6,(byte)255);
  String[] parts=value.split(":");for(int j=0;j<16;j++)for(int i=0;i<6;i++)packet[6+j*6+i]=(byte)Integer.parseInt(parts[i],16);
  return packet;
 }
 static InetAddress broadcast(InetAddress local,int prefix)throws UnknownHostException{
  byte[] bytes=local.getAddress();if(bytes.length!=4||prefix<1||prefix>30)throw new IllegalArgumentException("Wi-Fi has no usable IPv4 broadcast subnet.");
  for(int i=prefix;i<32;i++)bytes[i/8]=(byte)(bytes[i/8]|(1<<(7-i%8)));
  return InetAddress.getByAddress(bytes);
 }
}
