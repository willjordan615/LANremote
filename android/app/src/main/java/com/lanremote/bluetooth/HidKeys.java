package com.lanremote.bluetooth;

import java.util.ArrayList;
import java.util.List;

final class HidKeys {
 static byte[] keyboard(int usage,int modifier){byte[] b=new byte[8];b[0]=(byte)modifier;b[2]=(byte)usage;return b;}
 static byte[] report(int id,int usage){
  if(id==1)return keyboard(usage,0);
  if(id==2)return new byte[]{(byte)usage,(byte)(usage>>8)};
  if(id==3)return new byte[]{(byte)(usage&7),0,0};
  throw new IllegalArgumentException("Unknown HID report");
 }
 // Standard US keyboard layout. Reject unsupported characters before sending anything.
 static List<byte[]> text(String text){
  if(text.length()>500)throw new IllegalArgumentException("Send up to 500 characters at a time.");
  ArrayList<byte[]> out=new ArrayList<>();
  String plain="-=[]\\;'"+(char)96+",./";
  String shifted="_+{}|:\"~<>?";
  int[] codes={0x2d,0x2e,0x2f,0x30,0x31,0x33,0x34,0x35,0x36,0x37,0x38};
  for(int i=0;i<text.length();i++){
   char c=text.charAt(i);int key=0,mod=0;
   if(c>='a'&&c<='z')key=4+c-'a';
   else if(c>='A'&&c<='Z'){key=4+c-'A';mod=2;}
   else if(c>='1'&&c<='9')key=0x1e+c-'1';
   else if(c=='0')key=0x27;
   else if(c==' ')key=0x2c;
   else if(c=='\n')key=0x28;
   else if(c=='\t')key=0x2b;
   else if("!@#$%^&*()".indexOf(c)>=0){key=0x1e+"!@#$%^&*()".indexOf(c);mod=2;}
   else if(plain.indexOf(c)>=0)key=codes[plain.indexOf(c)];
   else if(shifted.indexOf(c)>=0){key=codes[shifted.indexOf(c)];mod=2;}
   else throw new IllegalArgumentException("Bluetooth text supports US keyboard characters only; unsupported character at position "+(i+1)+".");
   out.add(keyboard(key,mod));
  }
  return out;
 }
}
