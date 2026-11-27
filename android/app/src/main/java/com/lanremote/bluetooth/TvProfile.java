package com.lanremote.bluetooth;

final class TvProfile {
 final String id,ip,name;final TvPlatform platform;
 TvProfile(String id,TvPlatform platform,String ip,String name){this.id=id;this.platform=platform;this.ip=ip;this.name=name;}
 String label(){return name.isEmpty()?platform.label+" • "+ip:name;}
 TvProfile rename(String name){return new TvProfile(id,platform,ip,name);}
 TvProfile at(String address){return new TvProfile(id,platform,address,name);}
 static boolean supported(TvPlatform p){return p==TvPlatform.LG||p==TvPlatform.SAMSUNG||p==TvPlatform.ROKU||p==TvPlatform.ANDROID||p==TvPlatform.SONY;}
 static boolean volumeAction(String action){return "volumeUp".equals(action)||"volumeDown".equals(action)||"MUTE".equals(action);}
 static boolean sameEndpoint(TvProfile a,TvProfile b){return a!=null&&b!=null&&a.platform==b.platform&&a.ip.equals(b.ip);}
}
