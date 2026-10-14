package app.deutschlern.ui.navigation

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationExtensionsTest {

    @Test
    fun getNavigationLong_withLongValue_returnsLong() {
        val handle = SavedStateHandle(mapOf("dialogId" to 123L))
        assertEquals(123L, handle.getNavigationLong("dialogId"))
    }

    @Test
    fun getNavigationLong_withStringValue_returnsParsedLong() {
        val handle = SavedStateHandle(mapOf("dialogId" to "456"))
        assertEquals(456L, handle.getNavigationLong("dialogId"))
    }

    @Test
    fun getNavigationLong_withIntValue_returnsLong() {
        val handle = SavedStateHandle(mapOf("dialogId" to 789))
        assertEquals(789L, handle.getNavigationLong("dialogId"))
    }

    @Test
    fun getNavigationLong_withInvalidString_returnsDefault() {
        val handle = SavedStateHandle(mapOf("dialogId" to "invalid"))
        assertEquals(0L, handle.getNavigationLong("dialogId"))
        assertEquals(99L, handle.getNavigationLong("dialogId", defaultValue = 99L))
    }

    @Test
    fun getNavigationLong_withMissingKey_returnsDefault() {
        val handle = SavedStateHandle()
        assertEquals(0L, handle.getNavigationLong("missing"))
        assertEquals(-1L, handle.getNavigationLong("missing", defaultValue = -1L))
    }

    @Test
    fun getNavigationLong_withUnexpectedType_returnsDefault() {
        val handle = SavedStateHandle(mapOf("dialogId" to listOf(1, 2, 3)))
        assertEquals(0L, handle.getNavigationLong("dialogId"))
    }
}
