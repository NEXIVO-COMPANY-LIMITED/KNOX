# ====================================================================
# PAYO / KNOX MDM DPC — Phase 9.1 ProGuard & R8 Obfuscation Rules
# ====================================================================

# 1. Keep Device Admin Receiver (Required by Android OS for Device Owner)
-keep class com.example.payo.receiver.MyDeviceAdminReceiver { *; }
-keep class com.example.payo.receiver.MdmDeviceAdminReceiver { *; }
-keep class com.example.payo.receiver.BootReceiver { *; }
-keep class com.example.payo.service.DeviceMonitorService { *; }

# 2. Keep Samsung Knox SDK Classes & Interfaces
-keep class com.samsung.android.knox.** { *; }
-dontwarn com.samsung.android.knox.**

# 3. Obfuscate Internal Helper Classes and Constants
-repackageclasses 'a'
-allowaccessmodification

# 4. Strip Debug Logging in Release Builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# 5. Keep Retrofit & Gson Models
-keepattributes Signature, InnerClasses, *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
