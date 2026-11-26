package com.lanremote.bluetooth;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import okhttp3.*;
import org.json.*;

final class LgRemote {
 private final Activity activity;
 private final SharedPreferences prefs;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final LinearLayout panel;
 private final TextView status;
 private final EditText ip;
 private final CheckBox legacy;
 private final Spinner inputs;
 private final ArrayList<String> inputIds=new ArrayList<>();
 private final ArrayList<Button> buttons=new ArrayList<>();
 private OkHttpClient client;
 private WebSocket control,pointer;
 private String address="",pin="";
 private boolean paired=false,pointerReady=false,connecting=false,disposed=false;
 private int generation=0,seq=0;
 private String inputRequest="",pointerRequest="";
 private volatile String offeredPin="";
 LgRemote(Activity a,LinearLayout parent){activity=a;prefs=a.getSharedPreferences("lg_remote",Context.MODE_PRIVATE);
  panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);parent.addView(panel);panel.setVisibility(View.GONE);
  status=new TextView(a);status.setPadding(0,dp(10),0,dp(10));panel.addView(status);
  ip=new EditText(a);ip.setSingleLine(true);ip.setInputType(InputType.TYPE_CLASS_PHONE);ip.setHint("LG TV IPv4 address");ip.setText(prefs.getString("address","192.168.68.66"));panel.addView(ip);
  legacy=new CheckBox(a);legacy.setText("Legacy unencrypted Wi-Fi (port 3000)");legacy.setChecked(prefs.getBoolean("legacy",false));panel.addView(legacy);
  row(action("Connect / pair",()->connect()),action("Disconnect",()->disconnect("Disconnected.")));
  TextView label=new TextView(a);label.setText("HDMI / external inputs");panel.addView(label);
  inputs=new Spinner(a);panel.addView(inputs);
  row(command("Refresh inputs",()->loadInputs()),command("Switch input",()->switchInput()));
  row(command("Power off",()->new AlertDialog.Builder(activity).setMessage("Turn off the LG TV?").setPositiveButton("Power off",(d,w)->request("system/turnOff",null)).setNegativeButton("Cancel",null).show()),command("Inputs",()->button("INPUT")),command("Home",()->button("HOME")));
  row(null,command("▲",()->button("UP")),null);row(command("◀",()->button("LEFT")),command("OK",()->button("ENTER")),command("▶",()->button("RIGHT")));row(null,command("▼",()->button("DOWN")),null);
  row(command("Back",()->button("BACK")),command("Settings",()->button("MENU")),command("Exit",()->button("EXIT")));
  row(command("Vol −",()->request("audio/volumeDown",null)),command("Mute",()->button("MUTE")),command("Vol +",()->request("audio/volumeUp",null)));
  row(command("Ch −",()->button("CHANNELDOWN")),command("Ch +",()->button("CHANNELUP")));
  row(command("Rewind",()->button("REWIND")),command("Play",()->button("PLAY")),command("Pause",()->button("PAUSE")),command("Fast fwd",()->button("FASTFORWARD")));
  panel.addView(action("Forget TV pairing / certificate",()->new AlertDialog.Builder(activity).setMessage("Forget the saved TV identity and pairing? Only do this if the TV was reset or its certificate changed.").setPositiveButton("Forget",(d,w)->{String s=ip.getText().toString().trim();disconnect("Pairing forgotten. Connect again.");prefs.edit().remove("pin_"+s).remove("key_"+s).remove("key_legacy_"+s).apply();}).setNegativeButton("Cancel",null).show()));
  TextView hint=new TextView(a);hint.setText("Keep the TV on and the phone on the same Wi-Fi. Accept the connection prompt on the TV. HDMI-CEC forwarding depends on SIMPLINK and the attached device. Power-on from standby is not supported yet.");panel.addView(hint);message("Connect to your LG TV.");
 }
 void visible(boolean value){panel.setVisibility(value?View.VISIBLE:View.GONE);}
 private int dp(int n){return (int)(activity.getResources().getDisplayMetrics().density*n+.5f);}
 private Button action(String text,Runnable run){Button b=new Button(activity);b.setText(text);b.setMinHeight(dp(48));b.setOnClickListener(v->{try{run.run();}catch(Exception e){message("TV error: "+e.getMessage());}});return b;}
 private Button command(String t,Runnable r){Button b=action(t,r);buttons.add(b);b.setEnabled(false);return b;}
 private void row(Button... bs){LinearLayout row=new LinearLayout(activity);for(Button b:bs)row.addView(b==null?new View(activity):b,new LinearLayout.LayoutParams(0,dp(60),1));panel.addView(row);}
 private void message(String s){status.setText(s);for(Button b:buttons)b.setEnabled(paired);}
 private static boolean privateIP(String s){String[] parts=s.split("\\.",-1);if(parts.length!=4)return false;int[] p=new int[4];try{for(int i=0;i<4;i++){if(!parts[i].matches("[0-9]{1,3}"))return false;p[i]=Integer.parseInt(parts[i]);if(p[i]>255||!parts[i].equals(Integer.toString(p[i])))return false;}}catch(Exception e){return false;}return p[0]==10||(p[0]==192&&p[1]==168)||(p[0]==172&&p[1]>=16&&p[1]<=31);}
 private boolean current(int g){return !disposed&&g==generation;}
 private void post(int g,Runnable r){main.post(()->{if(current(g))r.run();});}
 private static String fingerprint(X509Certificate cert)throws CertificateException{try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02X",b&255));return out.toString();}catch(GeneralSecurityException e){throw new CertificateException(e);}}
 private OkHttpClient makeClient(boolean plain,String expected,String target)throws Exception{
  OkHttpClient.Builder b=new OkHttpClient.Builder().connectTimeout(7,TimeUnit.SECONDS).readTimeout(0,TimeUnit.SECONDS).pingInterval(20,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).proxy(Proxy.NO_PROXY);
  if(!plain){X509TrustManager tm=new X509TrustManager(){public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}public void checkClientTrusted(X509Certificate[] c,String s)throws CertificateException{throw new CertificateException("Client trust not supported");}public void checkServerTrusted(X509Certificate[] chain,String s)throws CertificateException{if(chain==null||chain.length==0)throw new CertificateException("No TV certificate");String actual=fingerprint(chain[0]);if(expected.isEmpty()){offeredPin=actual;throw new CertificateException("TV identity needs approval");}if(!actual.equals(expected))throw new CertificateException("TV certificate changed");}};
   SSLContext tls=SSLContext.getInstance("TLS");tls.init(null,new TrustManager[]{tm},new SecureRandom());b.sslSocketFactory(tls.getSocketFactory(),tm).hostnameVerifier((host,session)->host.equals(target)&&!expected.isEmpty());
  }return b.build();
 }
 void connect(){if(disposed)return;String target=ip.getText().toString().trim();if(!privateIP(target)){message("Enter the private IPv4 address of your LG TV.");return;}
  boolean plain=legacy.isChecked();if(plain&&!prefs.getBoolean("legacy_"+target,false)){new AlertDialog.Builder(activity).setTitle("Unencrypted connection").setMessage("Older LG TVs may require unencrypted local Wi-Fi. Only use on a trusted home network. Pairing credentials and commands are not encrypted.").setPositiveButton("Use legacy mode",(d,w)->{prefs.edit().putBoolean("legacy_"+target,true).apply();open(target,true);}).setNegativeButton("Cancel",null).show();return;}open(target,plain);
 }
 private void open(String target,boolean plain){disconnect("Connecting…");address=target;pin=prefs.getString("pin_"+target,"");offeredPin="";connecting=true;prefs.edit().putString("address",target).putBoolean("legacy",plain).apply();final int g=generation;final String expected=pin;
  try{client=makeClient(plain,expected,target);String url=(plain?"ws://":"wss://")+target+(plain?":3000":":3001");control=client.newWebSocket(new Request.Builder().url(url).build(),new WebSocketListener(){
   @Override public void onOpen(WebSocket ws,Response response){post(g,()->register(ws,plain));}
   @Override public void onMessage(WebSocket ws,String text){post(g,()->receive(text,plain,g));}
   @Override public void onFailure(WebSocket ws,Throwable e,Response r){String offered=offeredPin;post(g,()->{if(!plain&&expected.isEmpty()&&!offered.isEmpty()){disconnect("TV certificate needs approval.");new AlertDialog.Builder(activity).setTitle("Trust this LG TV?").setMessage("Confirm this is your own TV at "+target+". Its TLS certificate fingerprint is:\n"+offered+"\nIf unsure, cancel. The TV will separately request pairing approval.").setPositiveButton("Trust TV",(d,w)->{if(!disposed){prefs.edit().putString("pin_"+target,offered).apply();open(target,false);}}).setNegativeButton("Cancel",null).show();}else disconnect("TV connection failed: "+e.getMessage()+". Check TV power, IP and pairing settings.");});}
   @Override public void onClosed(WebSocket ws,int code,String reason){post(g,()->disconnect("TV disconnected."));}
  });main.postDelayed(()->{if(current(g)&&connecting&&!paired){disconnect("Pairing timed out. Make sure TV is on and accept its prompt.");}},60000);
  }catch(Exception e){disconnect("TV setup failed: "+e.getMessage());}
 }
 private static JSONObject obj(Object... pairs){JSONObject o=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)o.put((String)pairs[i],pairs[i+1]);}catch(JSONException e){throw new IllegalArgumentException(e);}return o;}
 private String keyName(boolean plain){return (plain?"key_legacy_":"key_")+address;}
 private void register(WebSocket ws,boolean plain){JSONArray perms=new JSONArray(Arrays.asList("LAUNCH","CONTROL_AUDIO","CONTROL_INPUT_TV","CONTROL_INPUT_MEDIA_PLAYBACK","CONTROL_POWER","READ_INPUT_DEVICE_LIST","CONTROL_MOUSE_AND_KEYBOARD","CONTROL_INPUT_TEXT","READ_CURRENT_CHANNEL","READ_APP_STATUS","READ_POWER_STATE","TEST_OPEN","TEST_PROTECTED"));JSONObject manifest=obj("manifestVersion",1,"appVersion","1.0","appName","LAN Remote","permissions",perms,"signed",obj("permissions",perms,"localizedAppNames",obj("","LAN Remote")));JSONObject payload=obj("forcePairing",false,"pairingType","PROMPT","manifest",manifest);String key=prefs.getString(keyName(plain),"");if(!key.isEmpty())try{payload.put("client-key",key);}catch(JSONException ignored){}ws.send(obj("type","register","id","register","payload",payload).toString());message("Approve LAN Remote on the TV if prompted…");}
 private void receive(String text,boolean plain,int g){try{JSONObject o=new JSONObject(text);String type=o.optString("type"),id=o.optString("id");JSONObject p=o.optJSONObject("payload");if("registered".equals(type)){paired=true;connecting=false;if(p!=null&&!p.optString("client-key").isEmpty())prefs.edit().putString(keyName(plain),p.optString("client-key")).apply();message("Connected to LG TV.");loadInputs();pointerRequest=request("com.webos.service.networkinput/getPointerInputSocket",null);return;}if("error".equals(type)){message("TV rejected "+id+": "+o.optString("error","unknown error"));return;}if(p==null)return;if(p.has("returnValue")&&!p.optBoolean("returnValue")){message("TV command rejected: "+p.optString("errorText",id));return;}if(id.equals(inputRequest)){inputIds.clear();ArrayList<String> labels=new ArrayList<>();JSONArray list=p.optJSONArray("devices");if(list!=null)for(int i=0;i<list.length();i++){JSONObject d=list.optJSONObject(i);if(d==null)continue;String input=d.optString("id");if(input.isEmpty())continue;inputIds.add(input);labels.add(d.optString("label",input)+(d.optBoolean("connected")?" • connected":""));}if(labels.isEmpty())labels.add("No inputs reported");ArrayAdapter<String> a=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,labels);inputs.setAdapter(a);message("Connected • "+inputIds.size()+" inputs.");}else if(id.equals(pointerRequest)){openPointer(p.optString("socketPath"),plain,g);}else if(p.has("returnValue"))message("TV command accepted.");}catch(Exception e){message("TV response error: "+e.getMessage());}}
 private void openPointer(String url,boolean plain,int g){try{URI u=new URI(url);int port=plain?3000:3001;if(!address.equals(u.getHost())||u.getPort()!=port||u.getUserInfo()!=null||u.getFragment()!=null||!u.getPath().startsWith("/resources/"))throw new IllegalArgumentException("Unexpected TV pointer address");String endpoint=(plain?"ws://":"wss://")+address+":"+port+u.getRawPath()+(u.getRawQuery()==null?"":"?"+u.getRawQuery());pointer=client.newWebSocket(new Request.Builder().url(endpoint).build(),new WebSocketListener(){@Override public void onOpen(WebSocket w,Response r){post(g,()->{pointerReady=true;message("Connected • navigation ready.");});}@Override public void onFailure(WebSocket w,Throwable e,Response r){post(g,()->{pointerReady=false;message("TV navigation unavailable: "+e.getMessage());});}@Override public void onClosed(WebSocket w,int c,String r){post(g,()->{pointerReady=false;message("TV navigation disconnected.");});}});}catch(Exception e){message("TV navigation unavailable: "+e.getMessage());}}
 private String request(String path,JSONObject payload){if(!paired||control==null){message("Connect to the TV first.");return "";}String id="req"+(++seq);JSONObject o=obj("type","request","id",id,"uri","ssap://"+path);if(payload!=null)try{o.put("payload",payload);}catch(JSONException ignored){}if(!control.send(o.toString()))message("TV socket unavailable.");return id;}
 private void button(String name){if(!pointerReady||pointer==null){message("TV navigation not ready. Reconnect to TV.");return;}if(!pointer.send("type:button\nname:"+name+"\n\n"))message("TV navigation unavailable.");}
 private void loadInputs(){inputRequest=request("tv/getExternalInputList",null);}
 private void switchInput(){int i=inputs.getSelectedItemPosition();if(i<0||i>=inputIds.size()){message("Refresh inputs first.");return;}request("tv/switchInput",obj("inputId",inputIds.get(i)));}
 private void disconnect(String why){generation++;paired=false;connecting=false;pointerReady=false;if(pointer!=null)pointer.cancel();if(control!=null)control.cancel();pointer=null;control=null;if(client!=null){client.dispatcher().executorService().shutdown();client.connectionPool().evictAll();client=null;}message(why);}
 void destroy(){disposed=true;disconnect("Disconnected.");main.removeCallbacksAndMessages(null);}
}
