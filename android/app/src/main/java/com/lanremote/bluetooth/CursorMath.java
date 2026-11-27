package com.lanremote.bluetooth;

final class CursorMath {
 static final float DEAD=0.14f;
 static final int MAX=26;
 // Radial dead zone, curved speed and normalized diagonal movement.
 static int step(float x,float y,boolean horizontal){
  double radius=Math.hypot(x,y);
  if(!Double.isFinite(radius)||radius<=DEAD)return 0;
  double t=(Math.min(1,radius)-DEAD)/(1-DEAD);
  double speed=1+ (MAX-1)*t*(0.30+0.70*t);
  return (int)Math.round(speed*(horizontal?x:y)/radius);
 }
}
