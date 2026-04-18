# Proguard rules for DeltaAim

# Keep Shizuku classes
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# Keep AIDL interfaces
-keep class com.deltaaim.shizuku.** { *; }

# Keep AccessibilityService
-keep class com.deltaaim.service.** { *; }
