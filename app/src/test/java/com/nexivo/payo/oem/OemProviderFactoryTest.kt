package com.nexivo.payo.oem

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OemProviderFactoryTest {

    @Test
    fun nonSamsungManufacturerUsesNoOpProvider() {
        val samsung = object : KnoxProvider {
            override val isAvailable = true
            override fun applyOptionalHardening() = true
            override fun clearOptionalHardening() = true
        }

        val provider = OemProviderFactory.create("Google", samsung)

        assertSame(NoOpKnoxProvider, provider)
        assertTrue(provider.isAvailable.not())
    }

    @Test
    fun samsungManufacturerUsesSamsungProvider() {
        val samsung = object : KnoxProvider {
            override val isAvailable = true
            override fun applyOptionalHardening() = true
            override fun clearOptionalHardening() = true
        }

        assertSame(samsung, OemProviderFactory.create("samsung", samsung))
    }
}
