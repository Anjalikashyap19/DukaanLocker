package com.iadv.dukaanlocker.ui.theme

/**
 * The user's theme preference, persisted in [com.iadv.dukaanlocker.LockerStorage].
 *
 * [SYSTEM] follows the device setting live (and on first launch it is the
 * default, so a fresh install matches the phone's Light/Dark mode). The other
 * two entries pin the app until the user picks something else.
 */
enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorageValue(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}
