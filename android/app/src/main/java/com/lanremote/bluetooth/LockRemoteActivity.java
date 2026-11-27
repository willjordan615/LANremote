package com.lanremote.bluetooth;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class LockRemoteActivity extends Activity {
 private RemoteService service;private RelayGuest guest;private boolean bound,tvMode,active;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final Map<Button,Boolean> controls=new LinkedHashMap<>();
 private final Map<Button,String[]> labels=new LinkedHashMap<>();
 private final ArrayList<Button> tvControls=new ArrayList<>();
 private TextView status,tvStatus;private Button boxMode,tvModeButton,connectTv,boxPowerButton;
 private LgRemote lg;
 private Touchpad touchpad;
 static boolean enabled(Context c){return c.getSharedPreferences("remote",MODE_PRIVATE).getBoolean("lock_remote",false);}
 static void publish(Context c){NotificationManager nm=c.getSystemService(NotificationManager.class);if(!enabled(c)){nm.cancel(3);return;}nm.createNotificationChannel(new NotificationChannel("lock_remote","Lock-screen remote",NotificationManager.IMPORTANCE_LOW));PendingIntent open=PendingIntent.getActivity(c,3,new Intent(c,LockRemoteActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);try{nm.notify(3,new Notification.Builder(c,"lock_remote").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("LANremote controls").setContentText("Open remote session (may require unlock)").setContentIntent(open).setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true).build());}catch(RuntimeException ignored){}}
 private final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){service=((RemoteService.LocalBinder)b).getService();refresh();}public void onServiceDisconnected(ComponentName n){service=null;refresh();}};
 private final Runnable tick=new Runnable(){public void run(){if(!active)return;if(!enabled(LockRemoteActivity.this)){finish();return;}refresh();main.postDelayed(this,1000);}};
 @Override public void onCreate(Bundle state){super.onCreate(state);if(!enabled(this)){finish();return;}setShowWhenLocked(true);
  ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(12),dp(12),dp(12));scroll.addView(root);setContentView(scroll);
  TextView hint=new TextView(this);hint.setText("Lock your phone while this session is open. Close to leave the session. Android may still require unlocking.");hint.setTextSize(13);RemoteTheme.secondary(hint);root.addView(hint);
  boxMode=action(DeviceLabels.box(this),()->select(false));tvModeButton=action("Set up TV",()->select(true));for(Button b:new Button[]{boxMode,tvModeButton}){b.setSingleLine(true);b.setEllipsize(android.text.TextUtils.TruncateAt.END);}row(root,boxMode,tvModeButton);
  status=new TextView(this);status.setTextSize(13);root.addView(status);tvStatus=new TextView(this);tvStatus.setTextSize(13);RemoteTheme.secondary(tvStatus);root.addView(tvStatus);
  row(root,dual("Back",1,0x29,"BACK",true),dual("▲",1,0x52,"UP",true),dual("Home",2,0x223,"HOME",true));
  row(root,dual("◀",1,0x50,"LEFT",true),dual("OK",1,0x28,"ENTER",true),dual("▶",1,0x4f,"RIGHT",true));
  row(root,null,dual("▼",1,0x51,"DOWN",true),dual("Menu",2,0x40,"INPUT",true,"Inputs"));
  gap(root);touchpad=new Touchpad(this,this::movePointer);root.addView(touchpad);row(root,dual("Left click",3,1,"click",true,"Click"),dual("Right click",3,2,"EXIT",true,"Exit"));
  gap(root);row(root,dual("Vol −",2,0xea,"volumeDown",false),dual("Mute",2,0xe2,"MUTE",true),dual("Vol +",2,0xe9,"volumeUp",false));
  row(root,dual("Previous",2,0xb6,"REWIND",true,"Rewind"),dual("Play / Pause",2,0xcd,"playPause",true),dual("Next",2,0xb5,"FASTFORWARD",true,"Fast fwd"));
  gap(root);row(root,tvAction("HDMI 1",()->lg.quickHdmi(1)),tvAction("HDMI 2",()->lg.quickHdmi(2)),tvAction("HDMI 3",()->lg.quickHdmi(3)));
  Button on=action("TV on",()->lg.sessionWake());tvControls.add(on);
  Button off=tvAction("TV off",()->lg.sessionOff());
  Button boxPower=action("Box power",()->{if(guest!=null&&guest.ready())guest.submit(RelayWire.key(2,0x30));else if(service!=null&&service.ready())service.send(2,0x30);});boxPowerButton=boxPower;row(root,boxPower,on,off);
  connectTv=action("Reconnect saved TV",()->lg.connect());row(root,connectTv);
  row(root,action("Close remote session",()->finish()));
  // The existing TV transport is reused, but its configuration panel is never displayed here.
  lg=new LgRemote(this,root,true);lg.setMirrorStatus(tvStatus);lg.setObserver(this::refresh);
  select(state==null?getIntent().getBooleanExtra("tv_mode",false):state.getBoolean("tv_mode"));refresh();
 }
 @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putBoolean("tv_mode",tvMode);}
 private int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
 private void gap(LinearLayout root){View v=new View(this);root.addView(v,new LinearLayout.LayoutParams(-1,dp(8)));}
 private Button action(String label,Runnable r){Button b=new RemoteButton(this);b.setText(label);b.setAllCaps(false);b.setOnClickListener(v->{if(!enabled(this)){finish();return;}r.run();});return b;}
 private Button dual(String label,int report,int usage,String command,boolean pointer){return dual(label,report,usage,command,pointer,label);}
 private Button dual(String label,int report,int usage,String command,boolean pointer,String tvLabel){Button b=action(label,()->{if(tvMode){if(lg!=null&&lg.ready(pointer))lg.remoteAction(command);}else if(guest!=null&&guest.ready())guest.submit(RelayWire.key(report,usage));else if(service!=null&&service.ready())service.send(report,usage);});controls.put(b,pointer);labels.put(b,new String[]{label,tvLabel});if(RemoteButton.repeatable(report,usage))((RemoteButton)b).repeat(()->{if(tvMode){if(lg!=null&&lg.ready(pointer))lg.remoteAction(command);}else if(guest!=null&&guest.ready())guest.repeatKey(report,usage);else if(service!=null)service.repeatKey(report,usage);},()->active&&enabled(this)&&b.isEnabled());return b;}
 private Button tvAction(String name,Runnable run){Button b=action(name,()->{if(lg!=null&&lg.ready(false))run.run();});tvControls.add(b);return b;}
 private void row(LinearLayout root,Button... buttons){LinearLayout row=new LinearLayout(this);for(Button b:buttons)row.addView(b==null?new View(this):b,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(row);}
 private void select(boolean tv){if(touchpad!=null)touchpad.cancelMotion();tvMode=tv;boxMode.setSelected(!tv);tvModeButton.setSelected(tv);for(Map.Entry<Button,String[]> item:labels.entrySet())item.getKey().setText(item.getValue()[tv?1:0]);if(tv&&active&&lg!=null&&!lg.ready(false))lg.connect();refresh();}
 private void movePointer(int x,int y){if(!enabled(this)||(!tvMode&&!getSharedPreferences("remote",MODE_PRIVATE).getBoolean("box_cursor_trial",true)))return;if(tvMode){if(lg!=null)lg.movePointer(x,y);}else if(guest!=null&&guest.ready())guest.movePointer(x,y);else if(service!=null)service.movePointer(x,y);}
 private void refresh(){if(status==null||lg==null)return;String boxName=DeviceLabels.box(this),tvName=lg.deviceLabel();if(!boxName.contentEquals(boxMode.getText()))boxMode.setText(boxName);if(!tvName.contentEquals(tvModeButton.getText()))tvModeButton.setText(tvName);boolean box=guest!=null?guest.ready():service!=null&&service.ready();status.setText(box?"Box remote connected":"Box not connected • unlock for setup");for(Map.Entry<Button,Boolean> item:controls.entrySet())item.getKey().setEnabled(enabled(this)&&(tvMode?lg.ready(item.getValue()):box));for(Button b:tvControls)b.setEnabled(enabled(this)&&("TV on".contentEquals(b.getText())?lg.sessionCanWake():lg.ready(false)));connectTv.setEnabled(enabled(this)&&lg.sessionConfigured());boxPowerButton.setEnabled(enabled(this)&&box);if(touchpad!=null)touchpad.available(enabled(this)&&(tvMode?lg.ready(true):box&&getSharedPreferences("remote",MODE_PRIVATE).getBoolean("box_cursor_trial",true)),!tvMode&&!getSharedPreferences("remote",MODE_PRIVATE).getBoolean("box_cursor_trial",true)?"Bluetooth cursor is off; click buttons remain active.":"Connect your device to move the cursor");}
 @Override protected void onStart(){super.onStart();if(!enabled(this)){finish();return;}active=true;String invite=getSharedPreferences("remote",MODE_PRIVATE).getString("relayInvite","");if(!invite.isEmpty())try{guest=new RelayGuest(this,main,RelayWire.Invite.parse(invite),(r,s)->refresh());}catch(RuntimeException ignored){}else bound=bindService(new Intent(this,RemoteService.class),connection,0);if(lg!=null&&lg.sessionConfigured())lg.connect();main.post(tick);refresh();}
 @Override protected void onStop(){if(touchpad!=null)touchpad.cancelMotion();active=false;main.removeCallbacks(tick);if(guest!=null){guest.close();guest=null;}service=null;if(bound){unbindService(connection);bound=false;}super.onStop();}
 @Override protected void onDestroy(){if(lg!=null)lg.destroy();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
