# ProGuard / R8 rules for DeutschLern

# General optimization and attributes
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature,SourceFile,LineNumberTable

# Kotlinx Serialization
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class * implements kotlinx.serialization.internal.GeneratedSerializer {
    *;
}
-keepclassmembers class * {
    *** Companion;
    *** serializer(...);
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Gemini DTOs and Domain Models
-keep,allowobfuscation,allowoptimization class app.deutschlern.data.remote.dto.** { *; }
-keepclassmembers class app.deutschlern.data.remote.dto.** { *; }
-keep class app.deutschlern.domain.model.** { *; }
-keep class app.deutschlern.update.data.dto.** { *; }
-keepclassmembers class app.deutschlern.update.data.dto.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# Retrofit, OkHttp, Okio
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# Coroutines
-dontwarn kotlinx.coroutines.**
