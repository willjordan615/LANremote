package com.lanremote.bluetooth;
import java.util.Locale;
enum TvPlatform {
 LG("LG webOS"), SAMSUNG("Samsung Tizen"), ROKU("Roku"), ANDROID("Android / Google TV"), NONE("Set up TV");
 final String label;TvPlatform(String label){this.label=label;}
 static TvPlatform saved(String value){try{return valueOf(value);}catch(Exception e){return NONE;}}
 static TvPlatform service(String value){String s=value.toLowerCase(Locale.ROOT);if(s.equals("urn:lge-com:service:webos-second-screen:1"))return LG;if(s.equals("urn:samsung.com:device:remotecontrolreceiver:1")||s.equals("urn:samsung.com:service:multiscreenservice:1"))return SAMSUNG;if(s.equals("roku:ecp"))return ROKU;return NONE;}
 static String samsungKey(String action){switch(action){
  case "BACK":case "EXIT":return "KEY_RETURN";case "UP":return "KEY_UP";case "DOWN":return "KEY_DOWN";case "LEFT":return "KEY_LEFT";case "RIGHT":return "KEY_RIGHT";case "ENTER":return "KEY_ENTER";case "HOME":return "KEY_HOME";case "INPUT":return "KEY_SOURCE";case "MENU":return "KEY_MENU";case "volumeDown":return "KEY_VOLDOWN";case "volumeUp":return "KEY_VOLUP";case "MUTE":return "KEY_MUTE";case "REWIND":return "KEY_REWIND";case "FASTFORWARD":return "KEY_FF";case "PLAY":return "KEY_PLAY";case "PAUSE":return "KEY_PAUSE";case "powerOff":return "KEY_POWEROFF";case "HDMI1":return "KEY_HDMI1";case "HDMI2":return "KEY_HDMI2";case "HDMI3":return "KEY_HDMI3";default:return "";
 }}
}
