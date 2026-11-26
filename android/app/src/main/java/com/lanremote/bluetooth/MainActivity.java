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
 private RelayGuest guest;
 private RelayDiscovery.Search relaySearch;
 private RelayPairGuest pairingGuest;
 private AlertDialog pairingDialog,approvalDialog;
 private RelayPairHost.Request showingApproval;
 private String relayInvite="";
 private final Handler relayMain=new Handler(Looper.getMainLooper());
 private boolean bound,started;
 private final ServiceConnection serviceConnection=new ServiceConnection(){
  @Override public void onServiceConnected(ComponentName n,IBinder b){service=((RemoteService.LocalBinder)b).getService();if(!relayInvite.isEmpty()){service.shutdown();detach();return;}service.observe(()->{message(service.status());refresh();showApproval();});service.resumeRemote();}
  @Override public void onServiceDisconnected(ComponentName n){service=null;message("Bluetooth service disconnected. Tap Enable.");}
 };
 private TextView status;
 private AppUpdates updates;
 private LgRemote lg;
 private LinearLayout bluetoothPanel;
 private boolean tvMode;
 private Button boxModeButton,tvModeButton;
 private final Map<Button,Boolean> pointerControls=new HashMap<>();
 private final Map<Button,String[]> modeLabels=new HashMap<>();
 private final Map<Button,String[]> modeDescriptions=new HashMap<>();
 private Spinner paired;
 private final ArrayList<BluetoothDevice> devices=new ArrayList<>();
 private final ArrayList<Button> controls=new ArrayList<>();
 @Override public void onCreate(Bundle saved){super.onCreate(saved);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  relayInvite=getSharedPreferences("remote",MODE_PRIVATE).getString("relayInvite","");
  BluetoothManager manager=getSystemService(BluetoothManager.class);adapter=manager==null?null:manager.getAdapter();
  ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(8),dp(12),dp(8));root.setBackgroundColor(RemoteTheme.BG);scroll.setFillViewport(true);scroll.addView(root);setContentView(scroll);
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header);
  TextView title=new TextView(this);title.setText("LAN Remote • 0.5.2");title.setTextSize(20);title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));header.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));title.setGravity(Gravity.CENTER_VERTICAL);
  Button power=action("TV power",()->lg.powerDialog());((RemoteButton)power).icon("power","TV power on or off");header.addView(power,new LinearLayout.LayoutParams(dp(52),dp(48)));
  boxModeButton=action("vSeeBox",()->selectMode(false));tvModeButton=action("LG TV",()->selectMode(true));row(root,boxModeButton,tvModeButton);
  status=new TextView(this);status.setTextSize(13);RemoteTheme.secondary(status);status.setPadding(0,dp(4),0,dp(4));root.addView(status);
  TextView tvStatus=new TextView(this);tvStatus.setTextSize(12);RemoteTheme.secondary(tvStatus);root.addView(tvStatus);
  gap(root,8);
  row(root,dual("Back",1,0x29,"BACK",true),dual("▲",1,0x52,"UP",true),dual("Home",2,0x223,"HOME",true));
  row(root,dual("◀",1,0x50,"LEFT",true),dual("OK",1,0x28,"ENTER",true),dual("▶",1,0x4F,"RIGHT",true));
  Button settings=action("Settings",()->{selectMode(true);lg.quickSettings();});
  ((RemoteButton)settings).icon("settings","LG quick settings; hold for picture settings");
  settings.setOnLongClickListener(v->{selectMode(true);lg.pictureModes();return true;});
  Button menu=dual("Menu",2,0x40,"INPUT",true);modeLabels.put(menu,new String[]{"Menu","Inputs"});
  row(root,settings,dual("▼",1,0x51,"DOWN",true),menu);
  gap(root,10);
  mouseRow(root);
  gap(root,10);
  row(root,dual("Vol −",2,0xEA,"volumeDown",false),dual("Mute",2,0xE2,"MUTE",true),dual("Vol +",2,0xE9,"volumeUp",false));
  Button prev=dual("Previous",2,0xB6,"REWIND",true),play=dual("Play / Pause",2,0xCD,"playPause",true),next=dual("Next",2,0xB5,"FASTFORWARD",true);
  modeLabels.put(prev,new String[]{"Previous","Rewind"});modeLabels.put(next,new String[]{"Next","Fast fwd"});row(root,prev,play,next);
  gap(root,10);
  row(root,action("HDMI 1",()->lg.quickHdmi(1)),action("HDMI 2",()->lg.quickHdmi(2)),action("HDMI 3",()->lg.quickHdmi(3)));
  gap(root,8);
  bluetoothPanel=new LinearLayout(this);bluetoothPanel.setOrientation(LinearLayout.VERTICAL);root.addView(bluetoothPanel);
  LinearLayout setup=new LinearLayout(this);setup.setOrientation(LinearLayout.VERTICAL);setup.setVisibility(View.GONE);RemoteTheme.setup(setup);
  Button setupToggle=action("Bluetooth setup ▾",()->{});setupToggle.setOnClickListener(v->{boolean show=setup.getVisibility()!=View.VISIBLE;setup.setVisibility(show?View.VISIBLE:View.GONE);setupToggle.setText(show?"Bluetooth setup ▴":"Bluetooth setup ▾");});
  bluetoothPanel.addView(setupToggle,new LinearLayout.LayoutParams(-1,dp(48)));bluetoothPanel.addView(setup);
  row(setup,action("Enable",()->startHid()),action("Discoverable",()->discoverable()));
  paired=new Spinner(this);setup.addView(paired,new LinearLayout.LayoutParams(-1,dp(48)));
  row(setup,action("Refresh",()->refresh()),action("Select / connect",()->connectSelected()),action("Stop",()->{if(service!=null)service.shutdown();detach();started=false;message("Remote stopped. Open setup to enable.");}));
  row(setup,action("Nickname",()->renameSelected()),action("Forget target",()->forgetSelected()));
  gap(setup,8);
  row(setup,action("Share over Wi-Fi",()->shareRelay()),action("Stop sharing",()->{if(service!=null)service.stopRelay();}));
  row(setup,action("Find / join host",()->findRelay()),action("Use direct Bluetooth",()->directMode()));
  TextView hint=new TextView(this);hint.setTextSize(13);RemoteTheme.secondary(hint);hint.setText("Pair from the box, then select it here. Only your selected device receives commands. Likely TV/computer hosts appear first; other paired devices remain available.");setup.addView(hint);
  lg=new LgRemote(this,root);lg.setMirrorStatus(tvStatus);lg.setObserver(()->refreshControls());
  selectMode(false);
  TextView updateStatus=new TextView(this);updateStatus.setTextSize(12);RemoteTheme.secondary(updateStatus);root.addView(updateStatus);
  updates=new AppUpdates(this,updateStatus);row(root,action("Check / install update",()->updates.check(true)));
  message("Open Bluetooth setup to connect.");refresh();root.postDelayed(()->updates.check(false),1200);
 }
 private void selectMode(boolean tv){tvMode=tv;bluetoothPanel.setVisibility(tv?View.GONE:View.VISIBLE);if(lg!=null)lg.visible(tv);
  boxModeButton.setSelected(!tv);tvModeButton.setSelected(tv);
  for(Map.Entry<Button,String[]> e:modeLabels.entrySet())e.getKey().setText(e.getValue()[tv?1:0]);
  for(Map.Entry<Button,String[]> e:modeDescriptions.entrySet())e.getKey().setContentDescription(e.getValue()[tv?1:0]);
  refreshControls();
 }
 private void gap(LinearLayout parent,int height){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(-1,dp(height)));}
 private void refreshControls(){boolean bt=!relayInvite.isEmpty()?guest!=null&&guest.ready():service!=null&&service.ready();for(Button b:controls){boolean pointer=Boolean.TRUE.equals(pointerControls.get(b));b.setEnabled(tvMode?lg!=null&&lg.ready(pointer):bt);}}
 private Button dualAction(String label,Runnable bluetooth,Runnable tv,boolean pointer){Button b=action(label,()->{if(tvMode)tv.run();else bluetooth.run();});controls.add(b);pointerControls.put(b,pointer);b.setEnabled(false);return b;}
 private Button dual(String label,int report,int usage,String tv,boolean pointer){return dualAction(label,()->send(report,usage),()->lg.remoteAction(tv),pointer);}

 @Override protected void onDestroy(){if(lg!=null)lg.destroy();super.onDestroy();}
 private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 private Button action(String label,Runnable fn){Button b=new RemoteButton(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(4),0,dp(4),0);b.setMinHeight(dp(48));b.setOnClickListener(v->{try{fn.run();}catch(RuntimeException e){message("Error: "+e.getMessage());}});return b;}
 private android.graphics.drawable.GradientDrawable mouseShape(int color,float[] corners){
  android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(color);shape.setCornerRadii(corners);return shape;
 }
 private void mouseRow(LinearLayout parent){
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
  LinearLayout split=new LinearLayout(this);split.setGravity(Gravity.CENTER_VERTICAL);
  Button left=dual("Left",3,1,"click",true),right=dual("Right",3,2,"EXIT",true);
  modeLabels.put(left,new String[]{"Left","Click"});modeLabels.put(right,new String[]{"Right","Exit"});
  modeDescriptions.put(left,new String[]{"Left mouse click","Click TV pointer"});modeDescriptions.put(right,new String[]{"Right mouse click","Exit TV menu"});
  float r=dp(12);
  float[] all={r,r,r,r,r,r,r,r},lc={r,r,0,0,0,0,r,r},rc={0,0,r,r,r,r,0,0};
  android.graphics.drawable.GradientDrawable shell=mouseShape(RemoteTheme.SURFACE,all);shell.setStroke(dp(1),RemoteTheme.BORDER);split.setBackground(shell);
  android.content.res.ColorStateList ripple=android.content.res.ColorStateList.valueOf(0x40888888);
  left.setBackground(new android.graphics.drawable.RippleDrawable(ripple,null,mouseShape(0xffffffff,lc)));left.setBackgroundTintList(null);
  right.setBackground(new android.graphics.drawable.RippleDrawable(ripple,null,mouseShape(0xffffffff,rc)));right.setBackgroundTintList(null);
  split.addView(left,new LinearLayout.LayoutParams(0,dp(48),1));
  View seam=new View(this);seam.setBackgroundColor(RemoteTheme.BORDER);split.addView(seam,new LinearLayout.LayoutParams(dp(1),dp(28)));
  split.addView(right,new LinearLayout.LayoutParams(0,dp(48),1));
  LinearLayout.LayoutParams group=new LinearLayout.LayoutParams(0,dp(48),2);group.setMargins(dp(3),dp(3),dp(3),dp(3));group.height=dp(48);group.topMargin=0;group.bottomMargin=0;row.addView(split,group);
  Button keyboard=dualAction("Keyboard",()->keyboard(),()->lg.keyboard(),false);((RemoteButton)keyboard).icon("keyboard","Open keyboard");row.addView(keyboard,new LinearLayout.LayoutParams(0,dp(48),1));parent.addView(row);
 }
 private void keyboard(){if(!boxReady())return;EditText entry=new EditText(this);entry.setHint("Type or paste text");entry.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);entry.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
  AlertDialog d=new AlertDialog.Builder(this).setTitle("vSeeBox keyboard").setMessage("Focus a text field on the box first. US keyboard layout. Send types the composed text; OK submits it separately.").setView(entry).setPositiveButton("Send",null).setNeutralButton("Keys",null).setNegativeButton("Cancel",null).create();
  d.setOnShowListener(x->{d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{String[] labels={"Backspace","Space","Tab"};int[] codes={0x2a,0x2c,0x2b};new AlertDialog.Builder(this).setTitle("Send special key").setItems(labels,(dialog,i)->send(1,codes[i])).show();});d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{sendBoxText(entry.getText().toString());d.dismiss();}catch(RuntimeException e){entry.setError(e.getMessage());}});entry.requestFocus();d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);});d.show();}

 private void row(LinearLayout root,Button... buttons){LinearLayout row=new LinearLayout(this);for(Button b:buttons){View v=b==null?new View(this):b;row.addView(v,new LinearLayout.LayoutParams(0,dp(48),1));}root.addView(row);}
 private void message(String s){status.setText(s==null?"Unknown error":s);refreshControls();}
 private boolean permitted(){return Build.VERSION.SDK_INT<31||(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED);}
 private boolean ensure(){if(adapter==null){message("Bluetooth hardware unavailable.");return false;}if(!permitted()){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE},10);return false;}if(!adapter.isEnabled()){startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),11);return false;}return true;}
 @Override public void onRequestPermissionsResult(int code,String[] p,int[] g){super.onRequestPermissionsResult(code,p,g);if(code==10){if(permitted())startHid();else message("Nearby devices permission is required.");}}
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==11){if(result==RESULT_OK)startHid();else message("Bluetooth is disabled.");}}
 private void startHid(){if(!relayInvite.isEmpty()){message("Select Use direct Bluetooth to leave Wi-Fi relay mode.");return;}if(!ensure())return;started=true;Intent i=new Intent(this,RemoteService.class);startForegroundService(i);if(!bound)bound=bindService(i,serviceConnection,Context.BIND_AUTO_CREATE);if(service!=null)service.resumeRemote();if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},13);}
 @Override protected void onStart(){super.onStart();if(!relayInvite.isEmpty()){startGuest();return;}if(!bound)bound=bindService(new Intent(this,RemoteService.class),serviceConnection,0);}
 @Override protected void onResume(){super.onResume();if(relayInvite.isEmpty()&&service!=null){service.resumeRemote();showApproval();}}
 private void detach(){if(service!=null)service.observe(null);if(bound){unbindService(serviceConnection);bound=false;}service=null;refreshControls();}
 @Override protected void onStop(){cancelPairGuest();dismissApproval();stopRelaySearch();stopGuest();detach();super.onStop();}
 private String name(BluetoothDevice d){try{String n=d.getName();return n==null?d.getAddress():n;}catch(SecurityException e){return "Bluetooth device";}}
 private void discoverable(){if(!ensure())return;if(service==null||!service.registered()){message("Enable the Bluetooth remote first.");return;}Intent i=new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,300);startActivityForResult(i,12);}
 private String savedTarget(){if(service!=null)return service.lastAddress();android.content.SharedPreferences p=getSharedPreferences("remote",MODE_PRIVATE);return HostIdentity.initial(p.getString("selectedHost",null),p.getString("lastHost",""));}
 private int rank(BluetoothDevice d){BluetoothClass c=d.getBluetoothClass();if(c==null)return 1;int m=c.getMajorDeviceClass();if(m==BluetoothClass.Device.Major.COMPUTER||m==BluetoothClass.Device.Major.AUDIO_VIDEO)return 0;if(m==BluetoothClass.Device.Major.PERIPHERAL||m==BluetoothClass.Device.Major.WEARABLE)return 2;return 1;}
 private String deviceClass(BluetoothDevice d){BluetoothClass c=d.getBluetoothClass();if(c==null)return "Unknown class";switch(c.getMajorDeviceClass()){case BluetoothClass.Device.Major.COMPUTER:return "Computer / box";case BluetoothClass.Device.Major.AUDIO_VIDEO:return "Audio / video";case BluetoothClass.Device.Major.PHONE:return "Phone";case BluetoothClass.Device.Major.PERIPHERAL:return "Peripheral";case BluetoothClass.Device.Major.WEARABLE:return "Wearable";default:return "Other";}}
 private String deviceLabel(BluetoothDevice d){String nickname=getSharedPreferences("remote",MODE_PRIVATE).getString("nickname_"+d.getAddress(),"");String label=name(d);return (nickname.isEmpty()?label:nickname+" ("+label+")")+" • "+deviceClass(d)+" • "+d.getAddress()+(HostIdentity.matches(savedTarget(),d.getAddress())?" • selected":"");}
 private BluetoothDevice selectedDevice(){int i=paired.getSelectedItemPosition()-1;return i>=0&&i<devices.size()?devices.get(i):null;}
 private void refresh(){if(adapter==null||!permitted()||paired==null)return;BluetoothDevice prior=selectedDevice();String previous=prior==null?savedTarget():prior.getAddress();devices.clear();devices.addAll(adapter.getBondedDevices());devices.sort(Comparator.comparingInt(this::rank).thenComparing(this::name).thenComparing(BluetoothDevice::getAddress));ArrayList<String> labels=new ArrayList<>();labels.add("Choose a paired device…");for(BluetoothDevice d:devices)labels.add(deviceLabel(d));ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);paired.setAdapter(a);for(int j=0;j<devices.size();j++)if(HostIdentity.matches(previous,devices.get(j).getAddress())){paired.setSelection(j+1);break;}}
 private void connectSelected(){if(service==null||!service.registered()){message("Enable Bluetooth remote first.");return;}BluetoothDevice d=selectedDevice();if(d==null){message("Pair your phone from the TV box, then choose it here.");return;}if(HostIdentity.matches(savedTarget(),d.getAddress())){service.connect(d);return;}new AlertDialog.Builder(this).setTitle("Select Bluetooth remote target?").setMessage(deviceLabel(d)+"\nOnly this device will receive Bluetooth remote commands.").setPositiveButton("Select",(dialog,w)->{if(service!=null)service.connect(d);}).setNegativeButton("Cancel",null).show();}
 private void renameSelected(){BluetoothDevice d=selectedDevice();if(d==null){message("Choose a paired device first.");return;}EditText entry=new EditText(this);entry.setSingleLine();entry.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});entry.setText(getSharedPreferences("remote",MODE_PRIVATE).getString("nickname_"+d.getAddress(),""));entry.setHint("Living room, bedroom…");new AlertDialog.Builder(this).setTitle("Device nickname").setView(entry).setPositiveButton("Save",(dialog,w)->{String value=entry.getText().toString().trim();getSharedPreferences("remote",MODE_PRIVATE).edit().putString("nickname_"+d.getAddress(),value).apply();if(service!=null)service.nickname(d,value);refresh();}).setNegativeButton("Cancel",null).show();}
 private void forgetSelected(){new AlertDialog.Builder(this).setTitle("Forget remote target?").setMessage("This stops sending commands to the selected device, without removing the Android Bluetooth pairing.").setPositiveButton("Forget",(dialog,w)->{if(service!=null)service.forgetTarget();else getSharedPreferences("remote",MODE_PRIVATE).edit().putString("selectedHost","").remove("lastHost").apply();paired.setSelection(0);refresh();message("No target selected.");}).setNegativeButton("Cancel",null).show();}
 private boolean boxReady(){return !relayInvite.isEmpty()?guest!=null&&guest.ready():service!=null&&service.ready();}
 private void shareRelay(){if(service==null||!service.ready()){message("Connect directly to the box before sharing.");return;}try{String invitation=service.startRelay();new AlertDialog.Builder(this).setTitle("Wi-Fi remote invitation").setMessage("Other phones can find this host and request approval. Keep this private invitation as a manual fallback. Stop sharing to revoke access.\n\n"+invitation).setPositiveButton("Copy invitation",(dialog,w)->{android.content.ClipboardManager clipboard=getSystemService(android.content.ClipboardManager.class);ClipData clip=ClipData.newPlainText("LANremote invitation",invitation);if(Build.VERSION.SDK_INT>=33){PersistableBundle extras=new PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(extras);}clipboard.setPrimaryClip(clip);}).setNegativeButton("Close",null).show();}catch(Exception e){message("Cannot share: "+e.getMessage());}}
 private void stopRelaySearch(){if(relaySearch!=null){relaySearch.close();relaySearch=null;}}
 private void findRelay(){stopRelaySearch();message("Finding Wi-Fi remote hosts…");try{
  relaySearch=new RelayDiscovery.Search(this,relayMain,(hosts,error)->{relaySearch=null;if(isFinishing()||isDestroyed())return;
   if(hosts.isEmpty()){message(error==null?"No hosts found. Use an invitation or enable sharing on the host.":error);joinRelay(null);return;}
   String[] names=new String[hosts.size()];for(int k=0;k<names.length;k++){RelayDiscovery.Found h=hosts.get(k);names[k]=h.name+" • "+h.ip;}
   message("Found "+hosts.size()+" Wi-Fi remote host(s).");new AlertDialog.Builder(this).setTitle("Choose Wi-Fi remote host").setItems(names,(dialog,k)->joinRelay(hosts.get(k))).setNeutralButton("Paste invitation",(dialog,w)->joinRelay(null)).setNegativeButton("Cancel",null).show();
  });
 }catch(Exception e){message("Wi-Fi discovery unavailable: "+e.getMessage());joinRelay(null);}}
 private void joinRelay(RelayDiscovery.Found found){
  if(found!=null&&!relayInvite.isEmpty())try{RelayWire.Invite existing=RelayWire.Invite.parse(relayInvite);if(RelayWire.identity(existing.key).equals(found.id)){new AlertDialog.Builder(this).setTitle("Reconnect to saved host?").setMessage(found.name+" • "+found.ip).setPositiveButton("Connect",(dialog,w)->useInvitation(new RelayWire.Invite(found.ip,existing.key))).setNegativeButton("Cancel",null).show();return;}}catch(RuntimeException ignored){}
  if(found!=null){requestPairing(found);return;}
  EditText field=new EditText(this);field.setSingleLine(true);field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);field.setHint("Paste host invitation");
  new AlertDialog.Builder(this).setTitle(found==null?"Join Wi-Fi remote":found.name).setMessage("Paste the private invitation from the host to authorize this phone. Discovery alone cannot grant control. Joining stops this phone's direct Bluetooth connection.").setView(field).setPositiveButton("Join",(dialog,w)->{try{RelayWire.Invite invite=RelayWire.Invite.parse(field.getText().toString());if(found!=null){if(!RelayWire.identity(invite.key).equals(found.id)){message("Invitation does not match the selected host.");return;}invite=new RelayWire.Invite(found.ip,invite.key);}useInvitation(invite);}catch(RuntimeException e){message("Invalid Wi-Fi invitation.");}}).setNegativeButton("Cancel",null).show();
 }
 private void dismissApproval(){if(approvalDialog!=null){approvalDialog.dismiss();approvalDialog=null;}showingApproval=null;}
 private void showApproval(){RelayPairHost.Request pending=service==null?null:service.pendingPairing();if(pending==showingApproval)return;dismissApproval();if(pending==null||isFinishing()||isDestroyed())return;showingApproval=pending;
  approvalDialog=new AlertDialog.Builder(this).setTitle("Approve Wi-Fi remote?").setMessage("Guest phone: "+pending.address+"\n\nVerification code: "+pending.code+"\n\nCompare with the code on the guest phone. Approve only if they match and you want to grant remote control.").setPositiveButton("Approve",(dialog,w)->{if(service!=null)service.decidePairing(pending,true);}).setNegativeButton("Deny",(dialog,w)->{if(service!=null)service.decidePairing(pending,false);}).setOnCancelListener(dialog->{if(service!=null)service.decidePairing(pending,false);}).create();approvalDialog.show();
 }
 private void cancelPairGuest(){if(pairingGuest!=null){pairingGuest.close();pairingGuest=null;}if(pairingDialog!=null){pairingDialog.dismiss();pairingDialog=null;}}
 private void requestPairing(RelayDiscovery.Found found){cancelPairGuest();
  pairingDialog=new AlertDialog.Builder(this).setTitle("Requesting access").setMessage("Contacting "+found.name+"…").setNegativeButton("Cancel",(dialog,w)->cancelPairGuest()).setOnCancelListener(dialog->cancelPairGuest()).create();pairingDialog.show();
  pairingGuest=new RelayPairGuest(getApplicationContext(),relayMain,found,new RelayPairGuest.Observer(){
   public void compare(String code){if(pairingGuest==null||isFinishing()||isDestroyed())return;if(pairingDialog!=null)pairingDialog.dismiss();pairingDialog=new AlertDialog.Builder(MainActivity.this).setTitle("Compare verification code").setMessage(code+"\n\nCheck that the host phone shows the same code. Confirm here and approve on the host.").setPositiveButton("Codes match",(dialog,w)->{if(pairingGuest!=null){pairingGuest.approve();pairingDialog=new AlertDialog.Builder(MainActivity.this).setTitle("Waiting for host").setMessage("Approve the matching request on the host phone.").setNegativeButton("Cancel",(d,i)->cancelPairGuest()).setOnCancelListener(d->cancelPairGuest()).create();pairingDialog.show();}}).setNegativeButton("Cancel",(dialog,w)->cancelPairGuest()).setOnCancelListener(dialog->cancelPairGuest()).create();pairingDialog.show();}
   public void finished(RelayWire.Invite invite,String error){cancelPairGuest();if(isFinishing()||isDestroyed())return;if(invite!=null)useInvitation(invite);else new AlertDialog.Builder(MainActivity.this).setTitle("Could not join host").setMessage(error).setPositiveButton("Try again",(dialog,w)->requestPairing(found)).setNeutralButton("Paste invitation",(dialog,w)->joinRelay(null)).setNegativeButton("Cancel",null).show();}
  });
 }
 private void useInvitation(RelayWire.Invite invite){relayInvite=invite.encode();getSharedPreferences("remote",MODE_PRIVATE).edit().putString("relayInvite",relayInvite).apply();if(service!=null)service.shutdown();else stopService(new Intent(this,RemoteService.class));detach();startGuest();}
 private void startGuest(){stopGuest();try{guest=new RelayGuest(this,relayMain,RelayWire.Invite.parse(relayInvite),(ready,info)->message(info));message("Connecting to Wi-Fi relay…");}catch(RuntimeException e){message("Invalid invitation. Join again or use direct Bluetooth.");}}
 private void stopGuest(){if(guest!=null){guest.close();guest=null;}}
 private void directMode(){stopGuest();relayInvite="";getSharedPreferences("remote",MODE_PRIVATE).edit().remove("relayInvite").apply();message("Direct Bluetooth selected.");startHid();}
 private void sendBoxText(String text){if(!boxReady())throw new IllegalStateException("Connect the box first.");if(!relayInvite.isEmpty())guest.submit(RelayWire.text(text));else service.sendText(text);}
 private void send(int report,int usage){if(!relayInvite.isEmpty()){if(guest!=null&&guest.ready())guest.submit(RelayWire.key(report,usage));}else if(service!=null)service.send(report,usage);}

}
