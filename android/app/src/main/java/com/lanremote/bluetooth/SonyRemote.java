package com.lanremote.bluetooth;
import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.EditText;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

// Sony's documented, user-configured IP control. Fetch the actual model's key map.
final class SonyRemote {
 interface Observer{void update(String status);}
 private final Activity activity;private final String ip;private final boolean restricted;private final Observer observer;
 private final SharedPreferences prefs;private final Handler main=new Handler(Looper.getMainLooper());
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
 private volatile int generation;private volatile boolean disposed,blocked;private volatile HttpURLConnection connection;
 private boolean ready,connecting;private Map<String,String> keys=Collections.emptyMap();
 SonyRemote(Activity a,String ip,boolean restricted,Observer observer){this.activity=a;this.ip=ip;this.restricted=restricted;this.observer=observer;prefs=SecurePrefs.open(a,"sony_remote");worker.allowCoreThreadTimeOut(true);}
 boolean configured(){return !prefs.getString("psk_"+ip,"").isEmpty();}
 boolean ready(){return ready;}
 boolean supports(String action){if("playPause".equals(action))return supports("PLAY")||supports("PAUSE");return !SonyKeys.command(action,keys).isEmpty();}
 private void post(int g,Runnable r){main.post(()->{if(!disposed&&g==generation)r.run();});}
 void setup(){if(restricted){connect(false);return;}EditText field=new EditText(activity);field.setSingleLine(true);field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);field.setHint("TV's pre-shared key");field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(64)});field.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);AlertDialog d=new AlertDialog.Builder(activity).setTitle("Sony BRAVIA IP control").setMessage("On your TV, enable IP control with a pre-shared key. Enter that key here. The Sony HTTP interface sends it unencrypted, so use only trusted home Wi-Fi. LANremote saves it privately on this phone.").setView(field).setPositiveButton("Connect",null).setNegativeButton("Cancel",null).create();d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{String key=field.getText().toString();if(!key.matches("[\\x21-\\x7E]{1,64}")){field.setError("Enter the configured printable key");return;}disconnect("Connecting…");prefs.edit().putString("psk_"+ip,key).remove("denied_"+ip).apply();d.dismiss();connect(true);}));d.show();}
 void forget(){prefs.edit().remove("psk_"+ip).remove("denied_"+ip).apply();disconnect("Sony key forgotten.");}
 void connect(boolean explicit){if(disposed||connecting||ready)return;if(!configured()){observer.update("Configure Sony IP control first.");return;}if(!explicit&&prefs.getBoolean("denied_"+ip,false)){observer.update("Sony rejected the saved key. Open TV setup.");return;}blocked=false;connecting=true;int g=++generation;observer.update("Reading Sony remote capabilities…");String psk=prefs.getString("psk_"+ip,"");try{worker.execute(()->{try{
   String body=new JSONObject().put("method","getRemoteControllerInfo").put("id",1).put("params",new JSONArray()).put("version","1.0").toString();
   String result=exchange(g,"/sony/system","application/json",body,psk,false);
   JSONArray list=new JSONObject(result).getJSONArray("result").getJSONArray(1);Map<String,String> found=new HashMap<>();for(int i=0;i<Math.min(256,list.length());i++){JSONObject item=list.optJSONObject(i);if(item==null)continue;String name=item.optString("name"),value=item.optString("value");if(name.matches("[A-Za-z0-9_]{1,40}")&&SonyKeys.valid(value))found.put(name,value);}
   if(found.isEmpty())throw new IOException("No supported remote commands");
   post(g,()->{keys=found;ready=true;connecting=false;observer.update("Sony remote ready • "+found.size()+" advertised commands");});
  }catch(Exception e){failed(g,e);}});}catch(RejectedExecutionException e){connecting=false;observer.update("Sony remote busy.");}}
 private static final class Denied extends IOException{Denied(){super("Access denied");}}
 private String exchange(int g,String path,String type,String body,String key,boolean soap)throws Exception{
  if(disposed||blocked||g!=generation)throw new IOException("Session closed");RelayNetwork network=RelayNetwork.wifi(activity);if(!RelayWire.privateIP(ip)||!network.allows(InetAddress.getByName(ip)))throw new IOException("TV must be on the same private Wi-Fi subnet");
  HttpURLConnection c=(HttpURLConnection)network.network.openConnection(new URL("http://"+ip+path),Proxy.NO_PROXY);connection=c;
  try{if(disposed||blocked||g!=generation)throw new IOException("Session closed");c.setInstanceFollowRedirects(false);c.setConnectTimeout(2500);c.setReadTimeout(3000);c.setRequestMethod("POST");c.setRequestProperty("Content-Type",type);c.setRequestProperty("X-Auth-PSK",key);if(soap)c.setRequestProperty("SOAPACTION","\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"");c.setDoOutput(true);byte[] bytes=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}int status=c.getResponseCode();if(status==401||status==403)throw new Denied();if(status<200||status>=300)throw new IOException("HTTP "+status);ByteArrayOutputStream result=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(result.size()+n>65536)throw new IOException("Response too large");result.write(buffer,0,n);}}return new String(result.toByteArray(),StandardCharsets.UTF_8);
  }finally{c.disconnect();connection=null;}
 }
 private void failed(int g,Exception e){if(g!=generation)return;blocked=true;worker.getQueue().clear();post(g,()->{if(e instanceof Denied)prefs.edit().putBoolean("denied_"+ip,true).apply();disconnect(e instanceof Denied?"Sony denied access. Check the configured key and IP control settings.":"Sony remote unavailable. Check TV power, Wi-Fi and IP control.");});}
 void action(String action){if(!ready||blocked||disposed)return;String code=SonyKeys.command(action,keys);if(code.isEmpty())return;int g=generation;String key=prefs.getString("psk_"+ip,"");try{worker.execute(()->{if(blocked||disposed||g!=generation)return;try{exchange(g,"/sony/ircc","text/xml; charset=UTF-8",SonyKeys.envelope(code),key,true);}catch(Exception e){failed(g,e);}});}catch(RejectedExecutionException ignored){}}
 void disconnect(String message){generation++;blocked=true;ready=false;connecting=false;keys=Collections.emptyMap();worker.getQueue().clear();HttpURLConnection c=connection;if(c!=null)c.disconnect();observer.update(message);}
 void destroy(){disposed=true;disconnect("Sony remote resting.");worker.shutdownNow();main.removeCallbacksAndMessages(null);}
}
