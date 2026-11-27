package com.lanremote.bluetooth;
import java.io.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
public final class AtvWireTest {
 static void check(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  byte[] payload=AtvWire.concat(AtvWire.num(1,200),AtvWire.msg(10,new byte[200]));
  ByteArrayOutputStream out=new ByteArrayOutputStream();AtvWire.write(out,payload);
  AtvWire.Fields fields=AtvWire.parse(AtvWire.read(new ByteArrayInputStream(out.toByteArray())));
  check(fields.number(1)==200&&fields.message(10).length==200);
  check(AtvWire.key("ENTER")==23&&AtvWire.key("click")==0);
  check(AtvWire.unsigned(new java.math.BigInteger("128"))[0]==(byte)128);
  KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(1024);RSAPublicKey client=(RSAPublicKey)generator.generateKeyPair().getPublic(),server=(RSAPublicKey)generator.generateKeyPair().getPublic();byte[] hash=MessageDigest.getInstance("SHA-256").digest(AtvWire.concat(AtvWire.unsigned(client.getModulus()),AtvWire.unsigned(client.getPublicExponent()),AtvWire.unsigned(server.getModulus()),AtvWire.unsigned(server.getPublicExponent()),new byte[]{0x12,0x34}));String code=String.format("%02X1234",hash[0]&255);check(java.util.Arrays.equals(hash,AtvWire.secret(client,server,code)));
  try{AtvWire.secret(client,server,String.format("%02X1234",(hash[0]+1)&255));throw new AssertionError();}catch(IllegalArgumentException expected){}
  try{AtvWire.read(new ByteArrayInputStream(new byte[]{(byte)0xff,(byte)0xff,0x7f}));throw new AssertionError();}catch(IOException expected){}
  System.out.println("Android TV framing, key mapping and PIN proof tests passed");
 }
}
