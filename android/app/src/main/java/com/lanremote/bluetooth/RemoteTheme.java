package com.lanremote.bluetooth;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.View;
import android.widget.*;
import android.util.TypedValue;

final class RemoteTheme {
 static final int BG=0xff171717, SURFACE=0xff2a2a2a, MUTED_SURFACE=0xff202020, BORDER=0xff414141,
  TEXT=0xffececec, MUTED=0xffb4b4b4, DISABLED=0xff858585, ACTIVE=0xffececec, ACTIVE_TEXT=0xff171717;
 static int dp(Context c,float n){return Math.round(c.getResources().getDisplayMetrics().density*n);}
 static GradientDrawable shape(Context c,int color,float radius,int border){
  GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));if(border!=0)d.setStroke(dp(c,1),border);return d;
 }
 static ColorStateList textColors(){return new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_selected},new int[]{}},new int[]{DISABLED,ACTIVE_TEXT,TEXT});}
 static void button(Button b){
  Context c=b.getContext();b.setStateListAnimator(null);b.setElevation(0);b.setTextColor(textColors());
  b.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
  StateListDrawable states=new StateListDrawable();
  states.addState(new int[]{-android.R.attr.state_enabled},shape(c,MUTED_SURFACE,12,0));
  states.addState(new int[]{android.R.attr.state_selected},shape(c,ACTIVE,12,0));
  states.addState(new int[]{},shape(c,SURFACE,12,0));
  RippleDrawable ripple=new RippleDrawable(ColorStateList.valueOf(0x40888888),states,shape(c,Color.WHITE,12,0));
  b.setBackground(new InsetDrawable(ripple,dp(c,3),dp(c,3),dp(c,3),dp(c,3)));
  b.setBackgroundTintList(null);b.setPadding(dp(c,6),0,dp(c,6),0);b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));
 }
 static void secondary(TextView v){v.setTextColor(MUTED);}
 static void setup(LinearLayout v){Context c=v.getContext();v.setBackground(shape(c,MUTED_SURFACE,12,BORDER));v.setPadding(dp(c,8),dp(c,6),dp(c,8),dp(c,6));}
}
