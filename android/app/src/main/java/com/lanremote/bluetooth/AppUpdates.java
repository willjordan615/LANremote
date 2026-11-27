package com.lanremote.bluetooth;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.TextView;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

final class AppUpdates {
 private static final String API="https://api.github.com/repos/willjordan615/LANremote/releases/latest";
 private static final String PREFIX="https://github.com/willjordan615/LANremote/releases/download/";
 private final Activity activity;
 private final TextView status;
 private final DownloadManager downloads;
 private final android.content.SharedPreferences prefs;
 private boolean checking;
 AppUpdates(Activity a,TextView view){activity=a;status=view;downloads=a.getSystemService(DownloadManager.class);prefs=a.getSharedPreferences("updates",Context.MODE_PRIVATE);}
 private void show(String s){status.setText(s);}
 private long version() throws PackageManager.NameNotFoundException{return activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).getLongVersionCode();}
 private boolean alive(){return !activity.isFinishing()&&!activity.isDestroyed();}
 void check(boolean manual){
  if(checking)return;
  try{cleanupObsolete(version());}catch(Exception ignored){}
  if(prefs.getLong("download",-1)>=0){try{if(prefs.getLong("version",-1)<=version())clear(prefs.getLong("download",-1));}catch(Exception e){show("Update state: "+e.getMessage());return;}if(prefs.getLong("download",-1)>=0){if(manual)installOrStatus();else show("Update download saved. Tap Check / install update.");return;}}
  if(!manual){try{long installed=version();long now=System.currentTimeMillis();long last=prefs.getLong("auto_check_time",0);if(prefs.getLong("auto_check_build",-1)==installed&&now>=last&&now-last<IdlePolicy.UPDATE_INTERVAL){show(prefs.getString("auto_check_status","Automatic update check recently completed."));return;}prefs.edit().putLong("auto_check_time",now).putLong("auto_check_build",installed).putString("auto_check_status","Automatic update check recently attempted.").apply();}catch(Exception e){show("Update state: "+e.getMessage());return;}}
  checking=true;show("Checking GitHub for updates…");
  new Thread(()->{
   HttpURLConnection c=null;
   try{
    c=(HttpURLConnection)new URL(API).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("User-Agent","LANRemote-Android");
    if(c.getResponseCode()!=200)throw new IOException("GitHub HTTP "+c.getResponseCode());
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>512000)throw new IOException("Release metadata too large");bytes.write(b,0,n);}}
    JSONObject release=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
    Matcher m=Pattern.compile("(?m)^LANREMOTE_VERSION_CODE: ([0-9]+)$").matcher(release.optString("body").replace("\r",""));
    if(!m.find())throw new IOException("Latest release does not include update version metadata");
    long next=Long.parseLong(m.group(1));if(!Pattern.compile("(?m)^LANREMOTE_SIGNING: stable$").matcher(release.optString("body").replace("\r","")).find()||release.optBoolean("draft",false)||release.optBoolean("prerelease",false))throw new IOException("Only stable signed releases are supported");
    String url=null;JSONArray assets=release.getJSONArray("assets");for(int i=0;i<assets.length();i++){JSONObject a=assets.getJSONObject(i);if(a.optString("name").equals("LANRemote-Bluetooth.apk")&&a.optLong("size",0)>0&&a.optLong("size",0)<=10_000_000)url=a.optString("browser_download_url");}
    if(url==null||!url.startsWith(PREFIX)||!url.endsWith("/LANRemote-Bluetooth.apk"))throw new IOException("No trusted APK asset found");
    final String asset=url;final long installed=version();
    activity.runOnUiThread(()->{checking=false;if(!alive())return;prefs.edit().putLong("auto_check_time",System.currentTimeMillis()).putLong("auto_check_build",installed).putString("auto_check_status",next<=installed?"App is up to date.":"Update available (build "+next+").").apply();if(next<=installed){show("App is up to date.");return;}show("Update available (build "+next+").");if(activity.hasWindowFocus()||manual)new AlertDialog.Builder(activity).setTitle("LAN Remote update available").setMessage("Download the new version from our GitHub release? Android will ask you to approve installation.").setPositiveButton("Download",(d,w)->download(asset,next)).setNegativeButton("Later",null).show();});
   }catch(Exception e){activity.runOnUiThread(()->{checking=false;if(alive())show("Update check: "+e.getMessage());});}finally{if(c!=null)c.disconnect();}
  },"lanremote-update-check").start();
 }
 private void cleanupObsolete(long installed){File dir=activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);if(dir==null)return;File[] files=dir.listFiles();if(files==null)return;long pending=prefs.getLong("version",-1);for(File file:files){Matcher m=Pattern.compile("LANRemote-([0-9]+)\\.apk").matcher(file.getName());if(!file.isFile()||!m.matches())continue;try{long code=Long.parseLong(m.group(1));if(code<=installed&&code!=pending)file.delete();}catch(NumberFormatException ignored){}}}
 private File apk(long code){File dir=activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);if(dir==null)throw new IllegalStateException("Download storage unavailable");return new File(dir,"LANRemote-"+code+".apk");}
 private void download(String url,long code){
  try{File f=apk(code);if(f.exists()&&!f.delete())throw new IOException("Cannot replace old download");
   DownloadManager.Request request=new DownloadManager.Request(Uri.parse(url)).setTitle("LAN Remote update").setDescription("Bluetooth remote APK").setMimeType("application/vnd.android.package-archive").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalFilesDir(activity,Environment.DIRECTORY_DOWNLOADS,f.getName());
   long id=downloads.enqueue(request);prefs.edit().putLong("download",id).putLong("version",code).apply();show("Downloading update. Tap Check / install update after download finishes.");
  }catch(Exception e){show("Download failed: "+e.getMessage());}
 }
 private void clear(long id){downloads.remove(id);prefs.edit().remove("download").remove("version").apply();try{cleanupObsolete(version());}catch(Exception ignored){}}
 private void installOrStatus(){
  long id=prefs.getLong("download",-1),code=prefs.getLong("version",-1);
  if(id<0){check(true);return;}
  try(Cursor cursor=downloads.query(new DownloadManager.Query().setFilterById(id))){
   if(cursor==null||!cursor.moveToFirst()){clear(id);show("Download missing. Tap Check to retry.");return;}
   int state=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
   if(state==DownloadManager.STATUS_FAILED){clear(id);show("Download failed. Tap Check to retry.");return;}
   if(state!=DownloadManager.STATUS_SUCCESSFUL){show("Download is still running. Tap again after completion.");return;}
   PackageManager pm=activity.getPackageManager();PackageInfo candidate=pm.getPackageArchiveInfo(apk(code).getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
   PackageInfo installed=pm.getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
   if(candidate==null||!activity.getPackageName().equals(candidate.packageName)||candidate.getLongVersionCode()!=code||code<=installed.getLongVersionCode()){clear(id);show("Downloaded APK package or version did not match; discarded.");return;}
   if(candidate.signingInfo==null||installed.signingInfo==null||!sameSigners(candidate.signingInfo.getApkContentsSigners(),installed.signingInfo.getApkContentsSigners())){
    clear(id);show("Signing key mismatch. Download discarded. Do not install an update with a different signing identity; report this to the developer.");return;}
   if(!pm.canRequestPackageInstalls()){
    new AlertDialog.Builder(activity).setTitle("Allow update installation?").setMessage("Android requires permission for this app to request package installation. If you allow it, return here and tap Check / install update again.").setPositiveButton("Open settings",(d,w)->activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())))).setNegativeButton("Cancel",null).show();return;
   }
   Uri uri=downloads.getUriForDownloadedFile(id);if(uri==null)throw new IOException("Downloaded file unavailable");
   Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
   activity.startActivity(intent);show("Confirm installation in Android. If canceled, tap Check / install update to retry.");
  }catch(Exception e){show("Install failed: "+e.getMessage());}
 }
 private static boolean sameSigners(Signature[] a,Signature[] b){return a!=null&&b!=null&&a.length>0&&new HashSet<>(Arrays.asList(a)).equals(new HashSet<>(Arrays.asList(b)));}
}
