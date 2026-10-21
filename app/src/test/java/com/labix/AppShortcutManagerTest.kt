package com.labix

import com.labix.navirom.shortcut.AppShortcutManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AppShortcutManagerTest {

    @Test
    fun shortcutConstants_areValid() {
        assertEquals("shortcut_continue_last_played", AppShortcutManager.ID_CONTINUE)
        assertEquals("shortcut_start_random_server", AppShortcutManager.ID_RANDOM_SERVER)
        assertEquals("shortcut_start_random_local", AppShortcutManager.ID_RANDOM_LOCAL)
        assertEquals("shortcut_connect_last_server", AppShortcutManager.ID_CONNECT_SERVER)

        assertEquals("com.labix.ACTION_CONTINUE_LAST_PLAYED", AppShortcutManager.ACTION_CONTINUE_LAST_PLAYED)
        assertEquals("com.labix.ACTION_START_RANDOM_SERVER", AppShortcutManager.ACTION_START_RANDOM_SERVER)
        assertEquals("com.labix.ACTION_START_RANDOM_LOCAL", AppShortcutManager.ACTION_START_RANDOM_LOCAL)
        assertEquals("com.labix.ACTION_CONNECT_LAST_SERVER", AppShortcutManager.ACTION_CONNECT_LAST_SERVER)
    }
}
