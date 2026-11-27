package com.lanremote.bluetooth;
import android.app.*;
import android.content.*;
import android.os.*;
import android.security.keystore.*;
import android.widget.EditText;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;

// On-screen Polo pairing authenticates the server public key; subsequent sessions pin it.
// No ADB, shell commands, privileged APIs or official-app impersonation.
final class AndroidTvRemote {
 interface Observer{void update(String status);}
 private final Activity activity;private final String ip;private final boolean restricted;private final Observer observer;private final SharedPreferences prefs;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ThreadPoolExecutor writer=new ThreadPoolExecutor(1,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
 private volatile int generation;private volatile boolean disposed,ready;private boolean connecting;private volatile Socket socket;
 private volatile OutputStream output;private volatile int features;private AlertDialog codeDialog;private volatile CompletableFuture<String> codeFuture;
 AndroidTvRemote(Activity a,String ip,boolean restricted,Observer o){activity=a;this.ip=ip;this.restricted=restricted;observer=o;prefs=SecurePrefs.open(a,"android_tv");writer.allowCoreThreadTimeOut(true);}
 boolean configured(){return !prefs.getString("pin_"+ip,"").isEmpty();}
 boolean ready(){return ready;}
 boolean supports(String a){return a!=null&&AtvWire.key(a)!=0&&(features&2)!=0;}
 private void post(int g,Runnable r){main.post(()->{if(!disposed&&g==generation)r.run();});}
 private boolean current(int g){return !disposed&&g==generation;}
 private static String pin(X509Certificate c)throws Exception{byte[] d=MessageDigest.getInstance("SHA-256").digest(c.getPublicKey().getEncoded());return android.util.Base64.encodeToString(d,android.util.Base64.NO_WRAP);}
 static synchronized KeyStore identity()throws Exception{
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);String alias="lanremote_androidtv_v2";
  if(!ks.containsAlias(alias)){KeyPairGenerator g=KeyPairGenerator.getInstance("RSA","AndroidKeyStore");g.initialize(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY|KeyProperties.PURPOSE_DECRYPT).setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA384,KeyProperties.DIGEST_SHA512).setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,KeyProperties.SIGNATURE_PADDING_RSA_PSS).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1).setCertificateSubject(new X500Principal("CN=LANremote-"+UUID.randomUUID())).setCertificateSerialNumber(new java.math.BigInteger(63,new SecureRandom()).add(java.math.BigInteger.ONE)).build());g.generateKeyPair();}
  return ks;
 }
 private SSLSocket open(int port,String expected,boolean pairing,KeyStore ks,int g)throws Exception{
  if(!RelayWire.privateIP(ip))throw new IOException("Invalid local IP");RelayNetwork net=RelayNetwork.wifi(activity);InetAddress target=InetAddress.getByName(ip);if(!net.allows(target))throw new IOException("TV must be on the same private Wi-Fi subnet");
  KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(ks,null);
  X509TrustManager trust=new X509TrustManager(){public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}public void checkClientTrusted(X509Certificate[] c,String a)throws CertificateException{throw new CertificateException("Not a server");}public void checkServerTrusted(X509Certificate[] c,String a)throws CertificateException{try{if(c==null||c.length==0||!(c[0].getPublicKey() instanceof RSAPublicKey))throw new CertificateException("Invalid TV certificate");if(!pairing&&!pin(c[0]).equals(expected))throw new CertificateException("TV identity changed");}catch(Exception e){throw new CertificateException(e);}}};
  SSLContext ctx=SSLContext.getInstance("TLS");ctx.init(km.getKeyManagers(),new TrustManager[]{trust},null);
  Socket raw=net.network.getSocketFactory().createSocket();synchronized(this){if(!current(g)){raw.close();throw new IOException("Session closed");}socket=raw;}
  try{raw.connect(new InetSocketAddress(target,port),5000);SSLSocket tls=(SSLSocket)ctx.getSocketFactory().createSocket(raw,ip,port,true);synchronized(this){if(!current(g)){tls.close();throw new IOException("Session closed");}socket=tls;}tls.setEnabledProtocols(Arrays.stream(tls.getSupportedProtocols()).filter(x->x.equals("TLSv1.2")||x.equals("TLSv1.3")).toArray(String[]::new));tls.setSoTimeout(12000);tls.startHandshake();return tls;}catch(Exception e){raw.close();throw e;}
 }
 void connect(boolean explicit){if(disposed||connecting||ready)return;boolean pairing=!configured();if(pairing&&(!explicit||restricted)){observer.update("Pair Android TV in setup first.");return;}connecting=true;int g=++generation;observer.update(pairing?"Requesting Android TV pairing…":"Connecting to Android TV…");new Thread(()->run(g,pairing),"androidtv-session").start();}
 private void run(int g,boolean pairing){try{KeyStore ks=identity();String expected=prefs.getString("pin_"+ip,"");if(pairing){try(SSLSocket tls=open(6467,"",true,ks,g)){InputStream in=tls.getInputStream();OutputStream out=tls.getOutputStream();AtvWire.write(out,AtvWire.polo(10,AtvWire.concat(AtvWire.str(1,"atvremote"),AtvWire.str(2,"LANremote"))));AtvWire.expect(in,11);byte[] enc=AtvWire.concat(AtvWire.num(1,3),AtvWire.num(2,6));AtvWire.write(out,AtvWire.polo(20,AtvWire.concat(AtvWire.msg(1,enc),AtvWire.num(3,1))));AtvWire.expect(in,20);AtvWire.write(out,AtvWire.polo(30,AtvWire.concat(AtvWire.msg(1,enc),AtvWire.num(2,1))));AtvWire.expect(in,31);
   X509Certificate peer=(X509Certificate)tls.getSession().getPeerCertificates()[0];RSAPublicKey client=(RSAPublicKey)ks.getCertificate("lanremote_androidtv_v2").getPublicKey();RSAPublicKey server=(RSAPublicKey)peer.getPublicKey();CompletableFuture<String> future=new CompletableFuture<>();codeFuture=future;post(g,()->askCode(future));String code=future.get(60,TimeUnit.SECONDS);codeFuture=null;if(!current(g))return;byte[] secret=AtvWire.secret(client,server,code);AtvWire.write(out,AtvWire.polo(40,AtvWire.msg(1,secret)));AtvWire.expect(in,41);expected=pin(peer);if(!current(g))return;prefs.edit().putString("pin_"+ip,expected).apply();}
  }
  try(SSLSocket tls=open(6466,expected,false,ks,g)){tls.setSoTimeout(20000);InputStream in=tls.getInputStream();OutputStream out=tls.getOutputStream();output=out;boolean negotiated=false;while(current(g)){AtvWire.Fields fields=AtvWire.parse(AtvWire.read(in));if(fields.has(1)){features=(int)AtvWire.parse(fields.message(1)).number(1)&99;if((features&2)==0)throw new IOException("TV does not offer remote keys");byte[] info=AtvWire.concat(AtvWire.str(1,android.os.Build.MODEL),AtvWire.str(2,android.os.Build.MANUFACTURER),AtvWire.num(3,1),AtvWire.str(4,"1"),AtvWire.str(5,"LANremote"),AtvWire.str(6,"1.2"));send(out,AtvWire.msg(1,AtvWire.concat(AtvWire.num(1,features),AtvWire.msg(2,info))));}else if(fields.has(2)){send(out,AtvWire.msg(2,AtvWire.num(1,features)));negotiated=true;}else if(fields.has(8)){send(out,AtvWire.msg(9,AtvWire.num(1,AtvWire.parse(fields.message(8)).number(1))));}else if(fields.has(40)&&negotiated){post(g,()->{connecting=false;ready=true;observer.update("Android TV connected.");});}else if(fields.has(3))throw new IOException("TV rejected remote protocol");}}
 }catch(Exception e){post(g,()->disconnect("Android TV unavailable: "+(e.getMessage()==null?"pairing or connection failed":e.getMessage())+". Check TV Remote Service and retry."));}finally{if(current(g))post(g,()->{if(current(g))disconnect("Android TV disconnected.");});}}
 private void askCode(CompletableFuture<String> future){if(disposed||restricted){future.cancel(false);return;}EditText field=new EditText(activity);field.setSingleLine(true);field.setHint("6-character code");field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Pair Android TV").setMessage("Enter the code displayed on your TV.").setView(field).setPositiveButton("Pair",null).setNegativeButton("Cancel",(d,w)->future.cancel(false)).setOnCancelListener(d->future.cancel(false)).create();codeDialog=dialog;dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String code=field.getText().toString().trim();if(!code.matches("[0-9a-fA-F]{6}")){field.setError("Enter six hexadecimal characters");return;}future.complete(code);dialog.dismiss();}));dialog.show();}
 private void send(OutputStream out,byte[] message)throws IOException{synchronized(out){AtvWire.write(out,message);}}
 void action(String action){if(!ready||!supports(action))return;int g=generation,key=AtvWire.key(action);try{writer.execute(()->{if(!current(g)||!ready)return;OutputStream out=output;if(out==null)return;try{send(out,AtvWire.msg(10,AtvWire.concat(AtvWire.num(1,key),AtvWire.num(2,3))));}catch(IOException e){post(g,()->disconnect("Android TV connection lost."));}});}catch(RejectedExecutionException ignored){}}
 void forget(){prefs.edit().remove("pin_"+ip).apply();disconnect("Android TV pairing forgotten.");}
 void disconnect(String status){Socket old;synchronized(this){generation++;old=socket;socket=null;}ready=false;connecting=false;output=null;features=0;writer.getQueue().clear();CompletableFuture<String> future=codeFuture;if(future!=null)future.cancel(false);codeFuture=null;if(codeDialog!=null){codeDialog.dismiss();codeDialog=null;}if(old!=null)try{old.close();}catch(IOException ignored){}observer.update(status);}
 void destroy(){disposed=true;disconnect("Android TV resting.");writer.shutdownNow();main.removeCallbacksAndMessages(null);}
}
