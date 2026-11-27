package com.lanremote.bluetooth;
public class TextEditTest {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  TextEdit e=TextEdit.between("cat","car");check(e.deletes==1&&e.insert.equals("r"));
  e=TextEdit.between("ab","abc");check(e.deletes==0&&e.insert.equals("c"));
  e=TextEdit.between("a\uD83D\uDE00","a");check(e.deletes==1&&e.insert.isEmpty());
  check(TextEdit.between("same","same").empty());
  e=RelayWire.parseEdit(RelayWire.edit(new TextEdit(2,"hello")));check(e.deletes==2&&e.insert.equals("hello"));
  try{RelayWire.parseEdit(new byte[]{4,2,0});throw new AssertionError("oversize accepted");}catch(IllegalArgumentException expected){}
  System.out.println("Live text edit tests passed");
 }
}
