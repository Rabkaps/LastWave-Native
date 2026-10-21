# Wear OS rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# Keep Wearable Listener Services
-keep class * extends com.google.android.gms.wearable.WearableListenerService { <init>(...); }

# Keep Media3 Session Service
-keep class * extends androidx.media3.session.MediaSessionService { <init>(...); }
