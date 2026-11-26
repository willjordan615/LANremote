package com.lanremote.bluetooth;

import android.content.Context;
import android.os.Handler;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class RelayHost implements AutoCloseable {
 interface Receiver { byte receive(byte[] command); }
 private final RelayNetwork network;private final RelayWire.Invite invite;private final ServerSocket server;
 private final Handler main;private final Receiver receiver;
 private final AtomicBoolean closed=new AtomicBoolean();
 private final ThreadPoolExecutor workers=new ThreadPoolExecutor(3,3,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8));
 RelayHost(Context context,Handler main,Receiver receiver)throws Exception{
  this.main=main;this.receiver=receiver;network=RelayNetwork.wifi(context);invite=new RelayWire.Invite(network.local.getHostAddress(),RelayWire.random(32));
  server=new ServerSocket();try{server.setReuseAddress(true);server.bind(new InetSocketAddress(network.local,RelayWire.PORT),8);}catch(Exception e){server.close();throw e;}
  new Thread(this::accept,"lanrelay-host").start();
 }
 String invitation(){return invite.encode();}
 private void accept(){while(!closed.get())try{Socket s=server.accept();s.setSoTimeout(2000);if(!network.allows(s.getInetAddress())){s.close();continue;}try{workers.execute(()->handle(s));}catch(RejectedExecutionException e){s.close();}}catch(IOException e){if(!closed.get())close();}}
 private void handle(Socket s){try(Socket socket=s){if(closed.get())return;DataInputStream in=new DataInputStream(socket.getInputStream());DataOutputStream out=new DataOutputStream(socket.getOutputStream());byte[] challenge=RelayWire.random(16);out.write(challenge);out.flush();byte[] command=RelayWire.read(in,invite.key,challenge,1);
  FutureTask<Byte> job=new FutureTask<>(()->closed.get()?(byte)1:receiver.receive(command));main.post(job);byte result;try{result=job.get(1500,TimeUnit.MILLISECONDS);}catch(Exception e){job.cancel(false);return;}RelayWire.write(out,invite.key,challenge,2,new byte[]{result});
 }catch(Exception ignored){}}
 @Override public void close(){if(!closed.compareAndSet(false,true))return;try{server.close();}catch(IOException ignored){}workers.shutdown();}
}
