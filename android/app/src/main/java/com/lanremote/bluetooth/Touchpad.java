package com.lanremote.bluetooth;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.*;
import android.widget.*;

// Collapsible, fixed-center virtual mouse thumbstick.
final class Touchpad extends LinearLayout {
 interface Motion {void move(int x,int y);}
 private final Button toggle;
 private final Stick stick;
 private final TextView caption;
 Touchpad(Context c,Motion motion){
  super(c);setOrientation(VERTICAL);
  toggle=new RemoteButton(c);toggle.setAllCaps(false);toggle.setText("Mouse cursor ▾");addView(toggle,new LayoutParams(-1,RemoteTheme.dp(c,48)));
  stick=new Stick(c,motion);stick.setVisibility(GONE);stick.setEnabled(false);
  addView(stick,new LayoutParams(-1,RemoteTheme.dp(c,166)));
  caption=new TextView(c);caption.setTextSize(12);caption.setGravity(Gravity.CENTER);caption.setTextColor(RemoteTheme.MUTED);caption.setText("Connect your device to move the cursor");caption.setVisibility(GONE);addView(caption);
  toggle.setOnClickListener(v->{boolean open=stick.getVisibility()!=VISIBLE;stick.stop();stick.setVisibility(open?VISIBLE:GONE);caption.setVisibility(open?VISIBLE:GONE);toggle.setText(open?"Mouse cursor ▴":"Mouse cursor ▾");toggle.setSelected(open);});
 }
 void available(boolean ready,String unavailable){String label=ready?"Hold to move • Release to stop":unavailable;if(stick.isEnabled()!=ready){stick.stop();stick.setEnabled(ready);stick.invalidate();}if(!label.contentEquals(caption.getText()))caption.setText(label);}
 void cancelMotion(){stick.stop();}
 private static final class Stick extends View {
  private final Motion motion;
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private float x,y;
  private int finger=-1;
  private boolean ticking;
  private final Runnable tick=new Runnable(){public void run(){ticking=false;if(!usable()) {stop();return;}int dx=CursorMath.step(x,y,true),dy=CursorMath.step(x,y,false);if(dx!=0||dy!=0){motion.move(dx,dy);schedule(40);}}};
  Stick(Context c,Motion motion){super(c);this.motion=motion;setContentDescription("Mouse cursor thumbstick. Drag and hold to move; release to stop.");setFocusable(true);}
  private float radius(){return Math.min(getWidth(),getHeight())*0.43f;}
  private boolean usable(){return finger>=0&&isEnabled()&&isShown()&&hasWindowFocus();}
  private void schedule(int delay){if(!ticking){ticking=true;postDelayed(tick,delay);}}
  void stop(){finger=-1;x=y=0;removeCallbacks(tick);ticking=false;invalidate();if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);}
  private void position(float px,float py){float travel=Math.max(1,radius()*0.64f);float nx=(px-getWidth()/2f)/travel,ny=(py-getHeight()/2f)/travel;double r=Math.hypot(nx,ny);float scale=(float)Math.max(1,r);x=nx/scale;y=ny/scale;invalidate();if(usable()&&Math.hypot(x,y)>CursorMath.DEAD)schedule(0);else{removeCallbacks(tick);ticking=false;}}
  @Override protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f,r=radius();if(r<=0)return;paint.setStyle(Paint.Style.FILL);paint.setColor(RemoteTheme.MUTED_SURFACE);c.drawCircle(cx,cy,r,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(RemoteTheme.dp(getContext(),1));paint.setColor(RemoteTheme.BORDER);c.drawCircle(cx,cy,r,paint);paint.setColor(RemoteTheme.DISABLED);float p=r*.79f,mark=r*.07f;c.drawLine(cx-mark,cy-p,cx+mark,cy-p,paint);c.drawLine(cx-mark,cy+p,cx+mark,cy+p,paint);c.drawLine(cx-p,cy-mark,cx-p,cy+mark,paint);c.drawLine(cx+p,cy-mark,cx+p,cy+mark,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(!isEnabled()?RemoteTheme.SURFACE:finger>=0?RemoteTheme.ACTIVE:0xff777777);c.drawCircle(cx+x*r*.64f,cy+y*r*.64f,r*.32f,paint);}
  @Override public boolean onTouchEvent(MotionEvent e){if(!isEnabled())return false;switch(e.getActionMasked()){
   case MotionEvent.ACTION_DOWN:if(Math.hypot(e.getX()-getWidth()/2f,e.getY()-getHeight()/2f)>radius())return false;finger=e.getPointerId(0);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);RemoteButton.feedback(this);position(e.getX(),e.getY());return true;
   case MotionEvent.ACTION_MOVE:int index=e.findPointerIndex(finger);if(index<0)stop();else position(e.getX(index),e.getY(index));return true;
   case MotionEvent.ACTION_POINTER_DOWN:case MotionEvent.ACTION_POINTER_UP:case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:stop();return true;
   default:return true;
  }}
  @Override public void onWindowFocusChanged(boolean focus){if(!focus)stop();super.onWindowFocusChanged(focus);}
  @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
 }
}
