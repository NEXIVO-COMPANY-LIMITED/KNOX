package com.nexivo.payo.security

import com.nexivo.payo.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Prod/labRelease: ALLOW_DO_RELEASE=false. labDebug: true (escape hatch).
 */
class LabDeviceOwnerReleaseTest {

    @Test
    fun allowDoRelease_matchesChannelBuildType() {
        val labDebug = BuildConfig.FLAVOR == "lab" && BuildConfig.BUILD_TYPE == "debug"
        assertEquals(labDebug, BuildConfig.ALLOW_DO_RELEASE)
        assertEquals(labDebug, LabDeviceOwnerRelease.isEnabled())
    }

    @Test
    fun releaseWhenDisabled_failsClosed() {
        assumeTrue(!BuildConfig.ALLOW_DO_RELEASE)
        val calls = mutableListOf<String>()

        val result = LabDeviceOwnerRelease.performRelease(
            allowRelease = false,
            isDeviceOwner = true,
            transitionReleased = { calls += "released"; true },
            clearRestrictions = { calls += "restrictions" },
            clearDeviceOwner = { calls += "owner"; true },
            audit = { calls += "audit" }
        )

        assertTrue(result.isFailure)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun releasePath_runsSafetySequenceInOrder() {
        val calls = mutableListOf<String>()

        val result = LabDeviceOwnerRelease.performRelease(
            allowRelease = true,
            isDeviceOwner = true,
            transitionReleased = { calls += "released"; true },
            clearRestrictions = { calls += "restrictions" },
            clearDeviceOwner = { calls += "owner"; true },
            audit = { calls += "audit" }
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("released", "restrictions", "owner", "audit"), calls)
    }
}
