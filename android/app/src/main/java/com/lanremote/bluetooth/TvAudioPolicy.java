package com.lanremote.bluetooth;

// The television owns HDMI-CEC forwarding; never send a second command as a fallback.
final class TvAudioPolicy {
 static String lgKey(String action,boolean remoteKeys){
  if("MUTE".equals(action))return "MUTE";
  if(!remoteKeys)return "";
  if("volumeUp".equals(action))return "VOLUMEUP";
  if("volumeDown".equals(action))return "VOLUMEDOWN";
  return "";
 }
 static boolean lgNeedsPointer(String action,boolean remoteKeys){return !lgKey(action,remoteKeys).isEmpty();}
}
