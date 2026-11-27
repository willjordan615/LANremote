package com.lanremote.bluetooth;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.util.Xml;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import okhttp3.*;
import org.xmlpull.v1.XmlPullParser;

// User-authorized local ECP only. No cloud service, authentication emulation, or retry on denial.
final class RokuRemote {
 interface Observer{void update(String status);}
 private final Activity activity;private final String ip;private final SharedPreferences prefs;private final Observer observer;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
 private volatile int generation;private volatile boolean disposed,blocked;private volatile Call activeCall;
 private boolean ready,connecting,tv,volume,power;private String label="Roku";
 private OkHttpClient client;private NetworkIdentity networkIdentity;
 private static final class NetworkIdentity {final android.net.Network network;final String local;NetworkIdentity(RelayNetwork n){network=n.network;local=n.local.getHostAddress();}boolean matches(RelayNetwork n){return network.equals(n.network)&&local.equals(n.local.getHostAddress());}}
 RokuRemote(Activity a,String address,Observer o){activity=a;ip=address;observer=o;prefs=a.getSharedPreferences("roku_remote",Context.MODE_PRIVATE);worker.allowCoreThreadTimeOut(true);}
 boolean configured(){return prefs.getBoolean("allowed_"+ip,false);}
 boolean ready(){return ready;}
 String label(){return label;}
 boolean supports(String action){return !RokuKeys.key(action,tv,volume,power).isEmpty();}
 void authorize(){prefs.edit().putBoolean("allowed_"+ip,true).apply();connect();}
 void forget(){prefs.edit().remove("allowed_"+ip).apply();disconnect("Roku authorization forgotten.");}
 private void post(int g,Runnable task){main.post(()->{if(!disposed&&g==generation)task.run();});}
 private OkHttpClient transport()throws Exception{
  RelayNetwork n=RelayNetwork.wifi(activity);if(!RelayWire.privateIP(ip)||!n.allows(InetAddress.getByName(ip)))throw new IOException("Roku must be on the same private Wi-Fi subnet.");
  if(client==null||networkIdentity==null||!networkIdentity.matches(n)){if(client!=null)client.connectionPool().evictAll();client=new OkHttpClient.Builder().socketFactory(n.network.getSocketFactory()).proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).connectTimeout(2,TimeUnit.SECONDS).readTimeout(3,TimeUnit.SECONDS).callTimeout(5,TimeUnit.SECONDS).connectionPool(new ConnectionPool(0,1,TimeUnit.SECONDS)).build();networkIdentity=new NetworkIdentity(n);}
  return client;
 }
 private Request.Builder request(String... segments){HttpUrl.Builder url=new HttpUrl.Builder().scheme("http").host(ip).port(8060);for(String part:segments)url.addPathSegment(part);return new Request.Builder().url(url.build());}
 private Response execute(Request request,int g)throws Exception{if(disposed||blocked||g!=generation)throw new IOException("Inactive session");Call call=transport().newCall(request);activeCall=call;if(disposed||blocked||g!=generation){call.cancel();throw new IOException("Inactive session");}return call.execute();}
 private static final class Info {boolean tv,volume,power;String name="Roku";}
 private static Info info(ResponseBody body)throws Exception{
  if(body==null||body.contentLength()>65536)throw new IOException("Invalid device information");ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=body.byteStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>65536)throw new IOException("Device information too large");bytes.write(buffer,0,n);}}
  XmlPullParser parser=Xml.newPullParser();parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false);parser.setInput(new ByteArrayInputStream(bytes.toByteArray()),"UTF-8");Info i=new Info();boolean root=false;String name="",model="";for(int type=parser.getEventType();type!=XmlPullParser.END_DOCUMENT;type=parser.next()){if(type!=XmlPullParser.START_TAG)continue;String tag=parser.getName();if(parser.getDepth()==1){if(!"device-info".equals(tag))throw new IOException("Not a Roku device");root=true;continue;}if(parser.getDepth()!=2)continue;switch(tag){case "is-tv":i.tv="true".equalsIgnoreCase(parser.nextText());break;case "supports-audio-volume-control":i.volume="true".equalsIgnoreCase(parser.nextText());break;case "supports-tv-power-control":i.power="true".equalsIgnoreCase(parser.nextText());break;case "user-device-name":name=parser.nextText();break;case "model-name":model=parser.nextText();break;default:break;}}
  if(!root)throw new IOException("Not a Roku device");i.volume|=i.tv;i.power|=i.tv;i.name=DeviceLabels.first(DeviceLabels.clean(name),DeviceLabels.clean(model),"Roku");return i;
 }
 void connect(){if(disposed||ready||connecting)return;if(!configured()){observer.update("Roku needs explicit local-control approval in TV setup.");return;}blocked=false;connecting=true;int g=++generation;observer.update("Checking Roku device…");try{worker.execute(()->{try(Response response=execute(request("query","device-info").get().build(),g)){if(!response.isSuccessful()){fail(g,response.code());return;}Info data=info(response.body());post(g,()->{connecting=false;ready=true;tv=data.tv;volume=data.volume;power=data.power;label=data.name;observer.update("Roku found • press a button to verify control.");});}catch(Exception e){post(g,()->disconnect("Roku unavailable. Check Wi-Fi, IP and TV power."));}finally{activeCall=null;}});}catch(RejectedExecutionException e){connecting=false;observer.update("Roku busy. Retry connection.");}}
 private void fail(int g,int code){if(g==generation){blocked=true;worker.getQueue().clear();}post(g,()->disconnect(RokuKeys.denied(code)?"Roku denied control (HTTP "+code+"). Check Control by mobile apps = Enabled, then reconnect.":"Roku request failed (HTTP "+code+"). Reconnect to retry."));}
 void action(String action){if(!ready||!configured()||disposed||blocked)return;String key=RokuKeys.key(action,tv,volume,power);if(key.isEmpty())return;int g=generation;try{worker.execute(()->{if(disposed||blocked||g!=generation)return;try(Response r=execute(request("keypress",key).post(RequestBody.create(new byte[0],null)).build(),g)){if(!r.isSuccessful()){if(RokuKeys.denied(r.code())){ // Stop any queued requests immediately, before main-thread notification.
       worker.getQueue().clear();
      }fail(g,r.code());return;}post(g,()->observer.update("Roku accepted remote command."));}catch(Exception e){post(g,()->disconnect("Roku command unavailable. Reconnect to retry."));}finally{activeCall=null;}});}catch(RejectedExecutionException ignored){}}
 void disconnect(String status){generation++;ready=false;connecting=false;worker.getQueue().clear();Call call=activeCall;if(call!=null)call.cancel();observer.update(status);}
 void destroy(){disposed=true;disconnect("Roku resting.");worker.shutdownNow();main.removeCallbacksAndMessages(null);}
}
