# libphonenumber is pure Java but its generated metadata + reflection-heavy
# country-code tables must survive release minification.
-keep class com.google.i18n.phonenumbers.** { *; }
-keep class com.google.i18n.phonenumbers.shortnumber.** { *; }
-keep class com.google.i18n.phonenumbers.metadata.** { *; }
-dontwarn com.google.i18n.phonenumbers.**
