package com.lanremote.bluetooth;
public class CursorMathTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  check(CursorMath.step(0,0,true)==0);check(CursorMath.step(.1f,.05f,true)==0);
  check(CursorMath.step(1,0,true)==26);check(CursorMath.step(-1,0,true)==-26);
  check(CursorMath.step(0,1,false)==26);check(CursorMath.step(0,1,true)==0);
  check(CursorMath.step(.3f,0,true)>0&&CursorMath.step(.3f,0,true)<CursorMath.step(.8f,0,true));
  int d=CursorMath.step(1,1,true);check(d==CursorMath.step(1,1,false)&&Math.hypot(d,d)<=27);
  check(CursorMath.step(100,0,true)==26);check(CursorMath.step(Float.NaN,1,true)==0);
  System.out.println("Cursor thumbstick dead zone, speed and direction tests passed");
 }
}
