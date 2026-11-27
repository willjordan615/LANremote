package com.lanremote.bluetooth;
public final class LanRemoteApplication extends android.app.Application {
 @Override public void onCreate(){super.onCreate();SecurePrefs.migrateExisting(this);}
}
