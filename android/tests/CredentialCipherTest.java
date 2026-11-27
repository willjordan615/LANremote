package com.lanremote.bluetooth;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.*;
public final class CredentialCipherTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 interface Attempt{void run()throws Exception;}
 static void reject(Attempt a)throws Exception{try{a.run();throw new AssertionError("Accepted invalid data");}catch(GeneralSecurityException expected){}}
 public static void main(String[] args)throws Exception{
  SecretKeySpec k=new SecretKeySpec(new byte[32],"AES"),other=new SecretKeySpec(new byte[]{1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},"AES");
  String s=CredentialCipher.seal(k,"lg_remote","key_10.0.0.2","old-token");check(!s.contains("old-token")&&s.startsWith(CredentialCipher.PREFIX));check(CredentialCipher.open(k,"lg_remote","key_10.0.0.2",s).equals("old-token"));check(!s.equals(CredentialCipher.seal(k,"lg_remote","key_10.0.0.2","old-token")));
  reject(()->CredentialCipher.open(k,"lg_remote","key_10.0.0.3",s));reject(()->CredentialCipher.open(k,"samsung_remote","key_10.0.0.2",s));reject(()->CredentialCipher.open(other,"lg_remote","key_10.0.0.2",s));
  byte[] raw=Base64.getDecoder().decode(s.substring(CredentialCipher.PREFIX.length()));raw[raw.length-1]^=1;reject(()->CredentialCipher.open(k,"lg_remote","key_10.0.0.2",CredentialCipher.PREFIX+Base64.getEncoder().encodeToString(raw)));reject(()->CredentialCipher.open(k,"lg_remote","key_10.0.0.2","plaintext"));
  check(CredentialCipher.secret("remote","relayInvite")&&!CredentialCipher.secret("remote","nickname_abc"));check(CredentialCipher.secret("relay_permissions_123","master")&&CredentialCipher.secret("relay_permissions_123","key_abc"));check(CredentialCipher.secret("sony_remote","psk_10.0.0.2")&&CredentialCipher.secret("android_tv","pin_10.0.0.2")&&CredentialCipher.secret("samsung_remote","token_10.0.0.2"));
  System.out.println("Credential encryption, nonce uniqueness and tamper rejection passed");
 }
}
