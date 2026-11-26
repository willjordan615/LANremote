package com.lanremote.bluetooth;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

final class RelayPermissions {
 private final SharedPreferences prefs;
 RelayPermissions(Context context,String target){prefs=context.getSharedPreferences("relay_permissions_"+target.replace(":",""),Context.MODE_PRIVATE);}
 private static String encode(byte[] k){return Base64.getUrlEncoder().withoutPadding().encodeToString(k);}
 private static byte[] decode(String s){try{byte[] b=Base64.getUrlDecoder().decode(s);return b.length==32?b:null;}catch(Exception e){return null;}}
 synchronized byte[] master(){byte[] key=decode(prefs.getString("master",""));if(key==null){key=RelayWire.random(32);if(!prefs.edit().putString("master",encode(key)).commit())throw new IllegalStateException("Unable to save sharing identity.");}return key;}
 synchronized byte[] lookup(String id){byte[] m=master();if(RelayWire.identity(m).equals(id))return m;return decode(prefs.getString("key_"+id,""));}
 synchronized byte[] issue(String name){if(guests().size()>=16)throw new IllegalStateException("Maximum 16 approved guests. Revoke an old guest first.");byte[] k=RelayWire.random(32);String id=RelayWire.identity(k);if(!prefs.edit().putString("key_"+id,encode(k)).putString("name_"+id,name).commit())throw new IllegalStateException("Unable to save permission.");return k;}
 synchronized Map<String,String> guests(){Map<String,String> result=new TreeMap<>();for(String key:prefs.getAll().keySet())if(key.startsWith("key_")){String id=key.substring(4);result.put(id,prefs.getString("name_"+id,"Guest"));}return result;}
 synchronized void revoke(String id){prefs.edit().remove("key_"+id).remove("name_"+id).commit();}
 synchronized void clear(){prefs.edit().clear().commit();}
}
