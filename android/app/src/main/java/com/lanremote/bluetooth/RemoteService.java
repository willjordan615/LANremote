package com.lanremote.bluetooth;

import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
import java.util.*;

public class RemoteService extends Service {
 private BluetoothAdapter adapter;
 private BluetoothHidDevice hid;
 private BluetoothDevice host;
 private RelayHost relay;
 private RelayPermissions permissions(){return new RelayPermissions(this,lastAddress());}
 private android.content.SharedPreferences remotePrefs(){return getSharedPreferences("remote",MODE_PRIVATE);}
 private String sharePref(){return "share_"+lastAddress();}
 private RelayPairHost.Request pairingRequest;
 private static final int PAIR_NOTICE=2;
 private int connectAttempt;
 private boolean switching;
 private boolean registered, requesting, sending, stopped, connecting;
 private final ArrayDeque<KeyReport> queue=new ArrayDeque<>();
 private static final class KeyReport {final int id;final byte[] data;final boolean motion;KeyReport(int id,byte[] data){this(id,data,false);}KeyReport(int id,byte[] data,boolean motion){this.id=id;this.data=data;this.motion=motion;}}
 private int mouseAccepted,mouseRejected,mouseBusy,clickAccepted,clickRejected;private String mouseProtocol="not reported",mouseError="";
 public String mouseDiagnostics(){return "Experimental box cursor: "+(remotePrefs().getBoolean("box_cursor_trial",false)?"enabled":"disabled")+"\n"+ "Bluetooth mouse report ID 3 (original three-byte format)\nHost protocol: "+mouseProtocol+"\nMotion reports accepted by Android: "+mouseAccepted+"\nClick presses accepted: "+clickAccepted+"\nClick presses/releases rejected: "+clickRejected+"\nMotion rejected: "+mouseRejected+"\nDropped while busy: "+mouseBusy+(mouseError.isEmpty()?"":"\nLast error: "+mouseError)+"\nAcceptance is not proof of cursor movement on the box.";}
 private int sendGeneration;
 private String status="Starting Bluetooth remote…";
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final LocalBinder binder=new LocalBinder();
 private Runnable observer;
 private static final String CHANNEL="remote", STOP="com.lanremote.bluetooth.STOP";
 private static final byte[] DESC = bytes(
  0x05,0x01,0x09,0x06,0xA1,0x01,0x85,0x01,
  0x05,0x07,0x19,0xE0,0x29,0xE7,0x15,0x00,0x25,0x01,0x75,0x01,0x95,0x08,0x81,0x02,
  0x95,0x01,0x75,0x08,0x81,0x01,
  0x95,0x05,0x75,0x01,0x05,0x08,0x19,0x01,0x29,0x05,0x91,0x02,0x95,0x01,0x75,0x03,0x91,0x01,
  0x95,0x06,0x75,0x08,0x15,0x00,0x25,0x65,0x05,0x07,0x19,0x00,0x29,0x65,0x81,0x00,0xC0,
  0x05,0x0C,0x09,0x01,0xA1,0x01,0x85,0x02,0x15,0x00,0x26,0xFF,0x03,0x19,0x00,0x2A,0xFF,0x03,0x75,0x10,0x95,0x01,0x81,0x00,0xC0,
  // Report 3: mouse buttons, relative X and Y (three bytes).
  0x05,0x01,0x09,0x02,0xA1,0x01,0x85,0x03,0x09,0x01,0xA1,0x00,
  0x05,0x09,0x19,0x01,0x29,0x03,0x15,0x00,0x25,0x01,0x95,0x03,0x75,0x01,0x81,0x02,
  0x95,0x01,0x75,0x05,0x81,0x01,0x05,0x01,0x09,0x30,0x09,0x31,0x15,0x81,0x25,0x7F,0x75,0x08,0x95,0x02,0x81,0x06,0xC0,0xC0);
 private static byte[] bytes(int... v){byte[] b=new byte[v.length];for(int i=0;i<v.length;i++)b[i]=(byte)v[i];return b;}

 public class LocalBinder extends Binder { public RemoteService getService(){return RemoteService.this;} }
 @Override public IBinder onBind(Intent intent){return binder;}
 @Override public void onCreate(){super.onCreate();BluetoothManager m=getSystemService(BluetoothManager.class);adapter=m==null?null:m.getAdapter();
  NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Bluetooth remote",NotificationManager.IMPORTANCE_LOW));nm.createNotificationChannel(new NotificationChannel("relay_approval","Wi-Fi remote approvals",NotificationManager.IMPORTANCE_HIGH));
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(intent!=null&&STOP.equals(intent.getAction())){remotePrefs().edit().putBoolean("box_reconnect_paused",true).apply();shutdown();return START_NOT_STICKY;}
  stopped=false;startForeground(1,notification());enable();return START_NOT_STICKY;
 }
 private Notification notification(){
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,RemoteService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("LAN Remote Bluetooth").setContentText(status).setContentIntent(open).addAction(new Notification.Action.Builder(null,"Disconnect",stop).build()).setOngoing(true).build();
 }
 private void message(String text){status=text;if(!stopped){try{getSystemService(NotificationManager.class).notify(1,notification());}catch(RuntimeException ignored){}}if(observer!=null)observer.run();}
 public void observe(Runnable value){observer=value;if(value!=null)value.run();}
 public String status(){return status+(relay!=null?" • Wi-Fi sharing on":"");}
 public boolean ready(){return host!=null&&registered&&matches(host);}
 public boolean registered(){return registered;}
 public String lastAddress(){android.content.SharedPreferences p=getSharedPreferences("remote",MODE_PRIVATE);return HostIdentity.initial(p.getString("selectedHost",null),p.getString("lastHost",""));}
 private boolean matches(BluetoothDevice d){return d!=null&&HostIdentity.matches(lastAddress(),d.getAddress());}
 private void saveTarget(String address){getSharedPreferences("remote",MODE_PRIVATE).edit().putString("selectedHost",address).remove("lastHost").apply();}
 public String nickname(BluetoothDevice d){return getSharedPreferences("remote",MODE_PRIVATE).getString("nickname_"+d.getAddress(),"");}
 public void nickname(BluetoothDevice d,String value){getSharedPreferences("remote",MODE_PRIVATE).edit().putString("nickname_"+d.getAddress(),value.trim()).apply();if(matches(d))message(ready()?"Connected: "+name(d):"Selected: "+name(d));}
 private void reject(BluetoothDevice d){try{if(hid!=null)hid.disconnect(d);}catch(RuntimeException ignored){}}
 private void accepted(BluetoothDevice d){if(!d.equals(host)){clearQueue();mouseAccepted=mouseRejected=mouseBusy=clickAccepted=clickRejected=0;mouseError="";}host=d;DeviceLabels.learnBluetooth(this,d);connecting=false;switching=false;connectAttempt++;message("Connected: "+name(d));if(relay==null&&remotePrefs().getBoolean(sharePref(),false))try{startRelay();}catch(Exception e){message("Connected • sharing paused: "+e.getMessage());}}
 private void reconcile(){if(hid==null)return;try{BluetoothDevice selected=null;for(BluetoothDevice d:hid.getConnectedDevices()){if(matches(d))selected=d;else if(!lastAddress().isEmpty())reject(d);}if(selected!=null)accepted(selected);else{host=null;if(lastAddress().isEmpty())message("Select your paired box in Bluetooth setup.");else reconnect();}}catch(RuntimeException e){message("Reconnect failed: "+e.getMessage());}}
 public void forgetTarget(){stopRelay();BluetoothDevice old=host;releaseOld();saveTarget("");clearQueue();host=null;connecting=false;switching=false;connectAttempt++;if(old!=null)reject(old);message("No target selected. Choose a paired device.");}
 private void releaseOld(){if(host!=null&&hid!=null&&sending){try{hid.sendReport(host,1,new byte[8]);hid.sendReport(host,2,new byte[2]);hid.sendReport(host,3,new byte[3]);}catch(RuntimeException ignored){}}}

 public void enable(){if(stopped||requesting||registered)return;if(adapter==null||!adapter.isEnabled()){message("Bluetooth unavailable or disabled.");return;}
  requesting=true;message("Requesting HID device profile…");
  try{if(hid!=null)register();else if(!adapter.getProfileProxy(this,listener,BluetoothProfile.HID_DEVICE)){requesting=false;message("Bluetooth HID profile unavailable.");}}
  catch(RuntimeException e){requesting=false;message("HID startup failed: "+e.getMessage());}
  handler.postDelayed(()->{if(requesting&&!registered){requesting=false;message("HID profile not ready. Tap Enable to retry.");}},10000);
 }
 private final BluetoothProfile.ServiceListener listener=new BluetoothProfile.ServiceListener(){
  @Override public void onServiceConnected(int p,BluetoothProfile proxy){handler.post(()->{if(stopped){adapter.closeProfileProxy(p,proxy);return;}hid=(BluetoothHidDevice)proxy;register();});}
  @Override public void onServiceDisconnected(int p){handler.post(()->{clearQueue();connectAttempt++;switching=false;hid=null;host=null;registered=false;requesting=false;connecting=false;message("HID profile disconnected. Return to the remote to retry.");});}
 };
 private void register(){if(stopped||registered||hid==null)return;try{
  BluetoothHidDeviceAppSdpSettings sdp=new BluetoothHidDeviceAppSdpSettings("LAN Remote","Bluetooth keyboard and mouse remote","LAN Remote",BluetoothHidDevice.SUBCLASS1_COMBO,DESC);
  if(!hid.registerApp(sdp,null,null,getMainExecutor(),callback)){requesting=false;message("HID registration rejected. Tap Enable to retry.");}
 }catch(RuntimeException e){requesting=false;message("HID registration failed: "+e.getMessage());}}
 private final BluetoothHidDevice.Callback callback=new BluetoothHidDevice.Callback(){
  @Override public void onAppStatusChanged(BluetoothDevice plugged,boolean ok){if(stopped)return;registered=ok;requesting=false;connecting=false;switching=false;connectAttempt++;if(!ok){clearQueue();host=null;message("Android ended HID registration. Return to the remote to retry.");return;}reconcile();}
  @Override public void onConnectionStateChanged(BluetoothDevice d,int state){if(stopped)return;
   if(state==BluetoothProfile.STATE_CONNECTED){if(matches(d)){accepted(d);}else if(lastAddress().isEmpty()){message("Paired with "+name(d)+". Select it in Bluetooth setup to enable controls.");}else{reject(d);}}
   else if(state==BluetoothProfile.STATE_CONNECTING&&matches(d)){connecting=true;message("Connecting to "+name(d)+"…");}
   else if(state==BluetoothProfile.STATE_DISCONNECTED){if(d.equals(host)){clearQueue();host=null;mouseProtocol="not reported";}if(switching){switching=false;connecting=false;reconnect();}else if(matches(d)){connecting=false;connectAttempt++;message("Disconnected from "+name(d)+". Return to reconnect.");}}
  }
  @Override public void onSetProtocol(BluetoothDevice d,byte protocol){if(matches(d))mouseProtocol=protocol==BluetoothHidDevice.PROTOCOL_BOOT_MODE?"boot":protocol==BluetoothHidDevice.PROTOCOL_REPORT_MODE?"report":"unknown ("+protocol+")";}
  @Override public void onGetReport(BluetoothDevice d,byte type,byte id,int size){if(stopped||hid==null||(!lastAddress().isEmpty()&&!matches(d)))return;int n=id==1?8:id==2?2:id==3?3:0;if(n>0&&type==BluetoothHidDevice.REPORT_TYPE_INPUT)hid.replyReport(d,type,id,new byte[n]);else hid.reportError(d,BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID);}
 };
 public String name(BluetoothDevice d){try{String n=d.getName();String nick=nickname(d);return !nick.isEmpty()?nick:n==null?d.getAddress():n;}catch(SecurityException e){return "Bluetooth device";}}
 public void resumeRemote(){if(stopped)return;if(!registered){enable();return;}reconnect();}
 private void reconnect(){if(stopped||!registered||hid==null||ready()||connecting||switching)return;String address=lastAddress();if(address.isEmpty())return;try{for(BluetoothDevice d:adapter.getBondedDevices())if(matches(d)){connectTarget(d);return;}message("Selected device is no longer paired. Choose another in setup.");}catch(RuntimeException e){message("Reconnect failed: "+e.getMessage());}}
 public void connect(BluetoothDevice d){if(stopped||!registered||hid==null){message("Enable Bluetooth remote first.");return;}if(d.getBondState()!=BluetoothDevice.BOND_BONDED){message("Pair this device first.");return;}
  if(!matches(d)){stopRelay();releaseOld();clearQueue();host=null;connecting=false;switching=false;connectAttempt++;saveTarget(d.getAddress());mouseProtocol="not reported";}
  connectTarget(d);
 }
 private void connectTarget(BluetoothDevice d){if(!matches(d)||stopped||!registered||hid==null)return;try{
   if(hid.getConnectionState(d)==BluetoothProfile.STATE_CONNECTED){for(BluetoothDevice other:hid.getConnectedDevices())if(!matches(other))reject(other);accepted(d);return;}
   if(connecting||switching)return;
   List<BluetoothDevice> others=hid.getDevicesMatchingConnectionStates(new int[]{BluetoothProfile.STATE_CONNECTED,BluetoothProfile.STATE_CONNECTING,BluetoothProfile.STATE_DISCONNECTING});
   boolean waiting=false;for(BluetoothDevice other:others)if(!matches(other)){waiting=true;reject(other);}
   if(waiting){switching=true;int token=++connectAttempt;message("Switching to "+name(d)+"…");handler.postDelayed(()->{if(token==connectAttempt&&switching){switching=false;message("Previous host still connected. Tap Connect to retry.");}},6000);return;}
   if(hid.getConnectionState(d)!=BluetoothProfile.STATE_CONNECTING&&!hid.connect(d)){message("Connection request rejected. Select the phone on the TV box or retry.");return;}
   connecting=true;int token=++connectAttempt;message("Connecting to "+name(d)+"…");handler.postDelayed(()->{if(token==connectAttempt&&connecting&&!ready()){connecting=false;message("No Bluetooth HID connection yet. Select the phone on the box or retry.");}},12000);
  }catch(RuntimeException e){connecting=false;switching=false;message("Connect failed: "+e.getMessage());}}
 public String startRelay()throws Exception{if(!ready()||stopped)throw new IllegalStateException("Connect Bluetooth first.");if(relay==null){relay=new RelayHost(this,handler,this::receiveRelay,name(host),this::pairingChanged,permissions());remotePrefs().edit().putBoolean(sharePref(),true).apply();message("Connected: "+name(host));}return relay.invitation();}
 public RelayPairHost.Request pendingPairing(){return pairingRequest;}
 private void pairingChanged(RelayPairHost.Request request){if(request!=null&&(relay==null||!ready()||stopped)){request.decide(false);return;}pairingRequest=request;NotificationManager nm=getSystemService(NotificationManager.class);if(request==null)nm.cancel(PAIR_NOTICE);else{try{PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);nm.notify(PAIR_NOTICE,new Notification.Builder(this,"relay_approval").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("LANremote connection request").setContentText("Open LANremote to compare the code and approve.").setContentIntent(open).setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true).build());}catch(RuntimeException ignored){}}if(observer!=null)observer.run();}
 public void decidePairing(RelayPairHost.Request request,boolean yes){if(request!=null&&request==pairingRequest){request.decide(yes&&ready()&&relay!=null);pairingChanged(null);}}
 public java.util.Map<String,String> approvedGuests(){return permissions().guests();}
 public void revokeGuest(String id){permissions().revoke(id);}
 public void revokeAllGuests(){pauseRelay();permissions().clear();remotePrefs().edit().putBoolean(sharePref(),false).apply();}
 public void stopRelay(){remotePrefs().edit().putBoolean(sharePref(),false).apply();pauseRelay();}
 private void pauseRelay(){if(relay!=null){relay.close();relay=null;pairingChanged(null);if(!stopped)message(ready()?"Connected: "+name(host):"Wi-Fi sharing stopped.");}}
 private byte receiveRelay(byte[] command){if(!ready()||stopped||relay==null)return 1;if(command.length==0)return 2;try{if(command[0]==0&&command.length==1)return 0;if(command[0]==1&&command.length==4){int report=command[1]&255,usage=((command[2]&255)<<8)|(command[3]&255);if(!RelayWire.allowed(report,usage)||queue.size()>=512)return 2;send(report,usage);return 0;}if(command[0]==3&&command.length==3){int x=command[1],y=command[2];if(!RelayWire.validMovement(x,y))return 2;return movePointer(x,y)?(byte)0:(byte)2;}if(command[0]==2&&command.length<=501){sendText(new String(command,1,command.length-1,java.nio.charset.StandardCharsets.UTF_8));return 0;}}catch(RuntimeException ignored){}return 2;}
 private void clearQueue(){queue.clear();sending=false;sendGeneration++;}
 public boolean movePointer(int x,int y){if(!remotePrefs().getBoolean("box_cursor_trial",false))return false;return enqueueMotion(x,y);}
 public boolean testMouseMotion(){return enqueueMotion(60,0);}
 private boolean enqueueMotion(int x,int y){if(!ready()||stopped||hid==null||!RelayWire.validMovement(x,y))return false;if(!queue.isEmpty()){mouseBusy++;return false;}queue.add(new KeyReport(3,HidKeys.movement(x,y),true));pump();return true;}
 public void repeatKey(int report,int usage){if(ready()&&!sending&&queue.isEmpty())send(report,usage);}
 public void send(int report,int usage){if(!ready()||stopped)return;if(report==3)queue.removeIf(r->r.motion);if(queue.size()>=512){message("Input queue full.");return;}queue.add(new KeyReport(report,HidKeys.report(report,usage)));pump();}
 public void sendText(String text){if(!ready()||stopped)throw new IllegalStateException("Connect the vSeeBox first.");List<byte[]> reports=HidKeys.text(text);if(queue.size()+reports.size()>512)throw new IllegalStateException("Wait for the current text to finish.");for(byte[] data:reports)queue.add(new KeyReport(1,data));pump();}
 private void pump(){if(stopped||!ready()||hid==null||sending||queue.isEmpty())return;KeyReport r=queue.remove();BluetoothDevice target=host;BluetoothHidDevice proxy=hid;int generation=sendGeneration;try{
  if(r.motion){if(!proxy.sendReport(target,r.id,r.data)){mouseRejected++;mouseError="Android rejected the movement report";}else mouseAccepted++;sending=true;handler.postDelayed(()->{if(generation!=sendGeneration)return;sending=false;pump();},15);return;}
  if(!proxy.sendReport(target,r.id,r.data)){if(r.id==3)clickRejected++;clearQueue();message("Input report rejected. Reconnect the box.");return;}if(r.id==3)clickAccepted++;sending=true;
  handler.postDelayed(()->{if(generation!=sendGeneration||stopped||!target.equals(host))return;try{if(!proxy.sendReport(target,r.id,new byte[r.data.length])){if(r.id==3)clickRejected++;clearQueue();message("Input release rejected. Reconnect the box.");return;}}catch(RuntimeException e){clearQueue();message("Input release failed: "+e.getMessage());return;}handler.postDelayed(()->{if(generation!=sendGeneration)return;sending=false;pump();},15);},35);
 }catch(RuntimeException e){if(r.motion){mouseRejected++;mouseError=e.getClass().getSimpleName();}clearQueue();message("Input failed: "+e.getMessage());}}
 public void shutdown(){if(stopped)return;stopped=true;pauseRelay();releaseOld();clearQueue();connectAttempt++;switching=false;handler.removeCallbacksAndMessages(null);BluetoothHidDevice p=hid;hid=null;host=null;registered=false;requesting=false;connecting=false;sending=false;if(p!=null){try{p.unregisterApp();}catch(RuntimeException ignored){}try{adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE,p);}catch(RuntimeException ignored){}}message("Remote stopped. Tap Enable to start.");stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
 @Override public void onDestroy(){shutdown();observer=null;super.onDestroy();}
}
