package com.lanremote.bluetooth;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.widget.Button;

/** Native button with a small, scalable line icon and normal accessibility semantics. */
final class RemoteButton extends Button {
 private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
 private String icon="";
 RemoteButton(Context context){super(context);}
 void icon(String kind,String description){icon=kind;setText("");setContentDescription(description);invalidate();}
 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);if(icon.isEmpty())return;
  float size=24*getResources().getDisplayMetrics().density;
  size=Math.min(size,Math.min(getWidth(),getHeight())*.65f);
  if(size<=0)return;
  canvas.save();canvas.translate((getWidth()-size)/2f,(getHeight()-size)/2f);canvas.scale(size/24f,size/24f);
  ink.setColor(getCurrentTextColor());ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.8f);ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeJoin(Paint.Join.ROUND);
  if("power".equals(icon)){canvas.drawArc(new RectF(3,4,21,22),-48,276,false,ink);canvas.drawLine(12,2,12,11,ink);}
  else if("keyboard".equals(icon)){
   canvas.drawRoundRect(new RectF(2,5,22,19),2,2,ink);
   for(int y=8;y<=12;y+=4)for(int x=5;x<=19;x+=4)canvas.drawLine(x,y,x+1,y,ink);
   canvas.drawLine(8,16,16,16,ink);
  }
  canvas.restore();
 }
}
