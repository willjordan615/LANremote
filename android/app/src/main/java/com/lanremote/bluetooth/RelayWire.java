package com.lanremote.bluetooth;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class RelayWire {
 static final int PORT=37941, MAX=1024;
 static final SecureRandom RANDOM=new SecureRandom();
 static byte[] random(int size){byte[] b=new byte[size];RANDOM.nextBytes(b);return b;}
 static final class Invite {
  final String ip;final byte[] key;
  Invite(String ip,byte[] key){this.ip=ip;this.key=key.clone();}
  String encode(){return "LANREMOTE1:"+ip+":"+Base64.getUrlEncoder().withoutPadding().encodeToString(key);}
  static Invite parse(String input){String[] fields=input.trim().split(":",-1);if(fields.length!=3||!fields[0].equals("LANREMOTE1")||!privateIP(fields[1]))throw new IllegalArgumentException("Invalid LANremote invitation.");byte[] key=Base64.getUrlDecoder().decode(fields[2]);if(key.length!=32)throw new IllegalArgumentException("Invalid invitation key.");return new Invite(fields[1],key);}
 }
 static String identity(byte[] key){try{byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(key);StringBuilder id=new StringBuilder();for(int i=0;i<16;i++)id.append(String.format(Locale.ROOT,"%02x",hash[i]&255));return id.toString();}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 static boolean privateIP(String address){String[] p=address.split("\\.",-1);if(p.length!=4)return false;int[] n=new int[4];try{for(int i=0;i<4;i++){n[i]=Integer.parseInt(p[i]);if(n[i]<0||n[i]>255||!p[i].equals(Integer.toString(n[i])))return false;}}catch(NumberFormatException e){return false;}return n[0]==10||(n[0]==192&&n[1]==168)||(n[0]==172&&n[1]>=16&&n[1]<=31);}
 static byte[] crypt(boolean encrypt,byte[] key,byte[] challenge,int direction,byte[] nonce,byte[] data)throws Exception{
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(encrypt?Cipher.ENCRYPT_MODE:Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD("LANREMOTE-RELAY-1".getBytes(StandardCharsets.US_ASCII));cipher.updateAAD(challenge);cipher.updateAAD(new byte[]{(byte)direction});return cipher.doFinal(data);
 }
 static void write(DataOutputStream out,byte[] key,byte[] challenge,int direction,byte[] data)throws Exception{
  if(data.length>MAX)throw new IOException("Message too large");byte[] nonce=random(12),sealed=crypt(true,key,challenge,direction,nonce,data);out.writeShort(sealed.length);out.write(nonce);out.write(sealed);out.flush();
 }
 static byte[] read(DataInputStream in,byte[] key,byte[] challenge,int direction)throws Exception{
  int length=in.readUnsignedShort();if(length<16||length>MAX+16)throw new IOException("Invalid frame length");byte[] nonce=new byte[12],sealed=new byte[length];in.readFully(nonce);in.readFully(sealed);return crypt(false,key,challenge,direction,nonce,sealed);
 }
 static byte[] key(int report,int usage){if(!allowed(report,usage))throw new IllegalArgumentException("Unsupported remote key.");return new byte[]{1,(byte)report,(byte)(usage>>8),(byte)usage};}
 static boolean allowed(int report,int usage){
  if(report==1)return Arrays.asList(0x29,0x52,0x50,0x28,0x4f,0x51,0x2a,0x2b,0x2c).contains(usage);
  if(report==2)return Arrays.asList(0x223,0x40,0xea,0xe2,0xe9,0xb6,0xcd,0xb5).contains(usage);
  return report==3&&(usage==1||usage==2);
 }
 static byte[] text(String text){HidKeys.text(text);byte[] raw=text.getBytes(StandardCharsets.UTF_8);byte[] b=new byte[raw.length+1];b[0]=2;System.arraycopy(raw,0,b,1,raw.length);return b;}
}
