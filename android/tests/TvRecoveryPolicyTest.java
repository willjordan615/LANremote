package com.lanremote.bluetooth;
import java.util.*;
public final class TvRecoveryPolicyTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){check(TvRecoveryPolicy.secure(TvPlatform.LG)&&TvRecoveryPolicy.secure(TvPlatform.ANDROID)&&!TvRecoveryPolicy.secure(TvPlatform.ROKU));check(TvRecoveryPolicy.failure("TV connection failed: timeout")&&!TvRecoveryPolicy.failure("Pairing forgotten. Connect again.")&&!TvRecoveryPolicy.failure("TV needs pairing again. Unlock to configure."));check(TvRecoveryPolicy.unique(Arrays.asList("10.0.0.3","10.0.0.3"),"10.0.0.2").equals("10.0.0.3"));check(TvRecoveryPolicy.unique(Arrays.asList("10.0.0.2","10.0.0.3"),"10.0.0.2").isEmpty());check(TvRecoveryPolicy.unique(Collections.singletonList("10.0.0.2"),"10.0.0.2").isEmpty());System.out.println("TV recovery identity selection tests passed");}
}
