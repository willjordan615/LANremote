package com.lanremote.bluetooth;

import android.content.Context;
import android.net.*;
import java.net.*;

final class RelayNetwork {
 final Network network;final InetAddress local;final int prefix;
 private RelayNetwork(Network network,InetAddress local,int prefix){this.network=network;this.local=local;this.prefix=prefix;}
 static RelayNetwork wifi(Context c)throws Exception{ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);for(Network n:cm.getAllNetworks()){NetworkCapabilities caps=cm.getNetworkCapabilities(n);LinkProperties lp=cm.getLinkProperties(n);if(caps==null||lp==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))continue;for(LinkAddress la:lp.getLinkAddresses()){InetAddress ip=la.getAddress();if(ip instanceof Inet4Address&&RelayWire.privateIP(ip.getHostAddress()))return new RelayNetwork(n,ip,la.getPrefixLength());}}throw new IllegalStateException("Connect to a private IPv4 Wi-Fi network first.");}
 boolean allows(InetAddress addr){return addr instanceof Inet4Address&&RelayWire.privateIP(addr.getHostAddress())&&LgDiscovery.sameSubnet(local,addr,prefix);}
}
