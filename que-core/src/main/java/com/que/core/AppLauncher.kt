package com.que.core

/**
 * Interface for launching applications.
 * Allows decoupling the action executor from platform-specific implementation.
 */
interface AppLauncher {
    /**
     * Launch an application by its name.
     * @param appName The name of the app to launch (e.g., "Instagram", "Settings").
     * @return true if launch was initiated specific to correct app, false otherwise.
     */
    fun launch(appName: String): Boolean
}
