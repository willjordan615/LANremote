package com.lanremote.bluetooth;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.concurrent.*;
import android.os.Handler;
import android.os.SystemClock;

final class RelayPairHost implements AutoCloseable {
 static final class Request {
  final String address,code;final CountDownLatch latch=new CountDownLatch(1);
  private volatile boolean approved;
  Request(String address,String code){this.address=address;this.code=code;}
  synchronized void decide(boolean yes){if(latch.getCount()!=0){approved=yes;latch.countDown();}}
  boolean await()throws InterruptedException{return latch.await(60,TimeUnit.SECONDS)&&approved;}
 }
 interface Observer{void changed(Request request);}
 private final RelayNetwork network;private final RelayWire.Invite invite;private final Handler main;private final Observer observer;private final ServerSocket server;
 private volatile boolean closed;private volatile Socket active;private volatile Request pending;
 RelayPairHost(RelayNetwork network,RelayWire.Invite invite,Handler main,Observer observer)throws Exception{
  this.network=network;this.invite=invite;this.main=main;this.observer=observer;server=new ServerSocket();try{server.setReuseAddress(true);server.bind(new InetSocketAddress(network.local,RelayPairWire.PORT),1);}catch(Exception e){server.close();throw e;}
  new Thread(this::loop,"lanrelay-pair-host").start();
 }
 private void loop(){long last=0;while(!closed)try{Socket s=server.accept();long now=SystemClock.elapsedRealtime();if(!network.allows(s.getInetAddress())||now-last<5000){s.close();continue;}last=now;active=s;if(closed){s.close();break;}handle(s);active=null;}catch(IOException e){if(!closed)close();}}
 private void handle(Socket s){Request request=null;byte[] key=null;try(Socket socket=s){
  socket.setSoTimeout(5000);DataInputStream in=new DataInputStream(socket.getInputStream());DataOutputStream out=new DataOutputStream(socket.getOutputStream());
  byte[] commitment=RelayPairWire.field(in,32,32);KeyPair host=RelayPairWire.keys();byte[] hp=host.getPublic().getEncoded(),hn=RelayWire.random(32);RelayPairWire.field(out,hp);RelayPairWire.field(out,hn);
  byte[] gp=RelayPairWire.field(in,64,256),gn=RelayPairWire.field(in,32,32);if(!RelayPairWire.verify(commitment,gp,gn))return;
  byte[] transcript=RelayPairWire.transcript(commitment,hp,hn,gp,gn);key=RelayPairWire.derive(host,gp,transcript);
  request=new Request(s.getInetAddress().getHostAddress(),RelayPairWire.code(key,transcript));pending=request;Request r=request;main.post(()->{if(!closed&&pending==r)observer.changed(r);});
  if(!r.await()||closed)return;
  socket.setSoTimeout(65000);byte[] consent=RelayWire.read(in,key,transcript,1);if(consent.length!=1||consent[0]!=1||closed)return;
  RelayWire.write(out,key,transcript,2,invite.key);
 }catch(Exception ignored){}finally{if(key!=null)java.util.Arrays.fill(key,(byte)0);if(request!=null){request.decide(false);pending=null;main.post(()->{if(pending==null)observer.changed(null);});}}}
 @Override public void close(){closed=true;Request r=pending;if(r!=null)r.decide(false);try{server.close();}catch(IOException ignored){}Socket s=active;if(s!=null)try{s.close();}catch(IOException ignored){}}
}
