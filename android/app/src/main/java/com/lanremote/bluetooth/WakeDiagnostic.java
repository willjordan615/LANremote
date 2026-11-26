package com.lanremote.bluetooth;

import android.content.Context;
import android.os.SystemClock;
import java.io.IOException;
import java.net.*;

final class WakeDiagnostic implements AutoCloseable {
 interface Observer{void update(String summary,String report,boolean done);}
 private volatile boolean cancelled;
 private volatile Thread worker;
 private volatile Socket probe;
 private volatile DatagramSocket datagram;
 private final Context context;
 private final String target,mac,source;
 private final int port;
 private final Observer observer;
 WakeDiagnostic(Context context,String target,String mac,String source,int port,Observer observer){this.context=context.getApplicationContext();this.target=target;this.mac=mac;this.source=source;this.port=port;this.observer=observer;}
 void start(){worker=new Thread(this::run,"lg-wake-diagnostic");worker.start();}
 private void publish(String summary,StringBuilder report,boolean done){if(!cancelled)observer.update(summary,report.toString(),done);}
 private boolean reachable(RelayNetwork wifi,InetAddress tv){
  if(cancelled)return false;
  try(Socket s=new Socket()){probe=s;if(cancelled)return false;wifi.network.bindSocket(s);s.connect(new InetSocketAddress(tv,port),800);return true;}
  catch(IOException|RuntimeException e){return false;}finally{probe=null;}
 }
 private void run(){
  StringBuilder report=new StringBuilder("TV power-on diagnostic\n").append("TV IP: ").append(target).append("\nWake address: ").append(mac.isEmpty()?"Missing or invalid":mac).append(" (").append(source).append(")\nControl port: ").append(port).append("\n");
  int sent=0;
  try{
   if(!RelayWire.privateIP(target))throw new IllegalArgumentException("Select a valid local TV address first.");
   byte[] packet=WakePacket.create(mac);
   report.append("Address format: valid (not proof of TV identity)\n");
   publish("Power on: address valid; checking Wi-Fi…",report,false);
   RelayNetwork wifi=RelayNetwork.wifi(context);InetAddress tv=InetAddress.getByName(target);
   if(!wifi.allows(tv)||tv.equals(wifi.local))throw new IOException("Phone and TV must be on the same Wi-Fi subnet.");
   InetAddress broadcast=WakePacket.broadcast(wifi.local,wifi.prefix);
   report.append("Wi-Fi IPv4: ").append(wifi.local.getHostAddress()).append('/').append(wifi.prefix).append("\nBroadcast: ").append(broadcast.getHostAddress()).append(":9\n");
   boolean before=reachable(wifi,tv);if(cancelled)return;
   report.append("Before wake: control port ").append(before?"already reachable":"not reachable").append('\n');
   publish("Power on: sending wake packets…",report,false);
   try(DatagramSocket sock=new DatagramSocket(null)){
    datagram=sock;if(cancelled)return;wifi.network.bindSocket(sock);sock.bind(new InetSocketAddress(wifi.local,0));sock.setBroadcast(true);
    for(int i=0;i<3&&!cancelled;i++){sock.send(new DatagramPacket(packet,packet.length,broadcast,9));sent++;Thread.sleep(120);}
   }finally{datagram=null;}
   if(cancelled)return;
   report.append("Wake packets: ").append(sent).append(" sent by phone (delivery not confirmed)\n");
   if(before){report.append("Result: TV control port was already reachable; cannot confirm a wake transition. Check whether the screen is on.");publish("Power on: TV was already reachable.",report,true);return;}
   publish("Wake request sent • checking TV for up to 30 seconds…",report,false);
   long start=SystemClock.elapsedRealtime(),end=start+30000;
   while(!cancelled&&SystemClock.elapsedRealtime()<end){
    if(reachable(wifi,tv)){long seconds=(SystemClock.elapsedRealtime()-start+999)/1000;report.append("Result: TV control port became reachable after about ").append(seconds).append(" seconds.\nThis suggests wake succeeded; screen state is not verified.");publish("Power on: TV control service is reachable.",report,true);return;}
    Thread.sleep(1100);
   }
   if(!cancelled){report.append("Result: no response from the configured TV control port within 30 seconds.\nCheck Turn on via Wi-Fi, active Wi-Fi MAC, and standby settings. The TV may also have changed IP or blocked the control port.");publish("Power on: packets sent; TV did not respond.",report,true);}
  }catch(Exception e){if(!cancelled){report.append("Wake packets sent: ").append(sent).append("\nResult: ").append(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());publish("Power on: "+(sent==0?"could not send wake request.":"wake test interrupted."),report,true);}}
 }
 @Override public void close(){cancelled=true;Thread t=worker;if(t!=null)t.interrupt();DatagramSocket d=datagram;if(d!=null)d.close();Socket s=probe;if(s!=null)try{s.close();}catch(IOException ignored){}}
}
