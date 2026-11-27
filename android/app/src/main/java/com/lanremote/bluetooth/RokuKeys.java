package com.lanremote.bluetooth;

final class RokuKeys {
 static String key(String action,boolean tv,boolean volume,boolean power){
  if(action==null)return "";
  switch(action){
   case "HOME":return "Home";case "BACK":case "EXIT":return "Back";case "UP":return "Up";case "DOWN":return "Down";case "LEFT":return "Left";case "RIGHT":return "Right";case "ENTER":return "Select";case "MENU":return "Info";case "REWIND":return "Rev";case "FASTFORWARD":return "Fwd";case "playPause":return "Play";
   case "volumeDown":return volume?"VolumeDown":"";case "volumeUp":return volume?"VolumeUp":"";case "MUTE":return volume?"VolumeMute":"";case "powerOff":return power?"PowerOff":"";
   case "HDMI1":return tv?"InputHDMI1":"";case "HDMI2":return tv?"InputHDMI2":"";case "HDMI3":return tv?"InputHDMI3":"";
   default:return "";
  }
 }
 static boolean allowedStatus(int status){return status>=200&&status<300;}
 static boolean denied(int status){return status==401||status==403;}
}
