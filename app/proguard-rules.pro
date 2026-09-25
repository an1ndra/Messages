# libphonenumber is pure Java but its generated metadata + reflection-heavy
# country-code tables must survive release minification.
-keep class com.google.i18n.phonenumbers.** { *; }
-keep class com.google.i18n.phonenumbers.shortnumber.** { *; }
-keep class com.google.i18n.phonenumbers.metadata.** { *; }
-dontwarn com.google.i18n.phonenumbers.**

# Vendored AOSP MMS stack (android-smsmms): the PDU/SMIL parsers are reached
# through generic types and XML pull-parser hooks, so keep them intact.
-keep class com.google.android.mms.pdu_alt.** { *; }
-keep class com.google.android.mms.smil.** { *; }
-keep class com.android.mms.dom.smil.** { *; }
-keep class com.android.mms.util.** { *; }
-keep class com.android.mms.util_alt.** { *; }
-keep class com.klinker.android.send_message.** { *; }
-dontwarn com.google.android.mms.**
-dontwarn com.android.mms.**
-dontwarn com.klinker.android.send_message.**
-dontwarn org.apache.http.**
