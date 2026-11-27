package com.lanremote.bluetooth;

import android.app.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;

final class LiveKeyboard {
 interface Sink {void edit(TextEdit edit);void enter();}
 private final Activity activity;
 private AlertDialog dialog;
 private boolean automatic;
 LiveKeyboard(Activity activity){this.activity=activity;}
 boolean showing(){return dialog!=null&&dialog.isShowing();}
 void close(){if(dialog!=null)dialog.dismiss();}
 void closeAutomatic(){if(automatic)close();}
 void show(String title,String note,Sink sink,Runnable compose,boolean auto){
  if(showing()||activity.isFinishing()||activity.isDestroyed())return;
  automatic=auto;
  EditText field=new EditText(activity);field.setSingleLine(true);field.setHint("Type search text");field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);field.setImeOptions(EditorInfo.IME_ACTION_SEARCH|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING|EditorInfo.IME_FLAG_NO_EXTRACT_UI);field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});field.setSaveEnabled(false);if(Build.VERSION.SDK_INT>=26)field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
  LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);int pad=RemoteTheme.dp(activity,16);content.setPadding(pad,0,pad,0);TextView tip=new TextView(activity);tip.setText(note+"\nLive edits are sent to the selected device. Use this for search, not passwords.");content.addView(tip);content.addView(field);
  Button back=new RemoteButton(activity);back.setText("Remote backspace");back.setAllCaps(false);content.addView(back);
  Handler main=new Handler(Looper.getMainLooper());
  final String[] sent={""};final boolean[] closed={false},failed={false};
  final Runnable flush=new Runnable(){public void run(){if(closed[0]||failed[0])return;if(BaseInputConnection.getComposingSpanStart(field.getText())>=0){main.postDelayed(this,120);return;}try{String next=field.getText().toString();TextEdit edit=TextEdit.between(sent[0],next);if(!edit.empty())sink.edit(edit);sent[0]=next;field.setError(null);}catch(RuntimeException e){failed[0]=true;field.setError("Live typing stopped: "+e.getMessage()+". Close and reopen to retry.");}}};
  field.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(Editable e){main.removeCallbacks(flush);if(!closed[0]&&!failed[0])main.postDelayed(flush,100);}});
  Runnable enter=()->{if(closed[0]||failed[0])return;main.removeCallbacks(flush);BaseInputConnection.removeComposingSpans(field.getText());flush.run();if(failed[0])return;try{sink.enter();close();}catch(RuntimeException e){field.setError(e.getMessage());}};
  back.setOnClickListener(v->{if(closed[0]||failed[0])return;main.removeCallbacks(flush);BaseInputConnection.removeComposingSpans(field.getText());flush.run();if(failed[0])return;try{String next=field.getText().toString();if(next.isEmpty()){sink.edit(new TextEdit(1,""));}else{int end=next.offsetByCodePoints(next.length(),-1);field.setText(next.substring(0,end));field.setSelection(field.length());main.removeCallbacks(flush);flush.run();}}catch(RuntimeException e){failed[0]=true;field.setError(e.getMessage());}});
  AlertDialog d=new AlertDialog.Builder(activity).setTitle(title).setView(content).setPositiveButton("Search / Enter",null).setNeutralButton("Compose instead",null).setNegativeButton("Close",null).create();dialog=d;
  d.setOnShowListener(v->{d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->enter.run());d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(w->{close();compose.run();});field.requestFocus();d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);});
  field.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_SEARCH){enter.run();return true;}return false;});
  d.setOnDismissListener(v->{closed[0]=true;main.removeCallbacks(flush);field.setText("");if(dialog==d){dialog=null;automatic=false;}});
  d.show();
 }
}
