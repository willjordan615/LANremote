package com.lanremote.bluetooth;
public class WakeAddressTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  String wired="10:12:34:56:78:90",wifi="20:12:34:56:78:90",target="192.168.1.2";
  check(WakeAddress.normalize("201234567890").equals(wifi));check(WakeAddress.normalize("00:00:00:00:00:00").isEmpty());check(WakeAddress.normalize("FF:FF:FF:FF:FF:FF").isEmpty());check(WakeAddress.normalize("01:12:34:56:78:90").isEmpty());
  check(WakeAddress.choose(target,wired,wifi,"192.168.1.3",target,"connected","connected").equals(wifi));
  check(WakeAddress.choose(target,wired,wifi,target,"","connected","disconnected").equals(wired));
  check(WakeAddress.choose(target,wired,wifi,"","","","").isEmpty());
  check(WakeAddress.choose(target,wired,wifi,"","","disconnected","connected").equals(wifi));
  check(WakeAddress.choose(target,wired,wifi,"192.168.1.3","192.168.1.4","connected","connected").isEmpty());
  System.out.println("Wake address selection tests passed");
 }
}
