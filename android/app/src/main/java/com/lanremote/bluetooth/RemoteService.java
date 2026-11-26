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
 private boolean registered, requesting, sending, stopped, connecting;
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
  0x05,0x0C,0x09,0x01,0xA1,0x01,0x85,0x02,0x15,0x00,0x26,0xFF,0x03,0x19,0x00,0x2A,0xFF,0x03,0x75,0x10,0x95,0x01,0x81,0x00,0xC0);
 private static byte[] bytes(int... v){byte[] b=new byte[v.length];for(int i=0;i<v.length;i++)b[i]=(byte)v[i];return b;}

 public class LocalBinder extends Binder { public RemoteService getService(){return RemoteService.this;} }
 @Override public IBinder onBind(Intent intent){return binder;}
 @Override public void onCreate(){super.onCreate();BluetoothManager m=getSystemService(BluetoothManager.class);adapter=m==null?null:m.getAdapter();
  NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Bluetooth remote",NotificationManager.IMPORTANCE_LOW));
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(intent!=null&&STOP.equals(intent.getAction())){shutdown();return START_NOT_STICKY;}
  stopped=false;startForeground(1,notification());enable();return START_NOT_STICKY;
 }
 private Notification notification(){
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,RemoteService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("LAN Remote Bluetooth").setContentText(status).setContentIntent(open).addAction(new Notification.Action.Builder(null,"Disconnect",stop).build()).setOngoing(true).build();
 }
 private void message(String text){status=text;if(!stopped){try{getSystemService(NotificationManager.class).notify(1,notification());}catch(RuntimeException ignored){}}if(observer!=null)observer.run();}
 public void observe(Runnable value){observer=value;if(value!=null)value.run();}
 public String status(){return status;}
 public boolean ready(){return host!=null&&registered;}
 public boolean registered(){return registered;}
 public String lastAddress(){return getSharedPreferences("remote",MODE_PRIVATE).getString("lastHost","");}
 private void remember(BluetoothDevice d){getSharedPreferences("remote",MODE_PRIVATE).edit().putString("lastHost",d.getAddress()).apply();}
 public void enable(){if(stopped||requesting||registered)return;if(adapter==null||!adapter.isEnabled()){message("Bluetooth unavailable or disabled.");return;}
  requesting=true;message("Requesting HID device profile…");
  try{if(hid!=null)register();else if(!adapter.getProfileProxy(this,listener,BluetoothProfile.HID_DEVICE)){requesting=false;message("Bluetooth HID profile unavailable.");}}
  catch(RuntimeException e){requesting=false;message("HID startup failed: "+e.getMessage());}
  handler.postDelayed(()->{if(requesting&&!registered){requesting=false;message("HID profile not ready. Tap Enable to retry.");}},10000);
 }
 private final BluetoothProfile.ServiceListener listener=new BluetoothProfile.ServiceListener(){
  @Override public void onServiceConnected(int p,BluetoothProfile proxy){handler.post(()->{if(stopped){adapter.closeProfileProxy(p,proxy);return;}hid=(BluetoothHidDevice)proxy;register();});}
  @Override public void onServiceDisconnected(int p){handler.post(()->{hid=null;host=null;registered=false;requesting=false;connecting=false;message("HID profile disconnected. Return to the remote to retry.");});}
 };
 private void register(){if(stopped||registered||hid==null)return;try{
  BluetoothHidDeviceAppSdpSettings sdp=new BluetoothHidDeviceAppSdpSettings("LAN Remote","Bluetooth keyboard remote","LAN Remote",BluetoothHidDevice.SUBCLASS1_KEYBOARD,DESC);
  if(!hid.registerApp(sdp,null,null,getMainExecutor(),callback)){requesting=false;message("HID registration rejected. Tap Enable to retry.");}
 }catch(RuntimeException e){requesting=false;message("HID registration failed: "+e.getMessage());}}
 private final BluetoothHidDevice.Callback callback=new BluetoothHidDevice.Callback(){
  @Override public void onAppStatusChanged(BluetoothDevice plugged,boolean ok){if(stopped)return;registered=ok;requesting=false;connecting=false;if(!ok)host=null;message(ok?"Remote registered. Pair or reconnect.":"Android ended HID registration. Return to the remote to retry.");if(ok){try{List<BluetoothDevice> connected=hid.getConnectedDevices();if(!connected.isEmpty()){host=connected.get(0);remember(host);message("Connected: "+name(host));}else reconnect();}catch(RuntimeException e){message("Reconnect failed: "+e.getMessage());}}}
  @Override public void onConnectionStateChanged(BluetoothDevice d,int state){if(stopped)return;if(state==BluetoothProfile.STATE_CONNECTED){host=d;connecting=false;remember(d);message("Connected: "+name(d));}else if(state==BluetoothProfile.STATE_CONNECTING){connecting=true;message("Connecting to "+name(d)+"…");}else if(state==BluetoothProfile.STATE_DISCONNECTED){if(d.equals(host))host=null;connecting=false;message(host==null?"Disconnected from "+name(d)+". Return to reconnect.":"Connected: "+name(host));}}
  @Override public void onGetReport(BluetoothDevice d,byte type,byte id,int size){if(stopped||hid==null)return;int n=id==1?8:id==2?2:0;if(n>0&&type==BluetoothHidDevice.REPORT_TYPE_INPUT)hid.replyReport(d,type,id,new byte[n]);else hid.reportError(d,BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID);}
 };
 public String name(BluetoothDevice d){try{String n=d.getName();return n==null?d.getAddress():n;}catch(SecurityException e){return "Bluetooth device";}}
 public void resumeRemote(){if(stopped)return;if(!registered){enable();return;}reconnect();}
 private void reconnect(){if(stopped||!registered||hid==null||host!=null||connecting)return;String address=lastAddress();if(address.isEmpty())return;try{for(BluetoothDevice d:adapter.getBondedDevices())if(address.equals(d.getAddress())){connect(d);return;}message("Previously connected device is no longer paired.");}catch(RuntimeException e){message("Reconnect failed: "+e.getMessage());}}
 public void connect(BluetoothDevice d){if(stopped||!registered||hid==null){message("Enable Bluetooth remote first.");return;}if(d.equals(host)){message("Connected: "+name(d));return;}try{if(!hid.connect(d)){connecting=false;message("Connection request rejected. Try pairing from the TV box.");return;}connecting=true;message("Connecting to "+name(d)+"…");handler.postDelayed(()->{if(connecting&&host==null){connecting=false;message("No Bluetooth HID connection yet. Try selecting the phone on the TV.");}},12000);}catch(RuntimeException e){connecting=false;message("Connect failed: "+e.getMessage());}}
 public void send(int report,int usage){if(stopped||host==null||hid==null||!registered||sending)return;BluetoothDevice target=host;BluetoothHidDevice proxy=hid;byte[] data=report==1?new byte[8]:new byte[2];if(report==1)data[2]=(byte)usage;else{data[0]=(byte)usage;data[1]=(byte)(usage>>8);}try{if(!proxy.sendReport(target,report,data)){message("Key report rejected. Reconnect the box.");return;}sending=true;handler.postDelayed(()->{sending=false;try{proxy.sendReport(target,report,new byte[data.length]);}catch(RuntimeException e){message("Key release failed: "+e.getMessage());}},45);}catch(RuntimeException e){message("Key failed: "+e.getMessage());}}
 public void shutdown(){if(stopped)return;stopped=true;handler.removeCallbacksAndMessages(null);BluetoothHidDevice p=hid;hid=null;host=null;registered=false;requesting=false;connecting=false;sending=false;if(p!=null){try{p.unregisterApp();}catch(RuntimeException ignored){}try{adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE,p);}catch(RuntimeException ignored){}}message("Remote stopped. Tap Enable to start.");stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
 @Override public void onDestroy(){shutdown();observer=null;super.onDestroy();}
}
