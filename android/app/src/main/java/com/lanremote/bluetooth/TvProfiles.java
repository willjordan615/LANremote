package com.lanremote.bluetooth;

import android.content.*;
import org.json.*;
import java.util.*;

final class TvProfiles {
 static final String AUTO="auto",BOX="box";
 private final Context context;private final SharedPreferences prefs;
 TvProfiles(Context c){context=c.getApplicationContext();prefs=c.getSharedPreferences("tv_profiles",Context.MODE_PRIVATE);migrate();}
 private void migrate(){if(prefs.getBoolean("migrated",false))return;SharedPreferences old=context.getSharedPreferences("tv_platform",Context.MODE_PRIVATE),lg=context.getSharedPreferences("lg_remote",Context.MODE_PRIVATE);
  String lgIp=lg.getString("last_connected_address",lg.getString("address",""));TvPlatform p=TvPlatform.saved(old.getString("platform",lgIp.isEmpty()?"NONE":"LG"));String ip=old.getString("ip",p==TvPlatform.LG?lgIp:"");if(p==TvPlatform.LG&&ip.isEmpty())ip=lgIp;
  ArrayList<TvProfile> profiles=new ArrayList<>();String selected="";
  if(TvProfile.supported(p)&&RelayWire.privateIP(ip)){TvProfile first=new TvProfile(UUID.randomUUID().toString(),p,ip,p==TvPlatform.LG?DeviceLabels.first(lg.getString("nickname_"+ip,""),lg.getString("device_name_"+ip,"")):"");profiles.add(first);selected=first.id;}
  if(RelayWire.privateIP(lgIp)&&!(p==TvPlatform.LG&&lgIp.equals(ip))){profiles.add(new TvProfile(UUID.randomUUID().toString(),TvPlatform.LG,lgIp,DeviceLabels.first(lg.getString("nickname_"+lgIp,""),lg.getString("device_name_"+lgIp,""))));}
  if(RelayWire.privateIP(lgIp)&&lg.contains("last_connected_legacy")&&!lg.contains("transport_legacy_"+lgIp))lg.edit().putBoolean("transport_legacy_"+lgIp,lg.getBoolean("last_connected_legacy",false)).apply();
  if(RelayWire.privateIP(lgIp)&&lg.contains("tv_reconnect_paused")&&!lg.contains("paused_"+lgIp))lg.edit().putBoolean("paused_"+lgIp,lg.getBoolean("tv_reconnect_paused",false)).apply();
  prefs.edit().putBoolean("migrated",true).putString("profiles",encode(profiles)).putString("selected",selected).putString("volume",AUTO).apply();
 }
 private static String encode(List<TvProfile> list){JSONArray a=new JSONArray();for(TvProfile p:list)try{a.put(new JSONObject().put("id",p.id).put("platform",p.platform.name()).put("ip",p.ip).put("name",p.name));}catch(JSONException e){throw new IllegalStateException(e);}return a.toString();}
 List<TvProfile> all(){ArrayList<TvProfile> list=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString("profiles","[]"));Set<String> ids=new HashSet<>();for(int i=0;i<Math.min(a.length(),32);i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;String id=o.optString("id"),ip=o.optString("ip");TvPlatform p=TvPlatform.saved(o.optString("platform"));if(!id.matches("[0-9a-f-]{36}")||!ids.add(id)||!RelayWire.privateIP(ip)||!TvProfile.supported(p))continue;list.add(new TvProfile(id,p,ip,DeviceLabels.clean(o.optString("name"))));}}catch(JSONException ignored){}return list;}
 TvProfile get(String id){for(TvProfile p:all())if(p.id.equals(id))return p;return null;}
 TvProfile selected(){return get(prefs.getString("selected",""));}
 String volume(){String v=prefs.getString("volume",AUTO);return AUTO.equals(v)||BOX.equals(v)||get(v)!=null?v:AUTO;}
 void volume(String v){if(AUTO.equals(v)||BOX.equals(v)||get(v)!=null)prefs.edit().putString("volume",v).apply();}
 void select(String id){if(get(id)!=null)prefs.edit().putString("selected",id).apply();}
 TvProfile add(TvPlatform platform,String ip){if(!TvProfile.supported(platform)||!RelayWire.privateIP(ip))throw new IllegalArgumentException("Choose a supported TV and private IPv4 address");List<TvProfile> list=all();for(TvProfile p:list)if(p.platform==platform&&p.ip.equals(ip))return p;if(list.size()>=32)throw new IllegalStateException("32 saved TVs maximum");TvProfile p=new TvProfile(UUID.randomUUID().toString(),platform,ip,"");list.add(p);prefs.edit().putString("profiles",encode(list)).apply();return p;}
 void rename(String id,String name){List<TvProfile> list=all();for(int i=0;i<list.size();i++)if(list.get(i).id.equals(id)){list.set(i,list.get(i).rename(DeviceLabels.clean(name)));prefs.edit().putString("profiles",encode(list)).apply();return;}}
 void remove(String id){List<TvProfile> list=all();list.removeIf(p->p.id.equals(id));SharedPreferences.Editor e=prefs.edit().putString("profiles",encode(list));if(id.equals(prefs.getString("selected","")))e.putString("selected",list.isEmpty()?"":list.get(0).id);if(id.equals(volume()))e.putString("volume",AUTO);e.apply();}
 boolean move(TvProfile from,String address){if(!RelayWire.privateIP(address))return false;List<TvProfile> list=all();for(TvProfile p:list)if(!p.id.equals(from.id)&&p.platform==from.platform&&p.ip.equals(address))return false;for(int i=0;i<list.size();i++)if(list.get(i).id.equals(from.id)&&list.get(i).ip.equals(from.ip)){list.set(i,list.get(i).at(address));prefs.edit().putString("profiles",encode(list)).apply();return true;}return false;}
}
