package com.lanremote.bluetooth;
import android.content.Context;
import android.net.nsd.*;
import android.os.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// Bounded, on-demand Android TV DNS-SD discovery; no periodic scan.
final class AtvDiscovery {
 static List<TvDiscovery.Found> find(Context context)throws Exception{
  RelayNetwork network=RelayNetwork.wifi(context);NsdManager manager=context.getSystemService(NsdManager.class);
  List<TvDiscovery.Found> found=Collections.synchronizedList(new ArrayList<>());ArrayDeque<NsdServiceInfo> queue=new ArrayDeque<>();Object lock=new Object();boolean[] active={true},busy={false};
  Runnable[] resolve={null};resolve[0]=()->{synchronized(lock){if(!active[0]||busy[0]||queue.isEmpty())return;busy[0]=true;NsdServiceInfo service=queue.remove();try{manager.resolveService(service,new NsdManager.ResolveListener(){public void onResolveFailed(NsdServiceInfo s,int error){next();}public void onServiceResolved(NsdServiceInfo s){InetAddress host=s.getHost();synchronized(lock){if(active[0]&&host!=null&&s.getPort()==6466&&network.allows(host)&&found.size()<32)found.add(new TvDiscovery.Found(TvPlatform.ANDROID,host.getHostAddress()));}next();}private void next(){synchronized(lock){busy[0]=false;}resolve[0].run();}});}catch(RuntimeException e){busy[0]=false;}}};
  NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){public void onDiscoveryStarted(String s){}public void onDiscoveryStopped(String s){}public void onStartDiscoveryFailed(String s,int e){}public void onStopDiscoveryFailed(String s,int e){}public void onServiceLost(NsdServiceInfo s){}public void onServiceFound(NsdServiceInfo s){synchronized(lock){if(!active[0]||queue.size()>=32)return;queue.add(s);}resolve[0].run();}};
  manager.discoverServices("_androidtvremote2._tcp.",NsdManager.PROTOCOL_DNS_SD,listener);try{new CountDownLatch(1).await(4500,TimeUnit.MILLISECONDS);}finally{synchronized(lock){active[0]=false;queue.clear();}try{manager.stopServiceDiscovery(listener);}catch(RuntimeException ignored){}}
  synchronized(found){return new ArrayList<>(found);}
 }
}
