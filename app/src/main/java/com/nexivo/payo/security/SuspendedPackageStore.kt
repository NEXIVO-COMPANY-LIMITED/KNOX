package com.nexivo.payo.security

import android.content.Context

/** Persists packages we suspended so clear/DUMP survive process death. */
object SuspendedPackageStore {
    private const val PREFS = "payo_suspended_packages"
    private const val KEY = "packages"

    fun save(context: Context, packages: Collection<String>) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY, packages.toSet())
            .commit()
    }

    fun load(context: Context): Set<String> =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())
            ?.toSet()
            ?: emptySet()

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .commit()
    }
}
