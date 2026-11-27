# Preserve readable application class names in diagnostics; R8 can still remove unused code and optimize methods.
-keepnames class com.lanremote.bluetooth.**
-keepattributes SourceFile,LineNumberTable
