# Keep all com.example application classes and members
-keep class com.example.** { *; }
-dontwarn com.example.**

# Keep Room generated classes
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Keep Moshi / JSON models
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}
