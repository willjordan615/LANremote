package com.lanremote.bluetooth;

import java.io.*;
import java.security.*;
import java.security.spec.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class RelayPairWire {
 static final int PORT=37942;
 static final byte[] DOMAIN="LANremote pairing v1".getBytes(StandardCharsets.US_ASCII);
 static KeyPair keys()throws Exception{KeyPairGenerator g=KeyPairGenerator.getInstance("EC");g.initialize(new ECGenParameterSpec("secp256r1"));return g.generateKeyPair();}
 static byte[] hash(byte[]... fields)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");d.update(DOMAIN);for(byte[] field:fields){d.update((byte)(field.length>>8));d.update((byte)field.length);d.update(field);}return d.digest();}
 static byte[] mac(byte[] key,String label,byte[] transcript)throws Exception{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));m.update(label.getBytes(StandardCharsets.US_ASCII));return m.doFinal(transcript);}
 static byte[] derive(KeyPair local,byte[] remote,byte[] transcript)throws Exception{
  PublicKey pub=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(remote));
  KeyAgreement agreement=KeyAgreement.getInstance("ECDH");agreement.init(local.getPrivate());agreement.doPhase(pub,true);
  byte[] secret=agreement.generateSecret();try{return mac(mac(transcript,"extract",secret),"pair key",transcript);}finally{Arrays.fill(secret,(byte)0);}
 }
 static String code(byte[] key,byte[] transcript)throws Exception{byte[] b=mac(key,"comparison",transcript);long n=((long)(b[0]&255)<<24)|((long)(b[1]&255)<<16)|((long)(b[2]&255)<<8)|(b[3]&255);return String.format(Locale.ROOT,"%06d",n%1000000);}
 static void field(DataOutputStream out,byte[] b)throws IOException{if(b.length>256)throw new IOException("Pair field too large");out.writeShort(b.length);out.write(b);out.flush();}
 static byte[] field(DataInputStream in,int min,int max)throws IOException{int n=in.readUnsignedShort();if(n<min||n>max)throw new IOException("Invalid pairing field");byte[] b=new byte[n];in.readFully(b);return b;}
 static byte[] transcript(byte[] commitment,byte[] hostPub,byte[] hostNonce,byte[] guestPub,byte[] guestNonce)throws Exception{return hash(commitment,hostPub,hostNonce,guestPub,guestNonce);}
 static boolean verify(byte[] commitment,byte[] guestPub,byte[] nonce)throws Exception{return MessageDigest.isEqual(commitment,hash(guestPub,nonce));}
}
