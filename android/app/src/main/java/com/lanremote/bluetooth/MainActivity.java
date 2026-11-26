package com.lanremote.bluetooth;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
 private AppUpdates updates;
 private LgRemote lg;
 private LinearLayout bluetoothPanel;
 private Spinner paired;
 private final ArrayList<BluetoothDevice> devices=new ArrayList<>();
 private final ArrayList<Button> controls=new ArrayList<>();
 @Override public void onCreate(Bundle saved){super.onCreate(saved);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  BluetoothManager manager=getSystemService(BluetoothManager.class);adapter=manager==null?null:manager.getAdapter();
  ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(10),dp(6),dp(10),dp(6));scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
  TextView title=new TextView(this);title.setText("LAN Remote • 0.4.3");title.setTextSize(20);root.addView(title);
  row(root,action("vSeeBox",()->selectMode(false)),action("LG TV",()->selectMode(true)));
  LinearLayout outer=root;bluetoothPanel=new LinearLayout(this);bluetoothPanel.setOrientation(LinearLayout.VERTICAL);outer.addView(bluetoothPanel);root=bluetoothPanel;
  status=new TextView(this);status.setTextSize(13);status.setPadding(0,dp(4),0,dp(4));root.addView(status);
  LinearLayout setup=new LinearLayout(this);setup.setOrientation(LinearLayout.VERTICAL);setup.setVisibility(View.GONE);
  Button setupToggle=action("Bluetooth setup ▾",()->{});setupToggle.setOnClickListener(v->{boolean show=setup.getVisibility()!=View.VISIBLE;setup.setVisibility(show?View.VISIBLE:View.GONE);setupToggle.setText(show?"Bluetooth setup ▴":"Bluetooth setup ▾");});
  row(setup,action("Enable",()->startHid()),action("Discoverable",()->discoverable()));
  paired=new Spinner(this);setup.addView(paired,new LinearLayout.LayoutParams(-1,dp(48)));
  row(setup,action("Refresh",()->refresh()),action("Connect",()->connectSelected()),action("Stop",()->{if(service!=null)service.shutdown();detach();started=false;message("Remote stopped. Open setup to enable.");}));
  TextView hint=new TextView(this);hint.setTextSize(13);hint.setText("Pair from the box using a mouse. If mouse buttons fail after updating, forget the phone on the box and pair again.");setup.addView(hint);
  row(root,key("Back",1,0x29),key("▲",1,0x52),key("Home",2,0x223));
  row(root,key("◀",1,0x50),key("OK",1,0x28),key("▶",1,0x4F));
  row(root,key("Right click",3,2),key("▼",1,0x51),key("Menu",2,0x40));
  row(root,key("Left click",3,1),connectedAction("Keyboard",()->keyboard()),action("TV Power",()->lg.powerDialog()));
  
  row(root,key("Vol −",2,0xEA),key("Mute",2,0xE2),key("Vol +",2,0xE9));
  row(root,key("Previous",2,0xB6),key("Play / Pause",2,0xCD),key("Next",2,0xB5));
  row(root,action("HDMI 1",()->lg.quickHdmi(1)),action("HDMI 2",()->lg.quickHdmi(2)),action("HDMI 3",()->lg.quickHdmi(3)));
  TextView tvStatus=new TextView(this);tvStatus.setTextSize(12);root.addView(tvStatus);
  root.addView(setupToggle,new LinearLayout.LayoutParams(-1,dp(48)));root.addView(setup);
  lg=new LgRemote(this,outer);lg.setMirrorStatus(tvStatus);root=outer;
  TextView updateStatus=new TextView(this);updateStatus.setTextSize(12);root.addView(updateStatus);
  updates=new AppUpdates(this,updateStatus);row(root,action("Check / install update",()->updates.check(true)));
  message("Open Bluetooth setup to connect.");root.postDelayed(()->updates.check(false),1200);
 }
 private void selectMode(boolean tv){bluetoothPanel.setVisibility(tv?View.GONE:View.VISIBLE);if(lg!=null)lg.visible(tv);}
 @Override protected void onDestroy(){if(lg!=null)lg.destroy();super.onDestroy();}
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 private Button action(String label,Runnable fn){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(4),0,dp(4),0);b.setMinHeight(dp(48));b.setOnClickListener(v->{try{fn.run();}catch(RuntimeException e){message("Error: "+e.getMessage());}});return b;}
 private Button connectedAction(String label,Runnable r){Button b=action(label,r);b.setEnabled(false);controls.add(b);return b;}
 private Button key(String label,int report,int usage){return connectedAction(label,()->send(report,usage));}
 private void keyboard(){if(service==null||!service.ready())return;EditText entry=new EditText(this);entry.setHint("Type or paste text");entry.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);entry.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
  AlertDialog d=new AlertDialog.Builder(this).setTitle("vSeeBox keyboard").setMessage("Focus a text field on the box first. US keyboard layout. Send types the composed text; OK submits it separately.").setView(entry).setPositiveButton("Send",null).setNeutralButton("Keys",null).setNegativeButton("Cancel",null).create();
  d.setOnShowListener(x->{d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{String[] labels={"Backspace","Space","Tab"};int[] codes={0x2a,0x2c,0x2b};new AlertDialog.Builder(this).setTitle("Send special key").setItems(labels,(dialog,i)->send(1,codes[i])).show();});d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{if(service==null)throw new IllegalStateException("Connect the box first.");service.sendText(entry.getText().toString());d.dismiss();}catch(RuntimeException e){entry.setError(e.getMessage());}});entry.requestFocus();d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);});d.show();}

 private void row(LinearLayout root,Button... buttons){LinearLayout row=new LinearLayout(this);for(Button b:buttons){View v=b==null?new View(this):b;row.addView(v,new LinearLayout.LayoutParams(0,dp(48),1));}root.addView(row);}
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
