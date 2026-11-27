package com.lanremote.bluetooth;
import java.util.*;
public final class SonyKeysTest {
 static void check(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] args){Map<String,String> keys=new HashMap<>();keys.put("Confirm","AAAAAQAAAAEAAABlAw==");check(SonyKeys.command("ENTER",keys).equals(keys.get("Confirm")));check(SonyKeys.command("HDMI2",keys).isEmpty());check(SonyKeys.command("powerOff",keys).isEmpty());check(!SonyKeys.valid("<bad>"));check(SonyKeys.envelope(keys.get("Confirm")).contains("<IRCCCode>"+keys.get("Confirm")+"</IRCCCode>"));System.out.println("Sony model-specific key and XML tests passed");}
}
