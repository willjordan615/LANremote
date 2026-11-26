package com.lanremote.bluetooth;
import java.util.*;
public class HidKeysTest {
 private static void eq(int a,int b){if(a!=b)throw new AssertionError(a+" != "+b);}
 public static void main(String[] args){
  byte[] right=HidKeys.report(3,2);eq(right.length,3);eq(right[0],2);eq(right[1],0);eq(right[2],0);
  eq(HidKeys.report(1,0x28)[2],0x28);
  List<byte[]> keys=HidKeys.text("aA0!?)\n");
  eq(keys.size(),7);eq(keys.get(0)[2],4);eq(keys.get(1)[0],2);eq(keys.get(2)[2],0x27);eq(keys.get(3)[2],0x1e);eq(keys.get(4)[2],0x38);eq(keys.get(5)[2],0x27);eq(keys.get(6)[2],0x28);
  for(char c=32;c<127;c++)eq(HidKeys.text(String.valueOf(c)).size(),1);
  try{HidKeys.text("é");throw new AssertionError("unsupported text accepted");}catch(IllegalArgumentException expected){}
  System.out.println("HID input mapping tests passed");
 }
}
