package com.lanremote.bluetooth;
import android.content.*;
import android.security.keystore.*;
import java.io.File;
import java.security.KeyStore;
import java.util.*;
import javax.crypto.*;

final class SecurePrefs implements SharedPreferences {
 private static final String ALIAS="lanremote_credentials_v1";
 private static final Map<String,SecurePrefs> stores=new HashMap<>();
 private static SecretKey cachedKey;
 private final String name;private final SharedPreferences raw;
 private SecurePrefs(Context c,String name){this.name=name;raw=c.getSharedPreferences(name,Context.MODE_PRIVATE);}
 static synchronized SecurePrefs open(Context c,String name){SecurePrefs existing=stores.get(name);if(existing!=null)return existing;SecurePrefs result=new SecurePrefs(c.getApplicationContext(),name);stores.put(name,result);return result;}
 private static synchronized SecretKey key()throws Exception{
  if(cachedKey!=null)return cachedKey;KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
  if(!ks.containsAlias(ALIAS)){KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(false).build());g.generateKey();}
  cachedKey=(SecretKey)ks.getKey(ALIAS,null);if(cachedKey==null)throw new java.security.GeneralSecurityException("Credential key unavailable");return cachedKey;
 }
 private boolean secret(String field){return CredentialCipher.secret(name,field);}
 private String encrypt(String field,String value){try{return CredentialCipher.seal(key(),name,field,value);}catch(Exception e){throw new IllegalStateException("Unable to securely save pairing credentials",e);}}
 private String decrypt(String field,String value){try{return CredentialCipher.open(key(),name,field,value);}catch(Exception e){throw new IllegalStateException("Saved pairing credentials unavailable; explicit re-pairing may be required",e);}}
 private synchronized void migrate(){SharedPreferences.Editor edit=null;for(Map.Entry<String,?> e:raw.getAll().entrySet()){if(!secret(e.getKey())||!(e.getValue() instanceof String))continue;String value=(String)e.getValue();if(value.startsWith(CredentialCipher.PREFIX))continue;if(edit==null)edit=raw.edit();edit.putString(e.getKey(),encrypt(e.getKey(),value));}if(edit!=null&&!edit.commit())throw new IllegalStateException("Credential migration was not saved");}
 static void migrateExisting(Context c){Set<String> names=new HashSet<>(Arrays.asList("remote","lg_remote","samsung_remote","sony_remote","android_tv"));File dir=new File(c.getApplicationInfo().dataDir,"shared_prefs");String[] files=dir.list();if(files!=null)for(String file:files)if(file.matches("relay_permissions_[A-Za-z0-9_-]+\\.xml"))names.add(file.substring(0,file.length()-4));for(String name:names)try{open(c,name).migrate();}catch(RuntimeException e){android.util.Log.w("LANremote","Credential migration unavailable; protected values will not be used as plaintext.");}}
 @Override public synchronized String getString(String field,String fallback){String value=raw.getString(field,null);if(value==null)return fallback;if(!secret(field))return value;if(!value.startsWith(CredentialCipher.PREFIX)){try{migrate();value=raw.getString(field,null);}catch(RuntimeException e){return fallback;}}if(value==null)return fallback;try{return decrypt(field,value);}catch(RuntimeException e){return fallback;}}
 @Override public Map<String,?> getAll(){Map<String,Object> all=new HashMap<>(raw.getAll());for(String field:new ArrayList<>(all.keySet()))if(secret(field))all.put(field,getString(field,""));return all;}
 Set<String> fields(){return new HashSet<>(raw.getAll().keySet());}
 @Override public Set<String> getStringSet(String k,Set<String> d){return raw.getStringSet(k,d);}
 @Override public int getInt(String k,int d){return raw.getInt(k,d);}
 @Override public long getLong(String k,long d){return raw.getLong(k,d);}
 @Override public float getFloat(String k,float d){return raw.getFloat(k,d);}
 @Override public boolean getBoolean(String k,boolean d){return raw.getBoolean(k,d);}
 @Override public boolean contains(String k){return raw.contains(k);}
 @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){raw.registerOnSharedPreferenceChangeListener(l);}
 @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){raw.unregisterOnSharedPreferenceChangeListener(l);}
 @Override public Editor edit(){return new SafeEditor(raw.edit());}
 private final class SafeEditor implements Editor {
  private final Editor edit;SafeEditor(Editor e){edit=e;}
  @Override public Editor putString(String k,String v){if(v==null)edit.remove(k);else edit.putString(k,secret(k)?encrypt(k,v):v);return this;}
  private void plain(String k){if(secret(k))throw new IllegalArgumentException("Protected preference requires string");}
  @Override public Editor putStringSet(String k,Set<String> v){plain(k);edit.putStringSet(k,v);return this;}
  @Override public Editor putInt(String k,int v){plain(k);edit.putInt(k,v);return this;}
  @Override public Editor putLong(String k,long v){plain(k);edit.putLong(k,v);return this;}
  @Override public Editor putFloat(String k,float v){plain(k);edit.putFloat(k,v);return this;}
  @Override public Editor putBoolean(String k,boolean v){plain(k);edit.putBoolean(k,v);return this;}
  @Override public Editor remove(String k){edit.remove(k);return this;}
  @Override public Editor clear(){edit.clear();return this;}
  @Override public boolean commit(){synchronized(SecurePrefs.this){return edit.commit();}}
  @Override public void apply(){synchronized(SecurePrefs.this){edit.apply();}}
 }
}
