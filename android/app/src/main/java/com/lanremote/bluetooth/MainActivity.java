package com.lanremote.bluetooth;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
 private BluetoothAdapter adapter;
 private RemoteService service;
 private boolean bound,started;
 private final ServiceConnection serviceConnection=new ServiceConnection(){
  @Override public void onServiceConnected(ComponentName n,IBinder b){service=((RemoteService.LocalBinder)b).getService();service.observe(()->{message(service.status());refresh();});service.resumeRemote();}
  @Override public void onServiceDisconnected(ComponentName n){service=null;message("Bluetooth service disconnected. Tap Enable.");}
 };
 private TextView status;
 private Spinner paired;
 private final ArrayList<BluetoothDevice> devices=new ArrayList<>();
 private final ArrayList<Button> controls=new ArrayList<>();
 @Override public void onCreate(Bundle saved){super.onCreate(saved);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  BluetoothManager manager=getSystemService(BluetoothManager.class);adapter=manager==null?null:manager.getAdapter();
  ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(18),dp(16),dp(18));scroll.addView(root);setContentView(scroll);
  TextView title=new TextView(this);title.setText("LAN Remote • Bluetooth 0.3.1");title.setTextSize(24);root.addView(title);
  status=new TextView(this);status.setTextSize(16);status.setPadding(0,dp(10),0,dp(10));root.addView(status);
  root.addView(action("1. Enable Bluetooth remote",()->startHid()));
  root.addView(action("2. Make phone discoverable",()->discoverable()));
  root.addView(action("3. Refresh paired devices",()->refresh()));
  paired=new Spinner(this);root.addView(paired);root.addView(action("Connect selected device",()->connectSelected()));
  root.addView(action("Stop Bluetooth remote",()->{if(service!=null)service.shutdown();detach();started=false;message("Remote stopped. Tap Enable to start.");}));
  row(root,null,key("▲",1,0x52),null);row(root,key("◀",1,0x50),key("OK",1,0x28),key("▶",1,0x4F));row(root,null,key("▼",1,0x51),null);
  row(root,key("Back / Esc",1,0x29),key("Home",2,0x223),key("Menu",2,0x40));
  row(root,key("Vol −",2,0xEA),key("Mute",2,0xE2),key("Vol +",2,0xE9));
  row(root,key("Previous",2,0xB6),key("Play / Pause",2,0xCD),key("Next",2,0xB5));
  TextView hint=new TextView(this);hint.setText("The remote can stay active while you switch apps. Use Stop Bluetooth remote to disconnect. On the TV box, use a mouse to open Bluetooth settings, search for this phone, and pair. Arrow keys and Enter are the first compatibility test. Home, Back and media mappings vary by device.");root.addView(hint);message("Tap Enable Bluetooth remote to start.");
 }
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 private Button action(String label,Runnable fn){Button b=new Button(this);b.setText(label);b.setMinHeight(dp(48));b.setOnClickListener(v->{try{fn.run();}catch(RuntimeException e){message("Error: "+e.getMessage());}});return b;}
 private Button key(String label,int report,int usage){Button b=action(label,()->send(report,usage));b.setEnabled(false);controls.add(b);return b;}
 private void row(LinearLayout root,Button... buttons){LinearLayout row=new LinearLayout(this);for(Button b:buttons){View v=b==null?new View(this):b;row.addView(v,new LinearLayout.LayoutParams(0,dp(60),1));}root.addView(row);}
 private void message(String s){status.setText(s==null?"Unknown error":s);for(Button b:controls)b.setEnabled(service!=null&&service.ready());}
 private boolean permitted(){return Build.VERSION.SDK_INT<31||(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED);}
 private boolean ensure(){if(adapter==null){message("Bluetooth hardware unavailable.");return false;}if(!permitted()){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE},10);return false;}if(!adapter.isEnabled()){startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),11);return false;}return true;}
 @Override public void onRequestPermissionsResult(int code,String[] p,int[] g){super.onRequestPermissionsResult(code,p,g);if(code==10){if(permitted())startHid();else message("Nearby devices permission is required.");}}
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==11){if(result==RESULT_OK)startHid();else message("Bluetooth is disabled.");}}
 private void startHid(){if(!ensure())return;started=true;Intent i=new Intent(this,RemoteService.class);startForegroundService(i);if(!bound)bound=bindService(i,serviceConnection,Context.BIND_AUTO_CREATE);if(service!=null)service.resumeRemote();if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},13);}
 @Override protected void onStart(){super.onStart();if(!bound)bound=bindService(new Intent(this,RemoteService.class),serviceConnection,0);}
 @Override protected void onResume(){super.onResume();if(service!=null)service.resumeRemote();}
 private void detach(){if(service!=null)service.observe(null);if(bound){unbindService(serviceConnection);bound=false;}service=null;}
 @Override protected void onStop(){detach();super.onStop();}
 private String name(BluetoothDevice d){try{String n=d.getName();return n==null?d.getAddress():n;}catch(SecurityException e){return "Bluetooth device";}}
 private void discoverable(){if(!ensure())return;if(service==null||!service.registered()){message("Enable the Bluetooth remote first.");return;}Intent i=new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,300);startActivityForResult(i,12);}
 private void refresh(){if(adapter==null||!permitted())return;String previous=paired.getSelectedItemPosition()>=0&&paired.getSelectedItemPosition()<devices.size()?devices.get(paired.getSelectedItemPosition()).getAddress():service==null?"":service.lastAddress();devices.clear();devices.addAll(adapter.getBondedDevices());devices.sort(Comparator.comparing(this::name));ArrayList<String> labels=new ArrayList<>();for(BluetoothDevice d:devices)labels.add(name(d)+" ("+d.getAddress()+")");if(labels.isEmpty())labels.add("No paired devices yet");ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);paired.setAdapter(a);for(int j=0;j<devices.size();j++)if(previous.equals(devices.get(j).getAddress())){paired.setSelection(j);break;}}
 private void connectSelected(){if(service==null||!service.registered()){message("Enable the Bluetooth remote first.");return;}int i=paired.getSelectedItemPosition();if(i<0||i>=devices.size()){message("Pair your phone from the TV box first, then Refresh.");return;}service.connect(devices.get(i));}
 private void send(int report,int usage){if(service!=null)service.send(report,usage);}
}
