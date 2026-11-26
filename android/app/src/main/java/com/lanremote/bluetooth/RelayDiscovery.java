package com.lanremote.bluetooth;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class RelayDiscovery {
 static final String TYPE="_lanremote._tcp.";
 static final class Found {
  final String name,ip,id;
  Found(String name,String ip,String id){this.name=name;this.ip=ip;this.id=id;}
 }
 interface Result {void complete(List<Found> hosts,String error);}
 static final class Advertisement implements AutoCloseable {
  private final NsdManager manager;private volatile boolean closed,registered;
  private final NsdManager.RegistrationListener listener=new NsdManager.RegistrationListener(){
   public void onServiceRegistered(NsdServiceInfo info){registered=true;if(closed)unregister();}
   public void onRegistrationFailed(NsdServiceInfo info,int error){registered=false;}
   public void onServiceUnregistered(NsdServiceInfo info){registered=false;}
   public void onUnregistrationFailed(NsdServiceInfo info,int error){registered=false;}
  };
  Advertisement(Context context,String name,RelayWire.Invite invite){
   manager=context.getSystemService(NsdManager.class);
   NsdServiceInfo info=new NsdServiceInfo();String safe=name.replaceAll("[\\p{Cntrl}]","").trim();if(safe.length()>24)safe=safe.substring(0,24);
   info.setServiceName("LANremote - "+safe);info.setServiceType(TYPE);info.setPort(RelayWire.PORT);info.setAttribute("id",RelayWire.identity(invite.key));
   manager.registerService(info,NsdManager.PROTOCOL_DNS_SD,listener);
  }
  private void unregister(){try{manager.unregisterService(listener);}catch(RuntimeException ignored){}}
  @Override public void close(){closed=true;if(registered)unregister();}
 }
 static final class Search implements AutoCloseable {
  private final NsdManager manager;private final Handler main;private final RelayNetwork wifi;private final Result result;
  private final Map<String,Found> found=new LinkedHashMap<>();private final ArrayDeque<NsdServiceInfo> pending=new ArrayDeque<>();private final Set<String> seen=new HashSet<>();
  private boolean closed,started,resolving;
  private final Runnable timeout=()->finish(null);
  private final NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){
   public void onDiscoveryStarted(String type){main.post(()->{started=true;if(closed)stop();});}
   public void onStartDiscoveryFailed(String type,int error){main.post(()->finish("Host discovery unavailable ("+error+")."));}
   public void onStopDiscoveryFailed(String type,int error){}
   public void onDiscoveryStopped(String type){}
   public void onServiceLost(NsdServiceInfo info){main.post(()->{seen.remove(info.getServiceName());found.values().removeIf(h->h.name.equals(info.getServiceName()));});}
   public void onServiceFound(NsdServiceInfo info){main.post(()->{if(closed||!TYPE.equalsIgnoreCase(info.getServiceType())||seen.size()>=32||!seen.add(info.getServiceName()))return;pending.add(info);next();});}
  };
  Search(Context context,Handler main,Result result)throws Exception{
   this.main=main;this.result=result;wifi=RelayNetwork.wifi(context);manager=context.getSystemService(NsdManager.class);
   manager.discoverServices(TYPE,NsdManager.PROTOCOL_DNS_SD,listener);main.postDelayed(timeout,5500);
  }
  private void next(){if(closed||resolving||pending.isEmpty())return;resolving=true;NsdServiceInfo info=pending.remove();
   try{manager.resolveService(info,new NsdManager.ResolveListener(){
    public void onResolveFailed(NsdServiceInfo service,int error){main.post(()->{resolving=false;next();});}
    public void onServiceResolved(NsdServiceInfo service){main.post(()->{resolving=false;if(closed)return;InetAddress host=service.getHost();byte[] attr=service.getAttributes().get("id");String id=attr==null?"":new String(attr,StandardCharsets.US_ASCII);
     if(host!=null&&wifi.allows(host)&&!host.equals(wifi.local)&&service.getPort()==RelayWire.PORT&&id.matches("[0-9a-f]{32}")){String ip=host.getHostAddress();found.put(ip,new Found(service.getServiceName(),ip,id));}next();});}
   });}catch(RuntimeException e){resolving=false;next();}
  }
  private void stop(){if(started)try{manager.stopServiceDiscovery(listener);}catch(RuntimeException ignored){}}
  private void finish(String error){if(closed)return;List<Found> hosts=new ArrayList<>(found.values());close();result.complete(hosts,error);}
  @Override public void close(){if(closed)return;closed=true;main.removeCallbacks(timeout);stop();pending.clear();}
 }
}
