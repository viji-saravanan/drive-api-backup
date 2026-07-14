package com.aryasubramani.vijibackup.app

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderPickerRequestStateInstrumentedTest {
    @Test
    fun exactLaunchedTokenRoundTripsThroughActivityBundle() {
        val state = FolderPickerRequestState.restore(null)
        val bundle = Bundle()

        assertTrue(state.stageForLaunch("opaque-request-token"))
        state.saveTo(bundle)
        val restored = FolderPickerRequestState.restore(bundle)

        assertEquals("opaque-request-token", restored.currentToken)
    }

    @Test
    fun restoredTokenCannotBeOverwrittenByAnotherLaunchEvent() {
        val bundle = Bundle().apply {
            putString(FolderPickerRequestState.SAVED_TOKEN_KEY, "restored-token")
        }
        val state = FolderPickerRequestState.restore(bundle)

        assertFalse(state.stageForLaunch("replacement-token"))
        assertEquals("restored-token", state.currentToken)
    }

    @Test
    fun onlyMatchingCompletionClearsCurrentToken() {
        val state = FolderPickerRequestState.restore(null)
        assertTrue(state.stageForLaunch("current-token"))

        assertFalse(state.clearIfMatching("stale-token"))
        assertEquals("current-token", state.currentToken)
        assertTrue(state.clearIfMatching("current-token"))
        assertNull(state.currentToken)
    }

    @Test
    fun blankOrWrongTypeSavedValueFailsClosed() {
        val blank = Bundle().apply {
            putString(FolderPickerRequestState.SAVED_TOKEN_KEY, " ")
        }
        val wrongType = Bundle().apply {
            putLong(FolderPickerRequestState.SAVED_TOKEN_KEY, 42L)
        }

        assertNull(FolderPickerRequestState.restore(blank).currentToken)
        assertNull(FolderPickerRequestState.restore(wrongType).currentToken)
    }
}
