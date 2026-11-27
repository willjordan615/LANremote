package com.lanremote.bluetooth;
public final class TvAudioPolicyTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  check(TvAudioPolicy.lgKey("volumeUp",false).isEmpty());
  check(TvAudioPolicy.lgKey("volumeDown",false).isEmpty());
  check(TvAudioPolicy.lgKey("MUTE",false).equals("MUTE"));
  check(TvAudioPolicy.lgKey("volumeUp",true).equals("VOLUMEUP"));
  check(TvAudioPolicy.lgKey("volumeDown",true).equals("VOLUMEDOWN"));
  check(TvAudioPolicy.lgKey("MUTE",true).equals("MUTE"));
  check(!TvAudioPolicy.lgNeedsPointer("volumeUp",false)&&TvAudioPolicy.lgNeedsPointer("volumeUp",true));
  check(TvAudioPolicy.lgKey("HOME",true).isEmpty());
  System.out.println("TV audio routing policy tests passed");
 }
}
