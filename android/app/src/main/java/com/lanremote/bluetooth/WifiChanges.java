package com.lanremote.bluetooth;

import android.content.Context;
import android.net.*;
import android.os.Handler;
import java.net.Inet4Address;
import java.util.HashMap;
import java.util.Map;

// Only active while the normal remote is visible; no Wi-Fi scanning or polling.
final class WifiChanges implements AutoCloseable {
 private final ConnectivityManager manager;
 private final Handler main;
 private final Runnable changed;
 private final Map<Network,String> addresses=new HashMap<>();
 private boolean closed,registered;
 private final Runnable notifyChange=this::notifyIfOpen;
 private void notifyIfOpen(){if(!closed)changed.run();}
 private final ConnectivityManager.NetworkCallback callback=new ConnectivityManager.NetworkCallback(){
  @Override public void onLinkPropertiesChanged(Network n,LinkProperties properties){
   StringBuilder key=new StringBuilder();for(LinkAddress a:properties.getLinkAddresses())if(a.getAddress() instanceof Inet4Address)key.append(a.toString()).append(';');
   String value=key.toString();main.post(()->{if(closed)return;String previous=addresses.put(n,value);if(!value.equals(previous))signal();});
  }
  @Override public void onLost(Network n){main.post(()->{if(!closed&&addresses.remove(n)!=null)signal();});}
 };
 WifiChanges(Context c,Handler main,Runnable changed){this.manager=c.getSystemService(ConnectivityManager.class);this.main=main;this.changed=changed;try{manager.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),callback);registered=true;}catch(RuntimeException ignored){}}
 private void signal(){main.removeCallbacks(notifyChange);main.postDelayed(notifyChange,500);}
 @Override public void close(){closed=true;main.removeCallbacks(notifyChange);if(registered)try{manager.unregisterNetworkCallback(callback);}catch(RuntimeException ignored){}addresses.clear();}
}
