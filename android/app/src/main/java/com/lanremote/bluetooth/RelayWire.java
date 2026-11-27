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
  final String ip,hostId;final byte[] key;
  Invite(String ip,byte[] key){this(ip,key,identity(key));}
  Invite(String ip,byte[] key,String hostId){if(!privateIP(ip)||key.length!=32||!hostId.matches("[0-9a-f]{32}"))throw new IllegalArgumentException("Invalid invitation");this.ip=ip;this.key=key.clone();this.hostId=hostId;}
  Invite at(String address){return new Invite(address,key,hostId);}
  String encode(){return "LANREMOTE2:"+ip+":"+Base64.getUrlEncoder().withoutPadding().encodeToString(key)+":"+hostId;}
  static Invite parse(String input){if(input==null||input.length()>256)throw new IllegalArgumentException("Invalid LANremote invitation.");String[] fields=input.trim().split(":",-1);boolean old=fields.length==3&&fields[0].equals("LANREMOTE1"),modern=fields.length==4&&fields[0].equals("LANREMOTE2");if(!old&&!modern)throw new IllegalArgumentException("Invalid LANremote invitation.");if(!fields[2].matches("[A-Za-z0-9_-]{43}=?"))throw new IllegalArgumentException("Invalid invitation key");byte[] key=Base64.getUrlDecoder().decode(fields[2]);return new Invite(fields[1],key,old?identity(key):fields[3]);}
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
  return readFrame(in,key,challenge,direction,in.readUnsignedShort());
 }
 static byte[] readFrame(DataInputStream in,byte[] key,byte[] challenge,int direction,int length)throws Exception{
  if(length<16||length>MAX+16)throw new IOException("Invalid frame length");byte[] nonce=new byte[12],sealed=new byte[length];in.readFully(nonce);in.readFully(sealed);return crypt(false,key,challenge,direction,nonce,sealed);
 }
 static byte[] key(int report,int usage){if(!allowed(report,usage))throw new IllegalArgumentException("Unsupported remote key.");return new byte[]{1,(byte)report,(byte)(usage>>8),(byte)usage};}
 static boolean allowed(int report,int usage){
  if(report==1)return Arrays.asList(0x29,0x52,0x50,0x28,0x4f,0x51,0x2a,0x2b,0x2c).contains(usage);
  if(report==2)return Arrays.asList(0x30,0x223,0x40,0xea,0xe2,0xe9,0xb6,0xcd,0xb5).contains(usage);
  return report==3&&(usage==1||usage==2);
 }
 static boolean validMovement(int x,int y){return x>=-127&&x<=127&&y>=-127&&y<=127&&(x!=0||y!=0);}
 static byte[] movement(int x,int y){if(!validMovement(x,y))throw new IllegalArgumentException("Invalid mouse delta");return new byte[]{3,(byte)x,(byte)y};}
 static byte[] edit(TextEdit edit){HidKeys.text(edit.insert);byte[] raw=edit.insert.getBytes(StandardCharsets.UTF_8);byte[] out=new byte[3+raw.length];out[0]=4;out[1]=(byte)(edit.deletes>>8);out[2]=(byte)edit.deletes;System.arraycopy(raw,0,out,3,raw.length);return out;}
 static TextEdit parseEdit(byte[] raw){if(raw.length<3||raw.length>503||raw[0]!=4)throw new IllegalArgumentException("Invalid text edit");int deletes=((raw[1]&255)<<8)|(raw[2]&255);String insert=new String(raw,3,raw.length-3,StandardCharsets.UTF_8);TextEdit edit=new TextEdit(deletes,insert);HidKeys.text(insert);return edit;}
 static byte[] text(String text){HidKeys.text(text);byte[] raw=text.getBytes(StandardCharsets.UTF_8);byte[] b=new byte[raw.length+1];b[0]=2;System.arraycopy(raw,0,b,1,raw.length);return b;}
}
