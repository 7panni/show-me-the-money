package com.qiyue.cashflow.shared

enum class AppMode { SIMPLE, CUSTOM, FULL }

enum class FeatureModule {
    BUDGET,
    TEMPORARY_CASHFLOW,
    TRANSFER,
    INSTALLMENT,
    PAYPAL_USD,
    CRYPTO,
}

data class AppProfile(
    val mode: AppMode,
    val customModules: Set<FeatureModule> = emptySet(),
) {
    val enabledModules: Set<FeatureModule>
        get() = when (mode) {
            AppMode.SIMPLE -> SIMPLE_MODULES
            AppMode.CUSTOM -> customModules
            AppMode.FULL -> FeatureModule.entries.toSet()
        }

    fun isEnabled(module: FeatureModule): Boolean = module in enabledModules

    fun modulesCsv(): String = customModules.sortedBy { it.ordinal }.joinToString(",") { it.name }

    companion object {
        val SIMPLE_MODULES = setOf(FeatureModule.BUDGET)

        fun parse(mode: String, modulesCsv: String): AppProfile {
            val parsedMode = runCatching { AppMode.valueOf(mode) }.getOrDefault(AppMode.SIMPLE)
            val modules = modulesCsv.split(',')
                .mapNotNull { raw -> runCatching { FeatureModule.valueOf(raw.trim()) }.getOrNull() }
                .toSet()
            return AppProfile(parsedMode, modules)
        }
    }
}
