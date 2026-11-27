package com.lanremote.bluetooth;

import android.content.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

// Discovery is a hint, not proof. Verify the existing TLS pin before copying any pairing record.
final class TvRecovery {
 static String store(TvPlatform p){switch(p){case LG:return "lg_remote";case SAMSUNG:return "samsung_remote";case ANDROID:return "android_tv";default:return "";}}
 static String pin(Context c,TvProfile p){
  if(!TvRecoveryPolicy.secure(p.platform))return "";SharedPreferences prefs=c.getSharedPreferences(store(p.platform),Context.MODE_PRIVATE);
  if(p.platform==TvPlatform.LG&&(prefs.getBoolean("paused_"+p.ip,false)||prefs.getBoolean("transport_legacy_"+p.ip,false)||prefs.getString("key_"+p.ip,"").isEmpty()))return "";
  if(p.platform==TvPlatform.SAMSUNG&&prefs.getString("token_"+p.ip,"").isEmpty())return "";
  return prefs.getString("pin_"+p.ip,"");
 }
 static String find(Context c,TvProfile profile,String pin)throws Exception{
  if(pin.isEmpty()||!TvRecoveryPolicy.secure(profile.platform))return "";
  RelayNetwork network=RelayNetwork.wifi(c);List<TvDiscovery.Found> candidates=profile.platform==TvPlatform.ANDROID?AtvDiscovery.find(c):TvDiscovery.find(c);
  Set<String> addresses=new LinkedHashSet<>();addresses.add(profile.ip);
  for(TvDiscovery.Found d:candidates)if(d.platform==profile.platform&&network.allows(InetAddress.getByName(d.ip)))addresses.add(d.ip);
  if(addresses.size()>TvRecoveryPolicy.MAX_CANDIDATES||addresses.size()<2)return "";
  ExecutorService workers=Executors.newFixedThreadPool(3);List<Callable<String>> tasks=new ArrayList<>();for(String address:addresses)tasks.add(()->verify(network,profile.platform,address,pin)?address:"");
  ArrayList<String> matched=new ArrayList<>();try{for(Future<String> f:workers.invokeAll(tasks,9,TimeUnit.SECONDS)){if(f.isCancelled())return "";try{String address=f.get();if(!address.isEmpty())matched.add(address);}catch(ExecutionException e){return "";}}}finally{workers.shutdownNow();}
  return TvRecoveryPolicy.unique(matched,profile.ip);
 }
 private static String certificatePin(TvPlatform p,X509Certificate certificate)throws Exception{byte[] data=p==TvPlatform.ANDROID?certificate.getPublicKey().getEncoded():certificate.getEncoded();byte[] hash=MessageDigest.getInstance("SHA-256").digest(data);if(p==TvPlatform.ANDROID)return android.util.Base64.encodeToString(hash,android.util.Base64.NO_WRAP);StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(Locale.ROOT,"%02X",b&255));return out.toString();}
 private static boolean verify(RelayNetwork network,TvPlatform p,String address,String expected){
  try{if(!RelayWire.privateIP(address))return false;InetAddress target=InetAddress.getByName(address);if(!network.allows(target))return false;
   X509TrustManager trust=new X509TrustManager(){public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}public void checkClientTrusted(X509Certificate[] cert,String type)throws CertificateException{throw new CertificateException("Not a server");}public void checkServerTrusted(X509Certificate[] cert,String type)throws CertificateException{try{if(cert==null||cert.length==0||!expected.equals(certificatePin(p,cert[0])))throw new CertificateException("TV identity mismatch");}catch(Exception e){throw new CertificateException(e);}}};
   KeyManager[] keys=null;if(p==TvPlatform.ANDROID){KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(AndroidTvRemote.identity(),null);keys=km.getKeyManagers();}
   SSLContext tls=SSLContext.getInstance("TLS");tls.init(keys,new TrustManager[]{trust},null);int port=p==TvPlatform.LG?3001:p==TvPlatform.SAMSUNG?8002:6466;
   try(Socket raw=network.network.getSocketFactory().createSocket()){raw.connect(new InetSocketAddress(target,port),1600);try(SSLSocket socket=(SSLSocket)tls.getSocketFactory().createSocket(raw,address,port,true)){socket.setSoTimeout(2000);socket.startHandshake();return expected.equals(certificatePin(p,(X509Certificate)socket.getSession().getPeerCertificates()[0]));}}
  }catch(Exception e){return false;}
 }
 // Called on main only, with a verified match and still-current profile. Never copy plaintext or rejected credentials.
 static boolean move(Context c,TvProfiles profiles,TvProfile old,String address,String expected){
  TvProfile current=profiles.get(old.id);if(!TvProfile.sameEndpoint(old,current)||!expected.equals(pin(c,old))||!RelayWire.privateIP(address)||address.equals(old.ip))return false;
  for(TvProfile other:profiles.all())if(!other.id.equals(old.id)&&other.platform==old.platform&&other.ip.equals(address))return false;
  SharedPreferences prefs=c.getSharedPreferences(store(old.platform),Context.MODE_PRIVATE);String existing=prefs.getString("pin_"+address,"");if(!existing.isEmpty()&&!existing.equals(expected))return false;
  String[] strings=old.platform==TvPlatform.LG?new String[]{"pin_","key_","nickname_","device_name_","wake_mac_","wake_wired_","wake_wifi_"}:old.platform==TvPlatform.SAMSUNG?new String[]{"pin_","token_"}:new String[]{"pin_"};
  String[] bools=old.platform==TvPlatform.LG?new String[]{"wake_manual_","paused_","transport_legacy_"}:new String[0];
  // Refuse to replace a conflicting preexisting credential even if it has no profile.
  for(String prefix:strings)if((prefix.equals("key_")||prefix.equals("token_"))&&prefs.contains(prefix+address)&&!prefs.getString(prefix+address,"").equals(prefs.getString(prefix+old.ip,"")))return false;
  SharedPreferences.Editor e=prefs.edit();for(String prefix:strings)if(prefs.contains(prefix+old.ip))e.putString(prefix+address,prefs.getString(prefix+old.ip,""));for(String prefix:bools)if(prefs.contains(prefix+old.ip))e.putBoolean(prefix+address,prefs.getBoolean(prefix+old.ip,false));if(!e.commit())return false;
  if(!profiles.move(old,address))return false;
  SharedPreferences.Editor clean=prefs.edit();for(String prefix:strings)clean.remove(prefix+old.ip);for(String prefix:bools)clean.remove(prefix+old.ip);clean.apply();return true;
 }
}
