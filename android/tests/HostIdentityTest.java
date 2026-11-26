package com.lanremote.bluetooth;
public class HostIdentityTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  check(HostIdentity.matches("AA:BB:CC:DD:EE:FF","aa:bb:cc:dd:ee:ff"));
  check(!HostIdentity.matches("AA:BB:CC:DD:EE:FF","AA:BB:CC:DD:EE:00"));
  check(!HostIdentity.matches("","AA:BB:CC:DD:EE:FF"));
  check(!HostIdentity.matches(null,null));
  check(HostIdentity.initial(null,"legacy").equals("legacy"));
  check(HostIdentity.initial("","legacy").isEmpty());
  check(HostIdentity.initial("selected","legacy").equals("selected"));
  System.out.println("Host identity tests passed");
 }
}
