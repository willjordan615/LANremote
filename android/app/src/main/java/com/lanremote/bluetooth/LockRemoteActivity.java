package com.lanremote.bluetooth;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

public final class LockRemoteActivity extends Activity {
 private RemoteService service;private RelayGuest guest;private boolean bound;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final java.util.ArrayList<Button> buttons=new java.util.ArrayList<>();
 private TextView status;
 static boolean enabled(Context c){return c.getSharedPreferences("remote",MODE_PRIVATE).getBoolean("lock_remote",false);}
 static void publish(Context c){NotificationManager nm=c.getSystemService(NotificationManager.class);if(!enabled(c)){nm.cancel(3);return;}nm.createNotificationChannel(new NotificationChannel("lock_remote","Lock-screen remote",NotificationManager.IMPORTANCE_LOW));PendingIntent open=PendingIntent.getActivity(c,3,new Intent(c,LockRemoteActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);try{nm.notify(3,new Notification.Builder(c,"lock_remote").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("LANremote controls").setContentText("Tap to open limited remote controls").setContentIntent(open).setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true).build());}catch(RuntimeException ignored){}}
 private final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){service=((RemoteService.LocalBinder)b).getService();service.observe(()->refresh());}public void onServiceDisconnected(ComponentName n){service=null;refresh();}};
 @Override public void onCreate(Bundle state){super.onCreate(state);if(!enabled(this)){finish();return;}setShowWhenLocked(true); // Do not dismiss the keyguard or wake the screen.
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(16,16,16,16);root.setGravity(Gravity.CENTER);setContentView(root);
  TextView title=new TextView(this);title.setText("LANremote");title.setTextSize(24);root.addView(title);status=new TextView(this);root.addView(status);
  row(root,new String[]{"Back","▲","Home"},new int[]{1,1,2},new int[]{0x29,0x52,0x223});
  row(root,new String[]{"◀","OK","▶"},new int[]{1,1,1},new int[]{0x50,0x28,0x4f});
  row(root,new String[]{"","▼",""},new int[]{0,1,0},new int[]{0,0x51,0});
  row(root,new String[]{"Vol −","Mute","Vol +"},new int[]{2,2,2},new int[]{0xea,0xe2,0xe9});
  row(root,new String[]{"Previous","Play / Pause","Next"},new int[]{2,2,2},new int[]{0xb6,0xcd,0xb5});
  Button close=new RemoteButton(this);close.setText("Close");close.setOnClickListener(v->finish());root.addView(close,new LinearLayout.LayoutParams(-1,dp(48)));refresh();
 }
 private int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
 private void row(LinearLayout root,String[] names,int[] reports,int[] uses){LinearLayout row=new LinearLayout(this);for(int i=0;i<names.length;i++){final int r=reports[i],u=uses[i];View v;if(r==0)v=new View(this);else{Button b=new RemoteButton(this);b.setText(names[i]);b.setAllCaps(false);b.setOnClickListener(x->{if(!enabled(this)){finish();return;}if(guest!=null&&guest.ready())guest.submit(RelayWire.key(r,u));else if(service!=null&&service.ready())service.send(r,u);});buttons.add(b);v=b;}row.addView(v,new LinearLayout.LayoutParams(0,dp(56),1));}root.addView(row,new LinearLayout.LayoutParams(-1,-2));}
 private void refresh(){if(status==null)return;boolean ready=enabled(this)&&(guest!=null?guest.ready():service!=null&&service.ready());status.setText(ready?"Remote connected":"Not connected • unlock to set up");for(Button b:buttons)b.setEnabled(ready);}
 @Override protected void onStart(){super.onStart();if(!enabled(this)){finish();return;}String invite=getSharedPreferences("remote",MODE_PRIVATE).getString("relayInvite","");if(!invite.isEmpty())try{guest=new RelayGuest(this,main,RelayWire.Invite.parse(invite),(r,s)->refresh());}catch(RuntimeException ignored){}else bound=bindService(new Intent(this,RemoteService.class),connection,0);refresh();}
 @Override protected void onStop(){if(guest!=null){guest.close();guest=null;}if(service!=null){service.observe(null);service=null;}if(bound){unbindService(connection);bound=false;}super.onStop();}
}
