package com.aryasubramani.vijibackup.app

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderPickerRequestStateInstrumentedTest {
    @Test
    fun exactLaunchIdentityRoundTripsThroughActivityBundle() {
        val state = FolderPickerRequestState.restore(null)
        val bundle = Bundle()

        val registration = checkNotNull(state.stageForLaunch("opaque-request-token"))
        state.saveTo(bundle)
        val restored = FolderPickerRequestState.restore(bundle)

        assertEquals("opaque-request-token", restored.currentToken)
        assertEquals(registration.registryKey, restored.currentRegistryKey)
        assertEquals(listOf(registration), restored.outstandingLaunches)
    }

    @Test
    fun activeLaunchCannotBeOverwrittenByAnotherLaunchEvent() {
        val state = FolderPickerRequestState.restore(null)
        val active = checkNotNull(state.stageForLaunch("active-token"))

        assertNull(state.stageForLaunch("replacement-token"))
        assertEquals("active-token", state.currentToken)
        assertEquals(active.registryKey, state.currentRegistryKey)
    }

    @Test
    fun retiredResultCannotConsumeOrClearReplacementLaunch() {
        val state = FolderPickerRequestState.restore(null)
        val retired = checkNotNull(state.stageForLaunch("retired-token"))

        assertTrue(state.retireCurrent())
        assertNull(state.currentToken)
        val replacement = checkNotNull(state.stageForLaunch("replacement-token"))

        assertNull(state.consumeResult(retired.registryKey))
        assertEquals("replacement-token", state.currentToken)
        assertEquals(replacement.registryKey, state.currentRegistryKey)
        assertEquals("replacement-token", state.consumeResult(replacement.registryKey))
        assertNull(state.currentToken)
        assertNull(state.currentRegistryKey)
    }

    @Test
    fun retiredAndReplacementLaunchesBothRoundTripWithoutChangingIdentity() {
        val state = FolderPickerRequestState.restore(null)
        val retired = checkNotNull(state.stageForLaunch("retired-token"))
        assertTrue(state.retireCurrent())
        val replacement = checkNotNull(state.stageForLaunch("replacement-token"))
        val bundle = Bundle()

        state.saveTo(bundle)
        val restored = FolderPickerRequestState.restore(bundle)

        assertEquals(listOf(retired, replacement), restored.outstandingLaunches)
        assertNull(restored.consumeResult(retired.registryKey))
        assertEquals("replacement-token", restored.currentToken)
        assertEquals(replacement.registryKey, restored.currentRegistryKey)
    }

    @Test
    fun unknownResultAndBlankTokenFailClosedWithoutChangingActiveLaunch() {
        val state = FolderPickerRequestState.restore(null)
        val active = checkNotNull(state.stageForLaunch("active-token"))

        assertNull(state.stageForLaunch(" "))
        assertNull(state.consumeResult("unknown-registry-key"))
        assertEquals("active-token", state.currentToken)
        assertEquals(active.registryKey, state.currentRegistryKey)
    }

    @Test
    fun malformedSavedLaunchCollectionsFailClosed() {
        val mismatched = Bundle().apply {
            putStringArrayList(
                FolderPickerRequestState.SAVED_REGISTRY_KEYS_KEY,
                arrayListOf("registry-key"),
            )
            putStringArrayList(
                FolderPickerRequestState.SAVED_REQUEST_TOKENS_KEY,
                arrayListOf(),
            )
        }
        val wrongType = Bundle().apply {
            putLong(FolderPickerRequestState.SAVED_REGISTRY_KEYS_KEY, 42L)
        }

        assertTrue(FolderPickerRequestState.restore(mismatched).outstandingLaunches.isEmpty())
        assertNull(FolderPickerRequestState.restore(wrongType).currentToken)
    }
}
