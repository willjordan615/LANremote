package com.lanremote.bluetooth;

import android.content.Context;
import android.os.Handler;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.concurrent.*;

final class RelayPairGuest implements AutoCloseable {
 interface Observer{void compare(String code);void finished(RelayWire.Invite invite,String error);}
 private volatile boolean closed;private volatile Socket active;private final CountDownLatch consent=new CountDownLatch(1);private volatile boolean approved;
 RelayPairGuest(Context context,Handler main,RelayDiscovery.Found found,Observer observer){
  new Thread(()->{RelayWire.Invite result=null;String error=null;byte[] key=null;try{
   RelayNetwork network=RelayNetwork.wifi(context);InetAddress target=InetAddress.getByName(found.ip);if(!network.allows(target)||target.equals(network.local))throw new IOException("Host must be on the same Wi-Fi.");
   try(Socket s=new Socket()){active=s;if(closed)return;network.network.bindSocket(s);s.connect(new InetSocketAddress(target,RelayPairWire.PORT),2000);s.setSoTimeout(5000);
    DataInputStream in=new DataInputStream(s.getInputStream());DataOutputStream out=new DataOutputStream(s.getOutputStream());
    KeyPair guest=RelayPairWire.keys();byte[] gp=guest.getPublic().getEncoded(),gn=RelayWire.random(32),commitment=RelayPairWire.hash(gp,gn);
    RelayPairWire.field(out,commitment);byte[] hp=RelayPairWire.field(in,64,256),hn=RelayPairWire.field(in,32,32);RelayPairWire.field(out,gp);RelayPairWire.field(out,gn);
    byte[] transcript=RelayPairWire.transcript(commitment,hp,hn,gp,gn);key=RelayPairWire.derive(guest,hp,transcript);String code=RelayPairWire.code(key,transcript);
    main.post(()->{if(!closed)observer.compare(code);});if(!consent.await(60,TimeUnit.SECONDS)||!approved||closed)throw new IOException("Pairing cancelled or expired.");
    RelayWire.write(out,key,transcript,1,new byte[]{1});s.setSoTimeout(65000);byte[] invite=RelayWire.read(in,key,transcript,2);
    if(invite.length==32&&RelayWire.identity(invite).equals(found.id))result=new RelayWire.Invite(found.ip,invite);
    else if(invite.length==64&&new String(invite,32,32,java.nio.charset.StandardCharsets.US_ASCII).equals(found.id))result=new RelayWire.Invite(found.ip,java.util.Arrays.copyOf(invite,32),found.id);
    else throw new IOException("Host identity changed. Find hosts again.");
   }
  }catch(Exception e){error="Pairing not completed. Make sure both codes match and the host approves.";}finally{active=null;if(key!=null)java.util.Arrays.fill(key,(byte)0);}
  RelayWire.Invite done=result;String failure=error;main.post(()->{if(!closed)observer.finished(done,failure);});
  },"lanrelay-pair-guest").start();
 }
 synchronized void approve(){if(!closed){approved=true;consent.countDown();}}
 @Override public void close(){closed=true;consent.countDown();Socket s=active;if(s!=null)try{s.close();}catch(IOException ignored){}}
}
