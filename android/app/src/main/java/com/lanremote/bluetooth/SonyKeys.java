package com.lanremote.bluetooth;
import java.util.Map;
final class SonyKeys {
 static String command(String action,Map<String,String> advertised){
  if(action==null)return "";
  String[] names;switch(action){
   case "BACK":case "EXIT":names=new String[]{"Back","Return"};break;
   case "UP":names=new String[]{"Up"};break;case "DOWN":names=new String[]{"Down"};break;case "LEFT":names=new String[]{"Left"};break;case "RIGHT":names=new String[]{"Right"};break;
   case "ENTER":names=new String[]{"Confirm","Enter"};break;case "HOME":names=new String[]{"Home"};break;
   case "MENU":names=new String[]{"Options","ActionMenu","Menu"};break;case "INPUT":names=new String[]{"Input"};break;
   case "volumeUp":names=new String[]{"VolumeUp"};break;case "volumeDown":names=new String[]{"VolumeDown"};break;case "MUTE":names=new String[]{"Mute"};break;
   case "PLAY":names=new String[]{"Play"};break;case "PAUSE":names=new String[]{"Pause"};break;
   case "REWIND":names=new String[]{"Rewind","FlashMinus","Prev"};break;case "FASTFORWARD":names=new String[]{"Forward","FlashPlus","Next"};break;
   case "powerOff":names=new String[]{"PowerOff"};break;case "HDMI1":names=new String[]{"Hdmi1","HDMI1"};break;case "HDMI2":names=new String[]{"Hdmi2","HDMI2"};break;case "HDMI3":names=new String[]{"Hdmi3","HDMI3"};break;
   default:return "";
  }
  for(String name:names){String value=advertised.get(name);if(valid(value))return value;}return "";
 }
 static boolean valid(String code){return code!=null&&code.matches("[A-Za-z0-9+/]{4,80}={0,2}")&&code.length()%4==0;}
 static String envelope(String code){if(!valid(code))throw new IllegalArgumentException("Invalid remote code");return "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:X_SendIRCC xmlns:u=\"urn:schemas-sony-com:service:IRCC:1\"><IRCCCode>"+code+"</IRCCCode></u:X_SendIRCC></s:Body></s:Envelope>";}
}
