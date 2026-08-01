package com.qiyue.cashflow.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppProfileTest {
    @Test
    fun profilesEnableOnlyExpectedModules() {
        assertTrue(AppProfile(AppMode.FULL).isEnabled(FeatureModule.CRYPTO))
        assertTrue(AppProfile(AppMode.SIMPLE).isEnabled(FeatureModule.BUDGET))
        assertFalse(AppProfile(AppMode.SIMPLE).isEnabled(FeatureModule.CRYPTO))
        val custom = AppProfile(AppMode.CUSTOM, setOf(FeatureModule.TEMPORARY_CASHFLOW))
        assertTrue(custom.isEnabled(FeatureModule.TEMPORARY_CASHFLOW))
        assertFalse(custom.isEnabled(FeatureModule.INSTALLMENT))
        assertEquals(custom, AppProfile.parse("CUSTOM", custom.modulesCsv()))
    }
}
