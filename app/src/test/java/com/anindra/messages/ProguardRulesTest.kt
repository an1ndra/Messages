package com.anindra.messages

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R8 minify strips WorkManager's generated Room database unless it is kept,
 * which crashed the release build at startup with
 * `NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []`.
 */
class ProguardRulesTest {

    private val rules: String by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "app/proguard-rules.pro") }
            .firstOrNull { it.isFile }
            ?.readText() ?: error("app/proguard-rules.pro not found")
    }

    @Test
    fun workManagerRoomDatabaseConstructorIsKept() {
        assertTrue(
            "minified builds crash at startup without the RoomDatabase constructor keep",
            rules.contains("-keep class * extends androidx.room.RoomDatabase { <init>(); }")
        )
    }

    @Test
    fun theConcreteWorkDatabaseIsKept() {
        assertTrue(
            "androidx.work.impl.WorkDatabase_Impl is loaded reflectively and must be kept",
            rules.contains("androidx.work.impl.WorkDatabase_Impl")
        )
    }
}
