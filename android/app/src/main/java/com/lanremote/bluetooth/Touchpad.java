package com.lanremote.bluetooth;

import android.content.Context;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;

final class Touchpad extends LinearLayout {
 interface Motion {void move(int x,int y);}
 private final TextView pad;
 private final Button toggle;
 private final Motion motion;
 private final Runnable click;
 private float lastX,lastY,downX,downY,dx,dy;
 private long lastSent,downTime;
 private boolean tracking,moved;
 private final float density,slop;
 Touchpad(Context c,Motion motion,Runnable click){super(c);this.motion=motion;this.click=click;density=getResources().getDisplayMetrics().density;slop=ViewConfiguration.get(c).getScaledTouchSlop();setOrientation(VERTICAL);
  toggle=new RemoteButton(c);toggle.setAllCaps(false);toggle.setText("Touchpad ▾");addView(toggle,new LayoutParams(-1,RemoteTheme.dp(c,48)));
  pad=new TextView(c);pad.setText("Drag to move • Tap to click");pad.setTextColor(RemoteTheme.MUTED);pad.setGravity(Gravity.CENTER);pad.setBackground(RemoteTheme.shape(c,RemoteTheme.MUTED_SURFACE,12,RemoteTheme.BORDER));pad.setContentDescription("Mouse touchpad");pad.setVisibility(GONE);pad.setEnabled(false);pad.setText("Connect your device to move the cursor");LayoutParams lp=new LayoutParams(-1,RemoteTheme.dp(c,156));lp.setMargins(RemoteTheme.dp(c,3),RemoteTheme.dp(c,2),RemoteTheme.dp(c,3),RemoteTheme.dp(c,2));addView(pad,lp);
  toggle.setOnClickListener(v->{boolean open=pad.getVisibility()!=VISIBLE;reset();pad.setVisibility(open?VISIBLE:GONE);toggle.setText(open?"Touchpad ▴":"Touchpad ▾");toggle.setSelected(open);});
  pad.setOnTouchListener((v,e)->touch(e));pad.setOnClickListener(v->{if(pad.isEnabled()){RemoteButton.feedback(pad);click.run();}});
 }
 void available(boolean ready,String unavailable){String label=ready?"Drag to move • Tap to click":unavailable;if(pad.isEnabled()==ready&&label.contentEquals(pad.getText()))return;pad.setEnabled(ready);pad.setText(label);if(!ready)reset();}
 private void reset(){tracking=false;moved=false;dx=dy=0;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);}
 private void flush(){int x=Math.max(-127,Math.min(127,Math.round(dx))),y=Math.max(-127,Math.min(127,Math.round(dy)));dx=dy=0;if(x!=0||y!=0){motion.move(x,y);lastSent=SystemClock.uptimeMillis();}}
 private boolean touch(MotionEvent e){
  if(!pad.isEnabled())return false;
  switch(e.getActionMasked()){
   case MotionEvent.ACTION_DOWN: tracking=true;moved=false;downX=lastX=e.getX();downY=lastY=e.getY();dx=dy=0;downTime=SystemClock.uptimeMillis();lastSent=0;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);return true;
   case MotionEvent.ACTION_POINTER_DOWN:reset();return true;
   case MotionEvent.ACTION_MOVE:if(!tracking||e.getPointerCount()!=1)return true;float x=e.getX(),y=e.getY();if(!moved&&Math.hypot(x-downX,y-downY)>slop)moved=true;if(moved){dx+=(x-lastX)/density*1.6f;dy+=(y-lastY)/density*1.6f;if(SystemClock.uptimeMillis()-lastSent>=40)flush();}lastX=x;lastY=y;return true;
   case MotionEvent.ACTION_UP:if(tracking){if(moved)flush();else if(SystemClock.uptimeMillis()-downTime<350)pad.performClick();}reset();return true;
   case MotionEvent.ACTION_CANCEL:reset();return true;
   default:return true;
  }
 }
}
