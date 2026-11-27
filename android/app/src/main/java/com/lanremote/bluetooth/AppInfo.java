package com.lanremote.bluetooth;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.view.View;
import android.widget.PopupMenu;
import java.security.MessageDigest;
import java.util.Locale;

// Read-only information; no additional permissions, requests, or background work.
final class AppInfo {
 private final Activity activity;
 AppInfo(Activity a){activity=a;}
 void menu(View anchor,Runnable update){
  PopupMenu menu=new PopupMenu(activity,anchor);
  menu.getMenu().add(0,1,0,"Disclosures");
  menu.getMenu().add(0,2,1,"Advanced information");
  menu.getMenu().add(0,3,2,"Check / update APK");
  menu.setOnMenuItemClickListener(item->{switch(item.getItemId()){case 1:disclosures();return true;case 2:advanced();return true;case 3:update.run();return true;default:return false;}});menu.show();
 }
 private void disclosures(){
  new AlertDialog.Builder(activity).setTitle("Disclosures").setMessage(
   "LANremote is an independently distributed, open-source remote. No account is required. Remote commands, device discovery and phone-to-phone sharing operate locally. The app contacts GitHub to check for and download updates; GitHub receives ordinary connection information, such as your IP address.\n\n"+
   "Bluetooth and network access are used for remote control; notification and connected-device service permissions support the Bluetooth remote. The install-package permission is used for requested APK updates. Android still requires your approval to install. Pairing credentials are stored locally using Android Keystore encryption; saved device names and settings remain app-private.\n\n"+
   "Some devices use unencrypted local protocols, including Roku, Sony BRAVIA IP and optional legacy LG control. Use these only on a trusted network. Device compatibility varies. If you enable a lock-screen remote session, anyone holding the phone can operate its visible controls.\n\n"+
   "Android may warn about independently distributed APKs. Keep Play Protect enabled. Do not install an APK if Android identifies it as harmful. LANremote has automated security checks but has not undergone an independent security audit.")
   .setPositiveButton("Close",null).setNeutralButton("Source / security", (d,w)->openSource()).show();
 }
 private void advanced(){
  String version="Unavailable",signing="Unavailable";
  try{PackageInfo p=activity.getPackageManager().getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);version=p.versionName+" (build "+p.getLongVersionCode()+")";if(p.signingInfo!=null){StringBuilder out=new StringBuilder();for(Signature sig:p.signingInfo.getApkContentsSigners()){if(out.length()>0)out.append('\n');for(byte b:MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()))out.append(String.format(Locale.ROOT,"%02X",b&255));}if(out.length()>0)signing=out.toString();}}catch(Exception ignored){}
  new AlertDialog.Builder(activity).setTitle("Advanced information").setMessage(
   "Version: "+version+"\nPackage: "+activity.getPackageName()+"\n\nInstalled signing certificate (SHA-256):\n"+signing+
   "\n\nUpdates are checked for package, version and signing identity. Published releases include APK SHA-256 checksums (a different value from the certificate fingerprint)."+
   "\n\nTV connections use local Wi-Fi; secure models verify saved TLS identities. Phone-to-phone sharing uses authenticated encryption. Device credentials are never displayed here. For protocol limitations, dependency details and security reports, see the project repository.")
   .setPositiveButton("Close",null).setNeutralButton("Source / security",(d,w)->openSource()).show();
 }
 private void openSource(){try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/willjordan615/LANremote")));}catch(ActivityNotFoundException e){android.widget.Toast.makeText(activity,"No browser available.",android.widget.Toast.LENGTH_SHORT).show();}}
}
