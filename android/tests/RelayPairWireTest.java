package com.lanremote.bluetooth;
import java.security.*;
import java.util.*;
public class RelayPairWireTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  KeyPair guest=RelayPairWire.keys(),host=RelayPairWire.keys();
  byte[] gp=guest.getPublic().getEncoded(),hp=host.getPublic().getEncoded(),gn=RelayWire.random(32),hn=RelayWire.random(32);
  byte[] commit=RelayPairWire.hash(gp,gn);check(RelayPairWire.verify(commit,gp,gn));check(!RelayPairWire.verify(commit,gp,RelayWire.random(32)));
  byte[] t=RelayPairWire.transcript(commit,hp,hn,gp,gn),a=RelayPairWire.derive(guest,hp,t),b=RelayPairWire.derive(host,gp,t);
  check(Arrays.equals(a,b));check(RelayPairWire.code(a,t).equals(RelayPairWire.code(b,t)));check(RelayPairWire.code(a,t).matches("[0-9]{6}"));
  byte[] changed=RelayPairWire.transcript(commit,hp,RelayWire.random(32),gp,gn);check(!Arrays.equals(a,RelayPairWire.derive(host,gp,changed)));
  System.out.println("Pairing agreement, commitment, transcript and comparison tests passed");
 }
}
