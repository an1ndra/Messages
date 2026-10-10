# libphonenumber is pure Java but its generated metadata + reflection-heavy
# country-code tables must survive release minification.
-keep class com.google.i18n.phonenumbers.** { *; }
-keep class com.google.i18n.phonenumbers.shortnumber.** { *; }
-keep class com.google.i18n.phonenumbers.metadata.** { *; }
-dontwarn com.google.i18n.phonenumbers.**

# Required for minify: WorkManager opens its Room database reflectively (loads
# WorkDatabase_Impl by name and calls its no-arg constructor). Without this R8
# strips it and the release build crashes at startup.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
