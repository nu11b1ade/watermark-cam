# Release R8 rules for 水印相机.
# Compose, CameraX, ExifInterface, Lifecycle and Navigation ship their own consumer rules;
# the keeps below are belt-and-braces for things reached via reflection or the manifest.

# Keep line numbers for readable crash stack traces (mapping.txt is kept per release).
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# ViewModels are created reflectively by the default ViewModelProvider factory.
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class * extends androidx.lifecycle.AndroidViewModel { <init>(android.app.Application); }

# CameraX: camera2 implementation is discovered via CameraXConfig.Provider / metadata.
-keep class androidx.camera.camera2.Camera2Config$DefaultProvider { *; }
-dontwarn androidx.camera.**

# ExifInterface: tag tables are accessed by name.
-keep class androidx.exifinterface.media.ExifInterface { *; }

# JSON: only the platform org.json is used (field names are string literals), nothing to keep.
# Updates: FileProvider is manifest-referenced and kept by AAPT's generated rules. UpdateManifest
# uses explicit org.json access rather than reflection, so it needs no additional keep rules.

# Enums used by name (e.g. persisted WatermarkSize presets via valueOf/name).
-keepclassmembers enum com.nu11b1ade.watermarkcam.** { *; }

# AMap Search SDK reflection/obfuscation rules (no maps SDK is used).
-keep class com.amap.api.services.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.loc.** { *; }
-dontwarn com.amap.api.**
-dontwarn com.autonavi.**
-dontwarn com.loc.**
