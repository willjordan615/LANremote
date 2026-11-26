package com.lanremote.bluetooth;

import java.util.Locale;

final class WakeAddress {
 static String normalize(String value){
  if(value==null)return "";
  String s=value.trim();
  if(!s.matches("(?i)([0-9a-f]{12}|[0-9a-f]{2}(:[0-9a-f]{2}){5}|[0-9a-f]{2}(-[0-9a-f]{2}){5})"))return "";
  s=s.replace(":","").replace("-","").toUpperCase(Locale.ROOT);
  if(s.equals("000000000000")||(Integer.parseInt(s.substring(0,2),16)&1)!=0)return "";
  StringBuilder b=new StringBuilder();for(int i=0;i<12;i+=2){if(i>0)b.append(':');b.append(s,i,i+2);}return b.toString();
 }
 static String choose(String target,String wiredMac,String wifiMac,String wiredIp,String wifiIp,String wiredState,String wifiState){
  String w=normalize(wiredMac),f=normalize(wifiMac);
  boolean wm=target.equals(wiredIp)&&!"disconnected".equalsIgnoreCase(wiredState),fm=target.equals(wifiIp)&&!"disconnected".equalsIgnoreCase(wifiState);
  if(wm||fm){if(wm&&fm)return w.equals(f)?w:"";return wm?w:f;}
  if(!wiredIp.isEmpty()&&!wifiIp.isEmpty())return "";
  if("connected".equalsIgnoreCase(wiredState)&&!"connected".equalsIgnoreCase(wifiState)&&wiredIp.isEmpty())return w;
  if("connected".equalsIgnoreCase(wifiState)&&!"connected".equalsIgnoreCase(wiredState)&&wifiIp.isEmpty())return f;
  if(!w.isEmpty()&&w.equals(f))return w;
  return "";
 }
}
