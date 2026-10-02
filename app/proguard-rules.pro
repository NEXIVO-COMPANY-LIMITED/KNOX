# ====================================================================
# Payo companion app — ProGuard & R8 Obfuscation Rules
# ====================================================================

# Manifest / system entry points
-keep class com.nexivo.payo.receiver.MyDeviceAdminReceiver { *; }
-keep class com.nexivo.payo.receiver.BootReceiver { *; }
-keep class com.nexivo.payo.service.DeviceMonitorService { *; }
-keep class com.nexivo.payo.service.PayoFirebaseMessagingService { *; }
-keep class com.nexivo.payo.ui.main.MainActivity { *; }
-keep class com.nexivo.payo.ui.lock.LockActivity { *; }
-keep class com.nexivo.payo.sync.CommandSyncWorker { *; }
-keep class com.nexivo.payo.financing.OfflineUnlockReportWorker { *; }
-keep class com.nexivo.payo.enrollment.EnrollmentRetryWorker { *; }

-repackageclasses 'a'
-allowaccessmodification

# Keep line info for crash reports (optional)
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, Exceptions

# ----- Retrofit / OkHttp / Gson -----
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.nexivo.payo.data.model.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ----- Room -----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ----- WorkManager -----
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}
-keepclassmembers class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# ----- Firebase Messaging -----
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ----- Kotlin / Coroutines -----
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Strip verbose debug logs in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
