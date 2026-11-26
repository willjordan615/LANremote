package com.lanremote.bluetooth;

final class HostIdentity {
 private HostIdentity(){}
 static boolean matches(String selected,String candidate){return selected!=null&&!selected.isEmpty()&&candidate!=null&&selected.equalsIgnoreCase(candidate);}
 static String initial(String saved,String legacy){return saved!=null?saved:legacy==null?"":legacy;}
}
