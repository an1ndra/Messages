# The MMS PDU codec is reached over reflection by nothing, but the transaction
# engine loads workers and the persister by class name after a process restart.
-keep class com.anindra.messages.mms.** { *; }
