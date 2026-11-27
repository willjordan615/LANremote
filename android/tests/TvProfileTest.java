package com.lanremote.bluetooth;
public final class TvProfileTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){TvProfile p=new TvProfile("a",TvPlatform.LG,"192.168.1.2","Living room");TvProfile q=p.at("192.168.1.3");check(q.id.equals(p.id)&&q.name.equals(p.name));check(!TvProfile.sameEndpoint(p,q));check(TvProfile.sameEndpoint(p,p.rename("Bedroom")));check(TvProfile.volumeAction("MUTE")&&TvProfile.volumeAction("volumeUp")&&!TvProfile.volumeAction("HOME"));check(TvProfile.supported(TvPlatform.SONY)&&!TvProfile.supported(TvPlatform.FIRE));System.out.println("TV profile and volume routing tests passed");}
}
