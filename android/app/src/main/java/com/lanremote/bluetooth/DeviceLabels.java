package com.lanremote.bluetooth;

import android.content.Context;
import android.content.SharedPreferences;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

final class DeviceLabels {
 static String clean(String s){if(s==null)return "";s=s.replaceAll("[\\p{Cntrl}]","").trim();return s.length()>64?s.substring(0,64):s;}
 static String first(String... names){for(String n:names){String s=clean(n);if(!s.isEmpty())return s;}return "";}
 static String box(Context c){SharedPreferences p=c.getSharedPreferences("remote",Context.MODE_PRIVATE);String invite=p.getString("relayInvite","");
  if(!invite.isEmpty())try{RelayWire.Invite i=RelayWire.Invite.parse(invite);return first(p.getString("relay_name_"+i.hostId,""),"Wi-Fi remote");}catch(RuntimeException ignored){return "Wi-Fi remote";}
  String target=HostIdentity.initial(p.getString("selectedHost",null),p.getString("lastHost",""));if(target.isEmpty())return "Set up Bluetooth";
  return first(p.getString("nickname_"+target,""),p.getString("device_name_"+target,""),"Bluetooth device");
 }
 static void learnBluetooth(Context c,BluetoothDevice d){if(d==null)return;try{String name=clean(d.getName());if(!name.isEmpty())c.getSharedPreferences("remote",Context.MODE_PRIVATE).edit().putString("device_name_"+d.getAddress(),name).apply();}catch(RuntimeException ignored){}}
 static void learnSavedBluetooth(Context c,BluetoothAdapter adapter,String target){if(adapter==null||target.isEmpty())return;try{for(BluetoothDevice d:adapter.getBondedDevices())if(HostIdentity.matches(target,d.getAddress())){learnBluetooth(c,d);break;}}catch(RuntimeException ignored){}}
 static void learnRelay(Context c,RelayWire.Invite invite,RelayDiscovery.Found found){if(found==null||!invite.hostId.equals(found.id))return;String n=clean(found.name);if(n.startsWith("LANremote - "))n=n.substring(12).trim();if(!n.isEmpty())c.getSharedPreferences("remote",Context.MODE_PRIVATE).edit().putString("relay_name_"+invite.hostId,n).apply();}
}
