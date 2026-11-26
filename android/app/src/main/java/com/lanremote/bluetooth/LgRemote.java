package com.lanremote.bluetooth;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.net.*;
import java.io.IOException;
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
 private TextView mirrorStatus;
 private Runnable observer;
 private String pendingInput="";
 private boolean pendingOff=false;
 private String pendingSettings="", pictureRequest="";
 private final EditText mac;
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
  status=new TextView(a);status.setTextSize(13);status.setPadding(0,dp(4),0,dp(4));
  LinearLayout setup=new LinearLayout(a);setup.setOrientation(LinearLayout.VERTICAL);setup.setVisibility(View.GONE);
  Button setupToggle=action("TV setup ▾",()->{});setupToggle.setOnClickListener(v->{boolean show=setup.getVisibility()!=View.VISIBLE;setup.setVisibility(show?View.VISIBLE:View.GONE);setupToggle.setText(show?"TV setup ▴":"TV setup ▾");});
  ip=new EditText(a);ip.setSingleLine(true);ip.setInputType(InputType.TYPE_CLASS_PHONE);ip.setHint("LG TV IPv4 address");ip.setText(prefs.getString("address","192.168.68.66"));setup.addView(ip);
  mac=new EditText(a);mac.setSingleLine(true);mac.setHint("TV MAC address (for power on)");mac.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);mac.setText(prefs.getString("wake_mac_"+ip.getText().toString().trim(),""));setup.addView(mac);
  setup.addView(action("Save TV wake address",()->saveMac()));
  legacy=new CheckBox(a);legacy.setText("Legacy unencrypted Wi-Fi (port 3000)");legacy.setChecked(prefs.getBoolean("legacy",false));setup.addView(legacy);
  rowIn(setup,action("Connect / pair",()->connect()),action("Disconnect",()->disconnect("Disconnected.")));
  setup.addView(action("Forget TV pairing / certificate",()->new AlertDialog.Builder(activity).setMessage("Forget the saved TV identity and pairing? Only do this if the TV was reset or its certificate changed.").setPositiveButton("Forget",(d,w)->{String s=ip.getText().toString().trim();disconnect("Pairing forgotten. Connect again.");prefs.edit().remove("pin_"+s).remove("key_"+s).remove("key_legacy_"+s).apply();}).setNegativeButton("Cancel",null).show()));
  TextView hint=new TextView(a);hint.setTextSize(13);hint.setText("Accept pairing on the TV. SIMPLINK depends on attached devices. For power on, enable TV On With Mobile / Turn on via Wi-Fi and save the TV network MAC address.");setup.addView(hint);
  inputs=new Spinner(a);setup.addView(inputs,new LinearLayout.LayoutParams(-1,dp(48)));
  rowIn(setup,command("Refresh HDMI",()->loadInputs()),command("Switch input",()->switchInput()));
  rowIn(setup,command("Ch −",()->button("CHANNELDOWN")),command("Ch +",()->button("CHANNELUP")));
  rowIn(setup,command("Backspace",()->request("com.webos.service.ime/deleteCharacters",obj("count",1))),command("Enter",()->request("com.webos.service.ime/sendEnterKey",obj())));
  panel.addView(setupToggle,new LinearLayout.LayoutParams(-1,dp(48)));panel.addView(setup);
  ArrayAdapter<String> empty=new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,Collections.singletonList("HDMI / external inputs"));inputs.setAdapter(empty);
  message("Open TV setup to connect.");

 }
 void visible(boolean value){panel.setVisibility(value?View.VISIBLE:View.GONE);}
 void setObserver(Runnable r){observer=r;}
 boolean ready(boolean needPointer){return paired&&(!needPointer||pointerReady);}
 void remoteAction(String command){
  if("volumeDown".equals(command)||"volumeUp".equals(command)){request("audio/"+command,null);return;}
  if("playPause".equals(command)){new AlertDialog.Builder(activity).setTitle("TV playback").setItems(new String[]{"Play","Pause"},(d,i)->button(i==0?"PLAY":"PAUSE")).setNegativeButton("Cancel",null).show();return;}
  if("click".equals(command)){if(!pointerReady||pointer==null){message("TV pointer not ready.");return;}if(!pointer.send("type:click\n\n"))message("TV pointer unavailable.");return;}
  button(command);
 }
 private int dp(int n){return (int)(activity.getResources().getDisplayMetrics().density*n+.5f);}
 private Button action(String text,Runnable run){Button b=new RemoteButton(activity);b.setText(text);b.setAllCaps(false);b.setTextSize(14);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(4),0,dp(4),0);b.setMinHeight(dp(48));b.setOnClickListener(v->{try{run.run();}catch(Exception e){message("TV error: "+e.getMessage());}});return b;}
 private Button command(String t,Runnable r){Button b=action(t,r);buttons.add(b);b.setEnabled(false);return b;}
 private void row(Button... bs){rowIn(panel,bs);}
 private void rowIn(LinearLayout parent,Button... bs){LinearLayout row=new LinearLayout(activity);for(Button b:bs)row.addView(b==null?new View(activity):b,new LinearLayout.LayoutParams(0,dp(48),1));parent.addView(row);}
 void setMirrorStatus(TextView view){mirrorStatus=view;mirrorStatus.setText("TV: "+status.getText());}
 private void message(String s){status.setText(s);if(mirrorStatus!=null)mirrorStatus.setText("TV: "+s);for(Button b:buttons)b.setEnabled(paired);if(observer!=null)observer.run();}
 private boolean validMac(String value){return value.matches("(?i)[0-9a-f]{2}([:-][0-9a-f]{2}){5}");}
 private boolean saveMac(){String target=ip.getText().toString().trim(),value=mac.getText().toString().trim();if(!privateIP(target)||!validMac(value)){message("Enter the TV IP and six-byte MAC address in TV setup.");return false;}prefs.edit().putString("wake_mac_"+target,value).apply();message("TV wake address saved.");return true;}
 void powerDialog(){new AlertDialog.Builder(activity).setTitle("LG TV power").setItems(new String[]{"Power on","Power off"},(d,i)->{if(i==0)wake();else if(paired)request("system/turnOff",null);else{pendingInput="";pendingSettings="";pendingOff=true;if(!connecting)connect();else message("Connecting • power off queued.");}}).setNegativeButton("Cancel",null).show();}
 void quickHdmi(int number){if(number<1||number>3)return;String id="HDMI_"+number;if(paired){request("tv/switchInput",obj("inputId",id));return;}pendingOff=false;pendingSettings="";pendingInput=id;if(!connecting)connect();else message("Connecting • HDMI "+number+" queued.");}
 void quickSettings(){if(paired&&pointerReady){button("MENU");return;}pendingInput="";pendingOff=false;pendingSettings="menu";if(!paired&&!connecting)connect();else if(paired){pointerRequest=request("com.webos.service.networkinput/getPointerInputSocket",null);}}
 void pictureModes(){if(!paired){pendingInput="";pendingOff=false;pendingSettings="picture";if(!connecting)connect();return;}
  // Launch the native picture settings UI instead of writing restricted system settings.
  pictureRequest=request("system.launcher/launch",obj("id","com.palm.app.settings","params",obj("target","picture")));
  if(pictureRequest.isEmpty()){quickSettings();return;}
  String id=pictureRequest;int g=generation;main.postDelayed(()->{if(current(g)&&id.equals(pictureRequest)){pictureRequest="";quickSettings();}},4000);
 }
 private void wake(){String target=ip.getText().toString().trim();String value=prefs.getString("wake_mac_"+target,"");if(!privateIP(target)||!validMac(value)){message("Set the TV MAC address in LG TV setup to enable power on.");new AlertDialog.Builder(activity).setTitle("TV power-on setup").setMessage("In LG TV mode, open TV setup and save the MAC address of the TV's active network adapter. Enable Turn on via Wi-Fi on the TV.").setPositiveButton("OK",null).show();return;}message("Sending TV wake request…");
  new Thread(()->{try{byte[] packet=new byte[102];Arrays.fill(packet,0,6,(byte)255);String[] parts=value.split("[:-]");for(int j=0;j<16;j++)for(int i=0;i<6;i++)packet[6+j*6+i]=(byte)Integer.parseInt(parts[i],16);
   InetAddress tv=InetAddress.getByName(target),broadcast=null,source=null;Enumeration<NetworkInterface> nets=NetworkInterface.getNetworkInterfaces();while(nets.hasMoreElements()){NetworkInterface n=nets.nextElement();if(!n.isUp()||n.isLoopback())continue;for(InterfaceAddress ia:n.getInterfaceAddresses()){if(!(ia.getAddress() instanceof Inet4Address)||ia.getBroadcast()==null)continue;int bits=ia.getNetworkPrefixLength();if(bits<1||bits>30)continue;byte[] x=ia.getAddress().getAddress(),y=tv.getAddress();boolean same=true;for(int k=0;k<bits;k++)if(((x[k/8]>>(7-k%8))&1)!=((y[k/8]>>(7-k%8))&1)){same=false;break;}if(same){broadcast=ia.getBroadcast();source=ia.getAddress();break;}}if(broadcast!=null)break;}
   if(broadcast==null)throw new IOException("Phone and TV must be on the same local subnet.");try(DatagramSocket sock=new DatagramSocket(new InetSocketAddress(source,0))){sock.setBroadcast(true);for(int i=0;i<3;i++){sock.send(new DatagramPacket(packet,packet.length,broadcast,9));Thread.sleep(120);}}main.post(()->{if(!disposed)message("Wake request sent; allow the TV time to start. Power-on is not confirmed.");});
  }catch(Exception e){main.post(()->{if(!disposed)message("TV wake failed: "+e.getMessage());});}},"lg-wake").start();
 }
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
 void connect(){if(disposed)return;String target=ip.getText().toString().trim();if(!privateIP(target)){pendingInput="";pendingOff=false;pendingSettings="";message("Enter the private IPv4 address of your LG TV.");return;}
  boolean plain=legacy.isChecked();if(plain&&!prefs.getBoolean("legacy_"+target,false)){new AlertDialog.Builder(activity).setTitle("Unencrypted connection").setMessage("Older LG TVs may require unencrypted local Wi-Fi. Only use on a trusted home network. Pairing credentials and commands are not encrypted.").setPositiveButton("Use legacy mode",(d,w)->{prefs.edit().putBoolean("legacy_"+target,true).apply();open(target,true);}).setNegativeButton("Cancel",(d,w)->{pendingInput="";pendingOff=false;pendingSettings="";}).setOnCancelListener(d->{pendingInput="";pendingOff=false;pendingSettings="";}).show();return;}open(target,plain);
 }
 private void open(String target,boolean plain){String queued=pendingInput;boolean off=pendingOff;String settings=pendingSettings;disconnect("Connecting…");pendingInput=queued;pendingOff=off;pendingSettings=settings;address=target;pin=prefs.getString("pin_"+target,"");offeredPin="";connecting=true;prefs.edit().putString("address",target).putBoolean("legacy",plain).apply();final int g=generation;final String expected=pin;
  try{client=makeClient(plain,expected,target);String url=(plain?"ws://":"wss://")+target+(plain?":3000":":3001");control=client.newWebSocket(new Request.Builder().url(url).build(),new WebSocketListener(){
   @Override public void onOpen(WebSocket ws,Response response){post(g,()->register(ws,plain));}
   @Override public void onMessage(WebSocket ws,String text){post(g,()->receive(text,plain,g));}
   @Override public void onFailure(WebSocket ws,Throwable e,Response r){String offered=offeredPin;post(g,()->{if(!plain&&expected.isEmpty()&&!offered.isEmpty()){String retryInput=pendingInput;boolean retryOff=pendingOff;String retrySettings=pendingSettings;disconnect("TV certificate needs approval.");new AlertDialog.Builder(activity).setTitle("Trust this LG TV?").setMessage("Confirm this is your own TV at "+target+". Its TLS certificate fingerprint is:\n"+offered+"\nIf unsure, cancel. The TV will separately request pairing approval.").setPositiveButton("Trust TV",(d,w)->{if(!disposed){prefs.edit().putString("pin_"+target,offered).apply();pendingInput=retryInput;pendingOff=retryOff;pendingSettings=retrySettings;open(target,false);}}).setNegativeButton("Cancel",null).show();}else disconnect("TV connection failed: "+e.getMessage()+". Check TV power, IP and pairing settings.");});}
   @Override public void onClosed(WebSocket ws,int code,String reason){post(g,()->disconnect("TV disconnected."));}
  });main.postDelayed(()->{if(current(g)&&connecting&&!paired){disconnect("Pairing timed out. Make sure TV is on and accept its prompt.");}},60000);
  }catch(Exception e){disconnect("TV setup failed: "+e.getMessage());}
 }
 private static JSONObject obj(Object... pairs){JSONObject o=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)o.put((String)pairs[i],pairs[i+1]);}catch(JSONException e){throw new IllegalArgumentException(e);}return o;}
 private String keyName(boolean plain){return (plain?"key_legacy_":"key_")+address;}
 private void register(WebSocket ws,boolean plain){JSONArray perms=new JSONArray(Arrays.asList("LAUNCH","CONTROL_AUDIO","CONTROL_INPUT_TV","CONTROL_INPUT_MEDIA_PLAYBACK","CONTROL_POWER","READ_INPUT_DEVICE_LIST","CONTROL_MOUSE_AND_KEYBOARD","CONTROL_INPUT_TEXT","READ_CURRENT_CHANNEL","READ_APP_STATUS","READ_POWER_STATE","TEST_OPEN","TEST_PROTECTED"));JSONObject manifest=obj("manifestVersion",1,"appVersion","1.0","appName","LAN Remote","permissions",perms,"signed",obj("permissions",perms,"localizedAppNames",obj("","LAN Remote")));JSONObject payload=obj("forcePairing",false,"pairingType","PROMPT","manifest",manifest);String key=prefs.getString(keyName(plain),"");if(!key.isEmpty())try{payload.put("client-key",key);}catch(JSONException ignored){}ws.send(obj("type","register","id","register","payload",payload).toString());message("Approve LAN Remote on the TV if prompted…");}
 private void receive(String text,boolean plain,int g){try{JSONObject o=new JSONObject(text);String type=o.optString("type"),id=o.optString("id");JSONObject p=o.optJSONObject("payload");if("registered".equals(type)){paired=true;connecting=false;if(p!=null&&!p.optString("client-key").isEmpty())prefs.edit().putString(keyName(plain),p.optString("client-key")).apply();message("Connected to LG TV.");String queued=pendingInput;boolean off=pendingOff;pendingInput="";pendingOff=false;if(off){request("system/turnOff",null);return;}if(!queued.isEmpty())request("tv/switchInput",obj("inputId",queued));loadInputs();pointerRequest=request("com.webos.service.networkinput/getPointerInputSocket",null);if("picture".equals(pendingSettings)){pendingSettings="";pictureModes();}return;}if("error".equals(type)){if(!id.isEmpty()&&id.equals(pictureRequest)){pictureRequest="";quickSettings();return;}message("TV rejected "+id+": "+o.optString("error","unknown error"));return;}if(p==null)return;if(!id.isEmpty()&&id.equals(pictureRequest)){pictureRequest="";if(!p.optBoolean("returnValue",false))quickSettings();else message("TV picture settings opened.");return;}if(p.has("returnValue")&&!p.optBoolean("returnValue")){message("TV command rejected: "+p.optString("errorText",id));return;}if(id.equals(inputRequest)){inputIds.clear();ArrayList<String> labels=new ArrayList<>();JSONArray list=p.optJSONArray("devices");if(list!=null)for(int i=0;i<list.length();i++){JSONObject d=list.optJSONObject(i);if(d==null)continue;String input=d.optString("id");if(input.isEmpty())continue;inputIds.add(input);labels.add(d.optString("label",input)+(d.optBoolean("connected")?" • connected":""));}if(labels.isEmpty())labels.add("No inputs reported");ArrayAdapter<String> a=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,labels);inputs.setAdapter(a);message("Connected • "+inputIds.size()+" inputs.");}else if(id.equals(pointerRequest)){openPointer(p.optString("socketPath"),plain,g);}else if(p.has("returnValue"))message("TV command accepted.");}catch(Exception e){message("TV response error: "+e.getMessage());}}
 private void openPointer(String url,boolean plain,int g){try{URI u=new URI(url);int port=plain?3000:3001;if(!address.equals(u.getHost())||u.getPort()!=port||u.getUserInfo()!=null||u.getFragment()!=null||!u.getPath().startsWith("/resources/"))throw new IllegalArgumentException("Unexpected TV pointer address");String endpoint=(plain?"ws://":"wss://")+address+":"+port+u.getRawPath()+(u.getRawQuery()==null?"":"?"+u.getRawQuery());pointer=client.newWebSocket(new Request.Builder().url(endpoint).build(),new WebSocketListener(){@Override public void onOpen(WebSocket w,Response r){post(g,()->{pointerReady=true;message("Connected • navigation ready.");if("menu".equals(pendingSettings)){pendingSettings="";button("MENU");}});}@Override public void onFailure(WebSocket w,Throwable e,Response r){post(g,()->{pointerReady=false;message("TV navigation unavailable: "+e.getMessage());});}@Override public void onClosed(WebSocket w,int c,String r){post(g,()->{pointerReady=false;message("TV navigation disconnected.");});}});}catch(Exception e){message("TV navigation unavailable: "+e.getMessage());}}
 private String request(String path,JSONObject payload){if(!paired||control==null){message("Connect to the TV first.");return "";}String id="req"+(++seq);JSONObject o=obj("type","request","id",id,"uri","ssap://"+path);if(payload!=null)try{o.put("payload",payload);}catch(JSONException ignored){}if(!control.send(o.toString()))message("TV socket unavailable.");return id;}
 private void button(String name){if(!pointerReady||pointer==null){message("TV navigation not ready. Reconnect to TV.");return;}if(!pointer.send("type:button\nname:"+name+"\n\n"))message("TV navigation unavailable.");}
 void keyboard(){EditText entry=new EditText(activity);entry.setHint("Type or paste text");entry.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);entry.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
  AlertDialog d=new AlertDialog.Builder(activity).setTitle("LG TV keyboard").setMessage("Open a text input field on the TV first. Send appends text to the focused field.").setView(entry).setPositiveButton("Send",(dialog,w)->{String value=entry.getText().toString();if(!value.isEmpty())request("com.webos.service.ime/insertText",obj("text",value,"replace",0));}).setNeutralButton("Keys",(dialog,w)->new AlertDialog.Builder(activity).setTitle("TV keyboard keys").setItems(new String[]{"Backspace","Enter"},(d,i)->{if(i==0)request("com.webos.service.ime/deleteCharacters",obj("count",1));else request("com.webos.service.ime/sendEnterKey",obj());}).show()).setNegativeButton("Cancel",null).create();
  d.setOnShowListener(x->{entry.requestFocus();d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE|android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);});d.show();}
 private void loadInputs(){inputRequest=request("tv/getExternalInputList",null);}
 private void switchInput(){int i=inputs.getSelectedItemPosition();if(i<0||i>=inputIds.size()){message("Refresh inputs first.");return;}request("tv/switchInput",obj("inputId",inputIds.get(i)));}
 private void disconnect(String why){pendingInput="";pendingOff=false;pendingSettings="";pictureRequest="";generation++;paired=false;connecting=false;pointerReady=false;if(pointer!=null)pointer.cancel();if(control!=null)control.cancel();pointer=null;control=null;if(client!=null){client.dispatcher().executorService().shutdown();client.connectionPool().evictAll();client=null;}message(why);}
 void destroy(){disposed=true;disconnect("Disconnected.");main.removeCallbacksAndMessages(null);}
}
