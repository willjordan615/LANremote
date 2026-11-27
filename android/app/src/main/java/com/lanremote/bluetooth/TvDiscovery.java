package com.lanremote.bluetooth;
import android.content.Context;
import android.os.SystemClock;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class TvDiscovery {
 static final class Found {final TvPlatform platform;final String ip;Found(TvPlatform p,String i){platform=p;ip=i;}String label(){return platform.label+" • "+ip;}}
 static TvPlatform parse(String response){String[] lines=response.split("\\r?\\n");if(lines.length==0||!lines[0].matches("HTTP/1\\.[01] 200(?: .*)?"))return TvPlatform.NONE;for(String line:lines){int colon=line.indexOf(':');if(colon>0&&line.substring(0,colon).trim().equalsIgnoreCase("ST"))return TvPlatform.service(line.substring(colon+1).trim());}return TvPlatform.NONE;}
 static List<Found> find(Context c)throws Exception{
  RelayNetwork network=RelayNetwork.wifi(c);Map<String,Found> found=new LinkedHashMap<>();
  try(MulticastSocket socket=new MulticastSocket(null)){
   socket.bind(new InetSocketAddress(0));network.network.bindSocket(socket);socket.setTimeToLive(1);socket.setSoTimeout(350);
   InetAddress multicast=InetAddress.getByName("239.255.255.250");
   for(String service:new String[]{LgDiscovery.SERVICE,"urn:samsung.com:device:RemoteControlReceiver:1","urn:samsung.com:service:MultiScreenService:1","roku:ecp"}){
    byte[] bytes=("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: "+service+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
    socket.send(new DatagramPacket(bytes,bytes.length,multicast,1900));
   }
   long end=SystemClock.elapsedRealtime()+4500;byte[] buffer=new byte[8192];
   while(SystemClock.elapsedRealtime()<end&&found.size()<32){DatagramPacket packet=new DatagramPacket(buffer,buffer.length);try{socket.receive(packet);}catch(SocketTimeoutException e){continue;}if(!network.allows(packet.getAddress()))continue;TvPlatform p=parse(new String(packet.getData(),0,packet.getLength(),StandardCharsets.UTF_8));if(p==TvPlatform.NONE)continue;String ip=packet.getAddress().getHostAddress();found.put(p.name()+":"+ip,new Found(p,ip));}
  }return new ArrayList<>(found.values());
 }
}
