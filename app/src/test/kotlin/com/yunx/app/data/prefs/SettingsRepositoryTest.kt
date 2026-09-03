package com.yunx.app.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsRepositoryTest {
    @Test
    fun `normalizes GitHub repository and rejects other hosts`() {
        assertEquals("https://github.com/example/jiexi", SettingsRepository.normalizeGitHubRepository("https://github.com/example/jiexi.git/"))
        assertThrows(IllegalArgumentException::class.java) {
            SettingsRepository.normalizeGitHubRepository("https://example.com/repo")
        }
    }
}
