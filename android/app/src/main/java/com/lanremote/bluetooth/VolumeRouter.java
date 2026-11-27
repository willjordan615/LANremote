package com.lanremote.bluetooth;

import android.app.*;
import android.widget.*;
import java.util.*;

final class VolumeRouter {
 private final Activity activity;private final TvProfiles profiles;private final TvRemote primary;private final Runnable observer;
 private TvRemote secondary;private TvProfile secondaryProfile;private boolean active,syncing;
 VolumeRouter(Activity a,TvRemote primary,Runnable observer){activity=a;profiles=new TvProfiles(a);this.primary=primary;this.observer=observer;}
 boolean wantsBox(){return TvProfiles.BOX.equals(profiles.volume());}
 boolean follows(){return TvProfiles.AUTO.equals(profiles.volume());}
 boolean wantsPrimary(){return !primary.profileId().isEmpty()&&primary.profileId().equals(profiles.volume());}
 boolean routes(String action){return TvProfile.volumeAction(action)&&!follows();}
 String label(){String v=profiles.volume();if(TvProfiles.AUTO.equals(v))return "Follow selected device";if(TvProfiles.BOX.equals(v))return DeviceLabels.box(activity);TvProfile p=profiles.get(v);return p==null?"Follow selected device":p.label();}
 void choose(){List<TvProfile> list=profiles.all();ArrayList<String> labels=new ArrayList<>();labels.add("Follow selected device");labels.add(DeviceLabels.box(activity));for(TvProfile p:list)labels.add(p.label());String v=profiles.volume();int selected=TvProfiles.BOX.equals(v)?1:0;for(int i=0;i<list.size();i++)if(list.get(i).id.equals(v))selected=i+2;new AlertDialog.Builder(activity).setTitle("Volume and mute target").setSingleChoiceItems(labels.toArray(new String[0]),selected,(d,i)->{profiles.volume(i==0?TvProfiles.AUTO:i==1?TvProfiles.BOX:list.get(i-2).id);d.dismiss();observer.run();}).setNegativeButton("Cancel",null).show();}
 void start(){active=true;sync();}
 void sync(){if(!active||syncing)return;syncing=true;try{TvProfile wanted=profiles.get(profiles.volume());if(wanted!=null&&wanted.id.equals(primary.profileId()))wanted=null;
  if(secondary!=null&&(wanted==null||!wanted.id.equals(secondaryProfile.id)||!wanted.ip.equals(secondaryProfile.ip))){TvRemote old=secondary;secondary=null;secondaryProfile=null;old.setObserver(null);old.destroy();}
  if(wanted!=null&&secondary==null){secondaryProfile=wanted;secondary=new TvRemote(activity,new LinearLayout(activity),true,wanted);secondary.setObserver(observer);secondary.resumeForeground(true);}
 }finally{syncing=false;}}
 boolean ready(String action,boolean boxReady){if(wantsBox())return boxReady;TvRemote target=wantsPrimary()?primary:secondary;return target!=null&&target.ready("MUTE".equals(action))&&target.supports(action);}
 void send(String action,Runnable box){if(wantsBox()){box.run();return;}TvRemote target=wantsPrimary()?primary:secondary;if(target!=null&&target.ready("MUTE".equals(action))&&target.supports(action))target.remoteAction(action);}
 void networkChanged(){if(secondary!=null)secondary.networkChanged();}
 void stop(){active=false;if(secondary!=null){TvRemote old=secondary;secondary=null;secondaryProfile=null;old.setObserver(null);old.destroy();}}
}
