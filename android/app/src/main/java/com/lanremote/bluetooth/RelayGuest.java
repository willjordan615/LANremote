package com.lanremote.bluetooth;

import android.content.Context;
import android.os.Handler;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

final class RelayGuest implements AutoCloseable {
 interface Observer{void update(boolean ready,String status);}
 private final Context context;private final Handler main;private final RelayWire.Invite invite;private final Observer observer;
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32));
 private final java.util.concurrent.atomic.AtomicBoolean motionPending=new java.util.concurrent.atomic.AtomicBoolean();
 private final java.util.concurrent.atomic.AtomicBoolean repeatPending=new java.util.concurrent.atomic.AtomicBoolean();
 private volatile boolean closed;private boolean ready;private volatile Socket active;
 private final Runnable poll=new Runnable(){public void run(){if(closed)return;submit(new byte[]{0});main.postDelayed(this,4000);}};
 RelayGuest(Context context,Handler main,RelayWire.Invite invite,Observer observer){this.context=context.getApplicationContext();this.main=main;this.invite=invite;this.observer=observer;main.post(poll);}
 boolean ready(){return ready&&!closed;}
 void submit(byte[] command){if(closed)return;try{worker.execute(()->exchange(command));}catch(RejectedExecutionException e){observer.update(ready,"Wi-Fi relay queue full. Try again.");}}
 void repeatKey(int report,int usage){if(!ready()||!worker.getQueue().isEmpty()||!repeatPending.compareAndSet(false,true))return;try{worker.execute(()->{try{exchange(RelayWire.key(report,usage));}finally{repeatPending.set(false);}});}catch(RejectedExecutionException e){repeatPending.set(false);}}
 void movePointer(int x,int y){if(!ready()||!RelayWire.validMovement(x,y)||!worker.getQueue().isEmpty()||!motionPending.compareAndSet(false,true))return;try{worker.execute(()->{try{exchange(RelayWire.movement(x,y));}finally{motionPending.set(false);}});}catch(RejectedExecutionException e){motionPending.set(false);}}
 private void exchange(byte[] command){if(closed)return;try{RelayNetwork network=RelayNetwork.wifi(context);InetAddress target=InetAddress.getByName(invite.ip);if(!network.allows(target))throw new IOException("Host must be on the same Wi-Fi subnet.");
  try(Socket socket=new Socket()){active=socket;if(closed)return;network.network.bindSocket(socket);socket.connect(new InetSocketAddress(target,RelayWire.PORT),1500);socket.setSoTimeout(2500);
   DataInputStream in=new DataInputStream(socket.getInputStream());DataOutputStream out=new DataOutputStream(socket.getOutputStream());byte[] challenge=new byte[16];in.readFully(challenge);if(!invite.hostId.equals(RelayWire.identity(invite.key))){out.writeShort(0);out.write(RelayWire.identity(invite.key).getBytes(java.nio.charset.StandardCharsets.US_ASCII));}RelayWire.write(out,invite.key,challenge,1,command);byte[] reply=RelayWire.read(in,invite.key,challenge,2);
   if(reply.length!=1)throw new IOException("Invalid response");if(reply[0]==0)report(true,"Wi-Fi relay connected");else if(reply[0]==1)report(false,"Relay host has no Bluetooth connection.");else if(command[0]!=3)report(true,"Relay input busy or rejected. Try again.");else report(true,"Relay mouse report rejected or busy; check host mouse diagnostics.");
  }finally{active=null;}
 }catch(Exception e){report(false,"Wi-Fi relay unavailable. Check host, invitation and Wi-Fi.");}}
 private void report(boolean connected,String status){main.post(()->{if(closed)return;ready=connected;observer.update(ready,status);});}
 @Override public void close(){closed=true;ready=false;main.removeCallbacks(poll);worker.shutdownNow();Socket s=active;if(s!=null)try{s.close();}catch(IOException ignored){}}
}
