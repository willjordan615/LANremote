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
  String manual="30:12:34:56:78:90";
  java.util.Map<String,String> all=WakeAddress.candidates(manual,true,wired,wifi);
  check(new java.util.ArrayList<>(all.keySet()).equals(java.util.Arrays.asList(manual)));
  check(WakeAddress.candidates("201234567890",true,wired,wifi).size()==1);
  check(WakeAddress.candidates("",true,wired,wifi).isEmpty());
  check(WakeAddress.candidates(manual,false,wired,wifi).size()==2);
  check(!WakeAddress.candidates(manual,false,wired,wifi).containsKey(manual));
  check(WakeAddress.candidates(manual,false,"","").containsKey(manual));
  check(WakeAddress.candidates("",false,wired,wifi).size()==2);
  check(WakeAddress.candidates(manual,true,"","").containsKey(manual));
  check(WakeAddress.candidates("",false,"00:00:00:00:00:00","FF:FF:FF:FF:FF:FF").isEmpty());
  check(WakeAddress.candidates(wired,false,wired,wifi).get(wired).equals("TV Ethernet"));
  check(WakeAddress.candidates("",false,wifi,"201234567890").size()==1);
  System.out.println("Wake address selection and multi-address deduplication tests passed");
 }
}
