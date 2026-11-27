package com.lanremote.bluetooth;
public class TvPlatformTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){check(TvPlatform.service("roku:ecp")==TvPlatform.ROKU);check(TvPlatform.service("urn:lge-com:service:webos-second-screen:1")==TvPlatform.LG);check(TvPlatform.service("urn:samsung.com:device:RemoteControlReceiver:1")==TvPlatform.SAMSUNG);check(TvPlatform.service("ssdp:all")==TvPlatform.NONE);check(TvPlatform.saved("unknown")==TvPlatform.NONE);check(TvPlatform.samsungKey("ENTER").equals("KEY_ENTER"));check(TvPlatform.samsungKey("click").isEmpty());check(TvPlatform.samsungKey("keyboard").isEmpty());System.out.println("TV platform and Samsung mapping tests passed");}
}
