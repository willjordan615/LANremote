package com.lanremote.bluetooth;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
final class CredentialCipher {
 static final String PREFIX="enc:v1:";
 static boolean secret(String store,String key){
  if("remote".equals(store))return "relayInvite".equals(key);
  if("lg_remote".equals(store))return key.startsWith("key_")||key.startsWith("pin_");
  if("samsung_remote".equals(store))return key.startsWith("token_")||key.startsWith("pin_");
  if("sony_remote".equals(store))return key.startsWith("psk_");
  if("android_tv".equals(store))return key.startsWith("pin_");
  return store.startsWith("relay_permissions_")&&("master".equals(key)||key.startsWith("key_"));
 }
 private static byte[] aad(String store,String key){return ("LANremote credentials v1\0"+store+"\0"+key).getBytes(StandardCharsets.UTF_8);}
 static String seal(SecretKey key,String store,String field,String value)throws GeneralSecurityException{
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);byte[] iv=cipher.getIV();if(iv.length!=12)throw new GeneralSecurityException("Unexpected nonce size");cipher.updateAAD(aad(store,field));byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));byte[] bytes=new byte[iv.length+encrypted.length];System.arraycopy(iv,0,bytes,0,iv.length);System.arraycopy(encrypted,0,bytes,iv.length,encrypted.length);return PREFIX+Base64.getEncoder().encodeToString(bytes);
 }
 static String open(SecretKey key,String store,String field,String value)throws GeneralSecurityException{
  if(!value.startsWith(PREFIX)||value.length()>131072)throw new GeneralSecurityException("Invalid credential format");byte[] bytes;try{bytes=Base64.getDecoder().decode(value.substring(PREFIX.length()));}catch(IllegalArgumentException e){throw new GeneralSecurityException("Invalid credential encoding",e);}if(bytes.length<28)throw new GeneralSecurityException("Invalid credential length");
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,bytes,0,12));cipher.updateAAD(aad(store,field));return new String(cipher.doFinal(bytes,12,bytes.length-12),StandardCharsets.UTF_8);
 }
}
