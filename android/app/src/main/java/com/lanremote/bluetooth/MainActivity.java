package com.lanremote.bluetooth;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
 private BluetoothAdapter adapter;
 private BluetoothHidDevice hid;
 private BluetoothDevice host;
 private boolean registered=false, requesting=false;
 private TextView status;
 private Spinner paired;
 private final ArrayList<BluetoothDevice> devices=new ArrayList<>();
 private final ArrayList<Button> controls=new ArrayList<>();
 private final Handler handler=new Handler(Looper.getMainLooper());
 private boolean sending=false;
 // Report 1: ordinary 8-byte keyboard. Report 2: 16-bit consumer usage.
 private static final byte[] DESC = bytes(
  0x05,0x01,0x09,0x06,0xA1,0x01,0x85,0x01,
  0x05,0x07,0x19,0xE0,0x29,0xE7,0x15,0x00,0x25,0x01,0x75,0x01,0x95,0x08,0x81,0x02,
  0x95,0x01,0x75,0x08,0x81,0x01,
  0x95,0x05,0x75,0x01,0x05,0x08,0x19,0x01,0x29,0x05,0x91,0x02,0x95,0x01,0x75,0x03,0x91,0x01,
  0x95,0x06,0x75,0x08,0x15,0x00,0x25,0x65,0x05,0x07,0x19,0x00,0x29,0x65,0x81,0x00,0xC0,
  0x05,0x0C,0x09,0x01,0xA1,0x01,0x85,0x02,0x15,0x00,0x26,0xFF,0x03,0x19,0x00,0x2A,0xFF,0x03,0x75,0x10,0x95,0x01,0x81,0x00,0xC0);
 private static byte[] bytes(int... v){byte[] b=new byte[v.length];for(int i=0;i<v.length;i++)b[i]=(byte)v[i];return b;}
 @Override public void onCreate(Bundle saved){super.onCreate(saved);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  BluetoothManager manager=getSystemService(BluetoothManager.class);adapter=manager==null?null:manager.getAdapter();
  ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(18),dp(16),dp(18));scroll.addView(root);setContentView(scroll);
  TextView title=new TextView(this);title.setText("LAN Remote • Bluetooth");title.setTextSize(24);root.addView(title);
  status=new TextView(this);status.setTextSize(16);status.setPadding(0,dp(10),0,dp(10));root.addView(status);
  root.addView(action("1. Enable Bluetooth remote",()->startHid()));
  root.addView(action("2. Make phone discoverable",()->discoverable()));
  root.addView(action("3. Refresh paired devices",()->refresh()));
  paired=new Spinner(this);root.addView(paired);root.addView(action("Connect selected device",()->connectSelected()));
  root.addView(action("Disconnect",()->{if(hid!=null&&host!=null)try{hid.disconnect(host);}catch(SecurityException e){message(e.getMessage());}}));
  row(root,null,key("▲",1,0x52),null);row(root,key("◀",1,0x50),key("OK",1,0x28),key("▶",1,0x4F));row(root,null,key("▼",1,0x51),null);
  row(root,key("Back / Esc",1,0x29),key("Home",2,0x223),key("Menu",2,0x40));
  row(root,key("Vol −",2,0xEA),key("Mute",2,0xE2),key("Vol +",2,0xE9));
  row(root,key("Previous",2,0xB6),key("Play / Pause",2,0xCD),key("Next",2,0xB5));
  TextView hint=new TextView(this);hint.setText("Keep this app open. On the TV box, use a mouse to open Bluetooth settings, search for this phone, and pair. Arrow keys and Enter are the first compatibility test. Home, Back and media mappings vary by device.");root.addView(hint);message("Tap Enable Bluetooth remote to start.");
 }
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 private Button action(String label,Runnable fn){Button b=new Button(this);b.setText(label);b.setMinHeight(dp(48));b.setOnClickListener(v->{try{fn.run();}catch(RuntimeException e){message("Error: "+e.getMessage());}});return b;}
 private Button key(String label,int report,int usage){Button b=action(label,()->send(report,usage));b.setEnabled(false);controls.add(b);return b;}
 private void row(LinearLayout root,Button... buttons){LinearLayout row=new LinearLayout(this);for(Button b:buttons){View v=b==null?new View(this):b;row.addView(v,new LinearLayout.LayoutParams(0,dp(60),1));}root.addView(row);}
 private void message(String s){status.setText(s==null?"Unknown error":s);for(Button b:controls)b.setEnabled(host!=null&&registered);}
 private boolean permitted(){return Build.VERSION.SDK_INT<31||(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED);}
 private boolean ensure(){if(adapter==null){message("Bluetooth hardware unavailable.");return false;}if(!permitted()){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE},10);return false;}if(!adapter.isEnabled()){startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),11);return false;}return true;}
 @Override public void onRequestPermissionsResult(int code,String[] p,int[] g){super.onRequestPermissionsResult(code,p,g);if(code==10){if(permitted())startHid();else message("Nearby devices permission is required.");}}
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==11){if(result==RESULT_OK)startHid();else message("Bluetooth is disabled.");}}
 private void startHid(){if(!ensure())return;if(registered){message("Bluetooth remote registered. Make phone discoverable or choose a paired device.");refresh();return;}if(requesting)return;requesting=true;message("Requesting HID device profile…");if(hid!=null){register();return;}if(!adapter.getProfileProxy(this,listener,BluetoothProfile.HID_DEVICE)){requesting=false;message("This phone does not provide Bluetooth HID device support.");return;}handler.postDelayed(()->{if(requesting&&!registered){requesting=false;message("HID profile not ready. Tap Enable to retry.");}},10000);}
 private final BluetoothProfile.ServiceListener listener=new BluetoothProfile.ServiceListener(){
  @Override public void onServiceConnected(int profile,BluetoothProfile proxy){runOnUiThread(()->{if(isFinishing()||isDestroyed()){if(adapter!=null)adapter.closeProfileProxy(profile,proxy);return;}hid=(BluetoothHidDevice)proxy;register();});}
  @Override public void onServiceDisconnected(int profile){runOnUiThread(()->{hid=null;host=null;registered=false;requesting=false;message("Bluetooth HID profile disconnected.");});}
 };
 private void register(){try{BluetoothHidDeviceAppSdpSettings sdp=new BluetoothHidDeviceAppSdpSettings("LAN Remote","Bluetooth keyboard remote","LAN Remote",BluetoothHidDevice.SUBCLASS1_KEYBOARD,DESC);
  if(!hid.registerApp(sdp,null,null,getMainExecutor(),callback)){requesting=false;message("HID registration rejected. Another HID app or phone firmware may block it.");}}catch(RuntimeException e){requesting=false;message("HID registration failed: "+e.getMessage());}}
 private final BluetoothHidDevice.Callback callback=new BluetoothHidDevice.Callback(){
  @Override public void onAppStatusChanged(BluetoothDevice plugged,boolean ok){registered=ok;requesting=false;if(!ok)host=null;message(ok?"Remote registered. Make phone discoverable, then pair on the TV box.":"HID registration ended. Keep app open and enable again.");if(ok)refresh();}
  @Override public void onConnectionStateChanged(BluetoothDevice d,int state){if(state==BluetoothProfile.STATE_CONNECTED){host=d;message("Connected: "+name(d)+". Try arrow keys and OK.");}else if(state==BluetoothProfile.STATE_CONNECTING){message("Connecting to "+name(d)+"…");}else if(state==BluetoothProfile.STATE_DISCONNECTED){if(d.equals(host))host=null;message(host==null?"Disconnected from "+name(d)+". Pair or reconnect.":"Connected: "+name(host));}refresh();}
  @Override public void onGetReport(BluetoothDevice d,byte type,byte id,int size){if(hid==null)return;int n=id==1?8:id==2?2:0;if(n>0&&type==BluetoothHidDevice.REPORT_TYPE_INPUT)hid.replyReport(d,type,id,new byte[n]);else hid.reportError(d,BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID);}
 };
 private String name(BluetoothDevice d){try{String n=d.getName();return n==null?d.getAddress():n;}catch(SecurityException e){return "Bluetooth device";}}
 private void discoverable(){if(!ensure())return;if(!registered){message("Enable the Bluetooth remote first.");return;}Intent i=new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,300);startActivityForResult(i,12);}
 private void refresh(){if(adapter==null||!permitted())return;devices.clear();devices.addAll(adapter.getBondedDevices());devices.sort(Comparator.comparing(this::name));ArrayList<String> labels=new ArrayList<>();for(BluetoothDevice d:devices)labels.add(name(d)+" ("+d.getAddress()+")");if(labels.isEmpty())labels.add("No paired devices yet");ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);paired.setAdapter(a);}
 private void connectSelected(){if(!registered||hid==null){message("Enable the Bluetooth remote first.");return;}int i=paired.getSelectedItemPosition();if(i<0||i>=devices.size()){message("Pair your phone from the TV box first, then Refresh.");return;}BluetoothDevice d=devices.get(i);if(!hid.connect(d)){message("Connection request rejected by Bluetooth stack.");return;}message("Connecting to "+name(d)+"…");handler.postDelayed(()->{if(host==null&&registered)message("No Bluetooth HID connection yet. Try selecting phone from the TV box.");},12000);}
 private void send(int report,int usage){if(host==null||hid==null||!registered||sending)return;BluetoothDevice target=host;BluetoothHidDevice proxy=hid;byte[] data=report==1?new byte[8]:new byte[2];if(report==1)data[2]=(byte)usage;else{data[0]=(byte)usage;data[1]=(byte)(usage>>8);}if(!proxy.sendReport(target,report,data)){message("Key report rejected. Reconnect the box.");return;}sending=true;handler.postDelayed(()->{sending=false;try{proxy.sendReport(target,report,new byte[data.length]);}catch(RuntimeException e){message("Key release failed: "+e.getMessage());}},45);}
 @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(hid!=null&&adapter!=null&&permitted()){try{hid.unregisterApp();adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE,hid);}catch(RuntimeException ignored){}}super.onDestroy();}
}
