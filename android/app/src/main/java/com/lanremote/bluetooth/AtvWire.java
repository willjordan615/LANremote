package com.lanremote.bluetooth;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

// Minimal bounded protobuf subset for Polo pairing and Android TV Remote v2.
final class AtvWire {
 static final int MAX=65536;
 static byte[] concat(byte[]... parts){ByteArrayOutputStream b=new ByteArrayOutputStream();for(byte[] p:parts)b.write(p,0,p.length);return b.toByteArray();}
 static byte[] var(long n){ByteArrayOutputStream b=new ByteArrayOutputStream();do{b.write((int)(n&127)|(n>127?128:0));n>>>=7;}while(n!=0);return b.toByteArray();}
 static byte[] num(int f,long n){return concat(var(f*8L),var(n));}
 static byte[] msg(int f,byte[] p){return concat(var(f*8L+2),var(p.length),p);}
 static byte[] str(int f,String s){return msg(f,s.getBytes(StandardCharsets.UTF_8));}
 static long readVar(InputStream in)throws IOException{long v=0;for(int i=0;i<10;i++){int c=in.read();if(c<0)throw new EOFException();if(i==9&&(c&254)!=0)throw new IOException("Invalid varint");v|=(long)(c&127)<<(7*i);if((c&128)==0)return v;}throw new IOException("Invalid varint");}
 static byte[] exact(InputStream in,int size)throws IOException{if(size<0||size>MAX)throw new IOException("Invalid frame size");byte[] b=new byte[size];new DataInputStream(in).readFully(b);return b;}
 static byte[] read(InputStream in)throws IOException{long size=readVar(in);if(size<1||size>MAX)throw new IOException("Invalid frame");return exact(in,(int)size);}
 static void write(OutputStream out,byte[] p)throws IOException{if(p.length<1||p.length>MAX)throw new IOException("Invalid frame");out.write(var(p.length));out.write(p);out.flush();}
 static final class Fields {
  final Map<Integer,Long> numbers=new HashMap<>();final Map<Integer,byte[]> messages=new HashMap<>();
  long number(int f){return numbers.getOrDefault(f,0L);}byte[] message(int f){return messages.get(f);}boolean has(int f){return messages.containsKey(f)||numbers.containsKey(f);}
 }
 static Fields parse(byte[] p)throws IOException{Fields f=new Fields();if(p==null)return f;ByteArrayInputStream in=new ByteArrayInputStream(p);while(in.available()>0){long tag=readVar(in);int field=(int)(tag>>>3);if(field<1||tag>Integer.MAX_VALUE)throw new IOException("Invalid field");switch((int)(tag&7)){case 0:f.numbers.put(field,readVar(in));break;case 1:exact(in,8);break;case 2:long n=readVar(in);if(n<0||n>in.available())throw new IOException("Invalid length");f.messages.put(field,exact(in,(int)n));break;case 5:exact(in,4);break;default:throw new IOException("Unknown wire type");}}return f;}
 static byte[] polo(int field,byte[] body){return concat(num(1,2),num(2,200),msg(field,body));}
 static void expect(InputStream in,int field)throws IOException{Fields f=parse(read(in));if(f.number(2)!=200||!f.has(field))throw new IOException("TV rejected pairing or returned an unexpected response");}
 static byte[] unsigned(BigInteger n){byte[] b=n.toByteArray();int start=0;while(start<b.length-1&&b[start]==0)start++;return Arrays.copyOfRange(b,start,b.length);}
 static byte[] secret(RSAPublicKey client,RSAPublicKey server,String code)throws Exception{if(!code.matches("[0-9A-Fa-f]{6}"))throw new IllegalArgumentException("Enter six hexadecimal characters");byte[] nonce={(byte)Integer.parseInt(code.substring(2,4),16),(byte)Integer.parseInt(code.substring(4,6),16)};byte[] digest=MessageDigest.getInstance("SHA-256").digest(concat(unsigned(client.getModulus()),unsigned(client.getPublicExponent()),unsigned(server.getModulus()),unsigned(server.getPublicExponent()),nonce));if((digest[0]&255)!=Integer.parseInt(code.substring(0,2),16))throw new IllegalArgumentException("Pairing code does not match the TV");return digest;}
 static int key(String a){switch(a){case "HOME":return 3;case "BACK":case "EXIT":return 4;case "UP":return 19;case "DOWN":return 20;case "LEFT":return 21;case "RIGHT":return 22;case "ENTER":return 23;case "volumeUp":return 24;case "volumeDown":return 25;case "powerOff":return 223;case "MENU":return 82;case "INPUT":return 178;case "MUTE":return 164;case "playPause":return 85;case "REWIND":return 89;case "FASTFORWARD":return 90;case "HDMI1":return 243;case "HDMI2":return 244;case "HDMI3":return 245;default:return 0;}}
}
