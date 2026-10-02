package com.nexivo.payo.oem

object NoOpKnoxProvider : KnoxProvider {
    override val isAvailable: Boolean = false
    override fun applyOptionalHardening(): Boolean = false
    override fun clearOptionalHardening(): Boolean = false
}
