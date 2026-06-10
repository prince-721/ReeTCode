# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified

# Room - keep entity and DAO classes
-keep class com.reeltracker.data.entities.** { *; }
-keep class com.reeltracker.data.dao.** { *; }
-keep class com.reeltracker.data.database.** { *; }

# Keep Accessibility Service
-keep class com.reeltracker.service.ReelAccessibilityService { *; }

# Keep BroadcastReceivers
-keep class com.reeltracker.receiver.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# DataStore
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
