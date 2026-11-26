package com.lanremote.bluetooth;

import android.content.Context;
import android.os.SystemClock;
import java.io.IOException;
import java.net.*;
import java.util.*;

final class WakeDiagnostic implements AutoCloseable {
 interface Observer{void update(String summary,String report,boolean done);}
 private volatile boolean cancelled;
 private volatile Thread worker;
 private volatile Socket probe;
 private volatile DatagramSocket datagram;
 private final Context context;
 private final String target;
 private final Map<String,String> candidates;
 private final int port;
 private final Observer observer;
 WakeDiagnostic(Context context,String target,Map<String,String> candidates,int port,Observer observer){this.context=context.getApplicationContext();this.target=target;this.candidates=new LinkedHashMap<>(candidates);this.port=port;this.observer=observer;}
 void start(){worker=new Thread(this::run,"lg-wake-diagnostic");worker.start();}
 private void publish(String summary,StringBuilder report,boolean done){if(!cancelled)observer.update(summary,report.toString(),done);}
 private boolean reachable(RelayNetwork wifi,InetAddress tv){
  if(cancelled)return false;
  try(Socket s=new Socket()){probe=s;if(cancelled)return false;wifi.network.bindSocket(s);s.connect(new InetSocketAddress(tv,port),800);return true;}
  catch(IOException|RuntimeException e){return false;}finally{probe=null;}
 }
 private void run(){
  StringBuilder report=new StringBuilder("TV power-on diagnostic\n").append("TV IP: ").append(target).append("\nControl port: ").append(port).append("\nWake addresses: ").append(candidates.size()).append("\n");
  for(Map.Entry<String,String> entry:candidates.entrySet())report.append("  ").append(entry.getKey()).append(" (").append(entry.getValue()).append(")\n");
  int sent=0;
  try{
   if(!RelayWire.privateIP(target))throw new IllegalArgumentException("Select a valid local TV address first.");
   if(candidates.isEmpty())throw new IllegalArgumentException("No valid wake address saved. Turn the TV on and connect once to learn its addresses. If none are found, open your router or mesh app, find the LG TV in connected devices, and enter the TV MAC in TV setup (not the router’s own MAC).");
   Map<String,byte[]> packets=new LinkedHashMap<>();for(String mac:candidates.keySet())packets.put(mac,WakePacket.create(mac));
   report.append("Address formats: valid (not proof of TV identity)\n");
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
    for(Map.Entry<String,byte[]> entry:packets.entrySet()){
     int count=0;for(int i=0;i<3&&!cancelled;i++){byte[] packet=entry.getValue();sock.send(new DatagramPacket(packet,packet.length,broadcast,9));sent++;count++;Thread.sleep(120);}
     report.append("Sent for ").append(entry.getKey()).append(": ").append(count).append("\n");
    }
   }finally{datagram=null;}
   if(cancelled)return;
   report.append("Wake packets: ").append(sent).append(" sent by phone (delivery not confirmed)\n");
   if(before){report.append("Result: TV control port was already reachable; cannot confirm a wake transition. Check whether the screen is on.");publish("Power on: TV was already reachable.",report,true);return;}
   publish("Wake request sent • checking TV for up to 30 seconds…",report,false);
   long start=SystemClock.elapsedRealtime(),end=start+30000;
   while(!cancelled&&SystemClock.elapsedRealtime()<end){
    if(reachable(wifi,tv)){long seconds=(SystemClock.elapsedRealtime()-start+999)/1000;report.append("Result: TV control port became reachable after about ").append(seconds).append(" seconds.\nThis suggests wake succeeded; screen state and which address worked are not verified.");publish("Power on: TV control service is reachable.",report,true);return;}
    Thread.sleep(1100);
   }
   if(!cancelled){report.append("Result: no response from the configured TV control port within 30 seconds.\nCheck Turn on via Wi-Fi and standby settings. If enabled, check the TV MAC shown in your router or mesh app’s connected-device list. It may differ from the TV settings address. Use TV setup → Enter TV MAC from router to save it. The TV may also have changed IP or blocked the control port.");publish("Power on: packets sent; TV did not respond.",report,true);}
  }catch(Exception e){if(!cancelled){report.append("Wake packets sent: ").append(sent).append("\nResult: ").append(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());publish("Power on: "+(sent==0?"could not send wake request.":"wake test interrupted."),report,true);}}
 }
 @Override public void close(){cancelled=true;Thread t=worker;if(t!=null)t.interrupt();DatagramSocket d=datagram;if(d!=null)d.close();Socket s=probe;if(s!=null)try{s.close();}catch(IOException ignored){}}
}
