package com.lanremote.bluetooth;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import android.net.*;
import android.content.Context;
import android.os.SystemClock;

final class LgDiscovery {
 static final String SERVICE="urn:lge-com:service:webos-second-screen:1";
 static final String SEARCH="M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: "+SERVICE+"\r\n\r\n";
 static String label(String response){
  String[] lines=response.split("\\r?\\n");
  if(lines.length==0||!lines[0].matches("HTTP/1\\.[01] 200(?: .*)?"))return null;
  Map<String,String> headers=new HashMap<>();
  for(int i=1;i<lines.length;i++){int p=lines[i].indexOf(':');if(p>0)headers.put(lines[i].substring(0,p).trim().toLowerCase(Locale.ROOT),lines[i].substring(p+1).trim());}
  if(!SERVICE.equalsIgnoreCase(headers.get("st")))return null;
  String info=headers.getOrDefault("server","");info=info.replaceAll("[\\p{Cntrl}]","").trim();
  return info.isEmpty()?"LG webOS TV": "LG webOS TV • "+info.substring(0,Math.min(90,info.length()));
 }
 static boolean sameSubnet(InetAddress a,InetAddress b,int bits){byte[] x=a.getAddress(),y=b.getAddress();if(x.length!=4||y.length!=4||bits<1||bits>32)return false;for(int i=0;i<bits;i++){int mask=1<<(7-i%8);if((x[i/8]&mask)!=(y[i/8]&mask))return false;}return true;}
 static Map<String,String> find(Context context)throws Exception{
  ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);Network wifi=null;LinkProperties lp=null;
  for(Network n:cm.getAllNetworks()){NetworkCapabilities c=cm.getNetworkCapabilities(n);LinkProperties candidate=cm.getLinkProperties(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&candidate!=null){wifi=n;lp=candidate;break;}}
  if(wifi==null)throw new IllegalStateException("Connect your phone to Wi-Fi first.");
  Map<String,String> found=new TreeMap<>();byte[] request=SEARCH.getBytes(StandardCharsets.US_ASCII);
  try(MulticastSocket socket=new MulticastSocket(null)){
   socket.bind(new InetSocketAddress(0));wifi.bindSocket(socket);socket.setTimeToLive(1);socket.setSoTimeout(350);
   InetAddress group=InetAddress.getByName("239.255.255.250");
   DatagramPacket query=new DatagramPacket(request,request.length,group,1900);socket.send(query);socket.send(query);
   long end=SystemClock.elapsedRealtime()+4500;
   while(SystemClock.elapsedRealtime()<end&&found.size()<32){byte[] buffer=new byte[8192];DatagramPacket reply=new DatagramPacket(buffer,buffer.length);try{socket.receive(reply);}catch(SocketTimeoutException e){continue;}
    InetAddress addr=reply.getAddress();if(!(addr instanceof Inet4Address)||!addr.isSiteLocalAddress())continue;
    boolean local=false;for(LinkAddress link:lp.getLinkAddresses())if(link.getAddress() instanceof Inet4Address&&sameSubnet(link.getAddress(),addr,link.getPrefixLength())){local=true;break;}if(!local)continue;
    String name=label(new String(reply.getData(),0,reply.getLength(),StandardCharsets.UTF_8));if(name!=null)found.put(addr.getHostAddress(),name);
   }
  }return found;
 }
}
