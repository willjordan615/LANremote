package com.lanremote.bluetooth;

final class TextEdit {
 final int deletes;final String insert;
 TextEdit(int deletes,String insert){if(deletes<0||deletes>500||insert.length()>500)throw new IllegalArgumentException("Text edit too large.");this.deletes=deletes;this.insert=insert;}
 static TextEdit between(String previous,String next){
  int i=0;while(i<previous.length()&&i<next.length()){int a=previous.codePointAt(i),b=next.codePointAt(i);if(a!=b)break;i+=Character.charCount(a);}
  return new TextEdit(previous.codePointCount(i,previous.length()),next.substring(i));
 }
 boolean empty(){return deletes==0&&insert.isEmpty();}
}
