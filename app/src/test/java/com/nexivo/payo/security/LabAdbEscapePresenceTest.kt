package com.nexivo.payo.security

import com.nexivo.payo.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Flavor-aware escape packaging checks.
 * labRelease/prod packaging absence also enforced by :app:verifyLabAdbEscapePackaging.
 */
class LabAdbEscapePresenceTest {

    private val isLabDebug: Boolean
        get() = BuildConfig.FLAVOR == "lab" && BuildConfig.BUILD_TYPE == "debug"

    @Test
    fun allowDoRelease_trueOnlyOnLabDebug() {
        if (isLabDebug) {
            assertTrue(BuildConfig.ALLOW_DO_RELEASE)
        } else {
            assertFalse(BuildConfig.ALLOW_DO_RELEASE)
        }
    }

    @Test
    fun labAdbEscapeReceiver_absentFromNonLabDebugClasspath() {
        assumeFalse(isLabDebug)
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.nexivo.payo.security.LabAdbEscapeReceiver")
        }
    }

    @Test
    fun labHardeningToggleReceiver_absentFromNonLabDebugClasspath() {
        assumeFalse(isLabDebug)
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.nexivo.payo.security.LabHardeningToggleReceiver")
        }
    }

    @Test
    fun labAdbEscapeReceiver_presentOnLabDebugClasspath() {
        assumeTrue(isLabDebug)
        Class.forName("com.nexivo.payo.security.LabAdbEscapeReceiver")
        Class.forName("com.nexivo.payo.security.LabHardeningToggleReceiver")
    }
}
