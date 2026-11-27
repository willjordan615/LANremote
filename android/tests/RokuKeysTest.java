package com.lanremote.bluetooth;
public final class RokuKeysTest {
 static void check(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] args){
  check(RokuKeys.key("ENTER",false,false,false).equals("Select"));
  check(RokuKeys.key("playPause",false,false,false).equals("Play"));
  check(RokuKeys.key("MENU",false,false,false).equals("Info"));
  check(RokuKeys.key("HDMI1",false,true,true).isEmpty());
  check(RokuKeys.key("HDMI3",true,true,true).equals("InputHDMI3"));
  check(RokuKeys.key("MUTE",false,false,false).isEmpty());
  check(RokuKeys.key("volumeUp",false,true,false).equals("VolumeUp"));
  check(RokuKeys.key("powerOff",false,false,true).equals("PowerOff"));
  check(RokuKeys.key("click",true,true,true).isEmpty());
  check(RokuKeys.denied(403)&&RokuKeys.denied(401)&&!RokuKeys.denied(404));
  check(RokuKeys.allowedStatus(200)&&!RokuKeys.allowedStatus(403));
  System.out.println("Roku control mapping and access-denial tests passed");
 }
}
