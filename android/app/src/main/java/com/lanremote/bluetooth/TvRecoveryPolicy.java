package com.lanremote.bluetooth;
import java.util.*;
final class TvRecoveryPolicy {
 static final long COOLDOWN=120000;
 static final int MAX_CANDIDATES=6;
 static boolean secure(TvPlatform p){return p==TvPlatform.LG||p==TvPlatform.SAMSUNG||p==TvPlatform.ANDROID;}
 static boolean failure(String s){return s.startsWith("TV connection failed:")||s.equals("TV disconnected.")||s.startsWith("Samsung connection unavailable")||s.equals("Samsung TV disconnected.")||s.startsWith("Android TV unavailable:")||s.equals("Android TV disconnected.");}
 static String unique(Collection<String> matches,String old){Set<String> distinct=new HashSet<>(matches);if(distinct.size()!=1)return "";String address=distinct.iterator().next();return old.equals(address)?"":address;}
}
