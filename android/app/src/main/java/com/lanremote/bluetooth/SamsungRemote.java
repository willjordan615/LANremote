package com.lanremote.bluetooth;
import android.app.*;
import android.content.*;
import android.os.*;
import android.util.Base64;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import okhttp3.*;
import org.json.*;

final class SamsungRemote {
 interface Observer {void update(String status);}
 private final Activity activity;private final SharedPreferences prefs;private final String ip;private final boolean restricted;private final Observer observer;
 private final Handler main=new Handler(Looper.getMainLooper());private OkHttpClient client;private WebSocket socket;
 private boolean ready,connecting,disposed;private int generation;private volatile String offered="";
 SamsungRemote(Activity a,String ip,boolean restricted,Observer observer){activity=a;this.ip=ip;this.restricted=restricted;this.observer=observer;prefs=a.getSharedPreferences("samsung_remote",Context.MODE_PRIVATE);}
 boolean ready(){return ready;}
 boolean configured(){return !prefs.getString("pin_"+ip,"").isEmpty()&&!prefs.getString("token_"+ip,"").isEmpty();}
 private static String fingerprint(X509Certificate cert)throws CertificateException{try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(Locale.ROOT,"%02X",b&255));return out.toString();}catch(GeneralSecurityException e){throw new CertificateException(e);}}
 private void post(int g,Runnable r){main.post(()->{if(!disposed&&g==generation)r.run();});}
 void connect(boolean explicit){if(disposed||ready||connecting||(!explicit&&!configured())||(restricted&&!configured()))return;
  try{if(!RelayWire.privateIP(ip)||!RelayNetwork.wifi(activity).allows(InetAddress.getByName(ip)))throw new IllegalArgumentException("TV must be on the same private Wi-Fi subnet.");
   final String expected=prefs.getString("pin_"+ip,""),token=prefs.getString("token_"+ip,"");offered="";
   X509TrustManager tm=new X509TrustManager(){public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}public void checkClientTrusted(X509Certificate[] c,String s)throws CertificateException{throw new CertificateException("Unsupported client certificate");}public void checkServerTrusted(X509Certificate[] chain,String s)throws CertificateException{if(chain==null||chain.length==0)throw new CertificateException("Missing TV certificate");String actual=fingerprint(chain[0]);if(expected.isEmpty()){offered=actual;throw new CertificateException("Approve certificate first");}if(!expected.equals(actual))throw new CertificateException("TV certificate changed");}};
   SSLContext tls=SSLContext.getInstance("TLS");tls.init(null,new TrustManager[]{tm},null);
   RelayNetwork network=RelayNetwork.wifi(activity);
   client=new OkHttpClient.Builder().socketFactory(network.network.getSocketFactory()).sslSocketFactory(tls.getSocketFactory(),tm).hostnameVerifier((host,session)->ip.equals(host)).proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false).connectTimeout(7,TimeUnit.SECONDS).readTimeout(0,TimeUnit.SECONDS).pingInterval(45,TimeUnit.SECONDS).build();
   String name=Base64.encodeToString("LANremote".getBytes(java.nio.charset.StandardCharsets.UTF_8),Base64.NO_WRAP);
   HttpUrl.Builder url=new HttpUrl.Builder().scheme("https").host(ip).port(8002).addPathSegments("api/v2/channels/samsung.remote.control").addQueryParameter("name",name);if(!token.isEmpty())url.addQueryParameter("token",token);
   connecting=true;final int g=++generation;observer.update("Connecting to Samsung TV…");
   socket=client.newWebSocket(new Request.Builder().url(url.build()).build(),new WebSocketListener(){
    @Override public void onMessage(WebSocket ws,String text){post(g,()->{try{JSONObject o=new JSONObject(text);String event=o.optString("event");if("ms.channel.connect".equals(event)){JSONObject data=o.optJSONObject("data");String received=data==null?"":data.optString("token","");if(!received.isEmpty()&&!received.matches("[0-9A-Za-z_-]{1,128}")){disconnect("Invalid pairing response.");return;}if(!explicit&&!received.isEmpty()&&!received.equals(token)){disconnect("Pairing changed. Open TV setup to approve.");return;}if(explicit&&!received.isEmpty())prefs.edit().putString("token_"+ip,received).apply();if(token.isEmpty()&&received.isEmpty()){disconnect("No pairing token returned; this model is not supported.");return;}connecting=false;ready=true;observer.update("Samsung TV connected");}else if("ms.channel.unauthorized".equals(event)||"ms.channel.timeOut".equals(event)){disconnect("TV did not authorize the remote. Open TV setup to pair.");}}catch(Exception ignored){}});}
    @Override public void onFailure(WebSocket ws,Throwable e,Response response){String offeredPin=offered;post(g,()->{disconnect("Samsung connection unavailable.");if(explicit&&!restricted&&expected.isEmpty()&&!offeredPin.isEmpty()&&!activity.isFinishing()){new AlertDialog.Builder(activity).setTitle("Trust this Samsung TV?").setMessage("Confirm this is your TV at "+ip+" on your home Wi-Fi. Certificate SHA-256:\n"+offeredPin+"\nThe TV will separately ask you to approve the remote.").setPositiveButton("Trust TV",(d,w)->{if(disposed)return;prefs.edit().putString("pin_"+ip,offeredPin).apply();connect(true);}).setNegativeButton("Cancel",null).show();}});}
    @Override public void onClosed(WebSocket ws,int code,String reason){post(g,()->disconnect("Samsung TV disconnected."));}
   });
   main.postDelayed(()->{if(g==generation&&connecting)disconnect("Pairing timed out. Approve the remote on the TV and retry.");},60000);
  }catch(Exception e){disconnect("Samsung connection unavailable: "+e.getMessage());}
 }
 void action(String action){if(!ready||socket==null)return;String key=TvPlatform.samsungKey(action);if(key.isEmpty()||socket.queueSize()>4096)return;try{JSONObject params=new JSONObject().put("Cmd","Click").put("DataOfCmd",key).put("Option","false").put("TypeOfRemote","SendRemoteKey");if(!socket.send(new JSONObject().put("method","ms.remote.control").put("params",params).toString()))disconnect("Samsung connection unavailable.");}catch(JSONException ignored){}}
 void forget(){disconnect("Samsung pairing forgotten.");prefs.edit().remove("pin_"+ip).remove("token_"+ip).apply();}
 void disconnect(String status){generation++;connecting=false;ready=false;main.removeCallbacksAndMessages(null);if(socket!=null)socket.cancel();socket=null;if(client!=null){client.dispatcher().executorService().shutdown();client.connectionPool().evictAll();client=null;}observer.update(status);}
 void destroy(){disposed=true;disconnect("Samsung connection resting.");}
}
