package com.aryasubramani.vijibackup.downloadsaccess.presentation

import com.aryasubramani.vijibackup.auth.presentation.MainDispatcherRule
import com.aryasubramani.vijibackup.downloadsaccess.data.DownloadsSourceConfiguration
import com.aryasubramani.vijibackup.downloadsaccess.data.DownloadsSourceStore
import com.aryasubramani.vijibackup.downloadsaccess.domain.DownloadsAccessHealth
import com.aryasubramani.vijibackup.downloadsaccess.domain.DownloadsAccessManager
import com.aryasubramani.vijibackup.downloadsaccess.domain.DownloadsAccessPlatform
import com.aryasubramani.vijibackup.downloadsaccess.domain.DownloadsAccessProbe
import com.aryasubramani.vijibackup.downloadsaccess.domain.DownloadsAccessSnapshot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsAccessViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun activationRefreshesTheCurrentDownloadsHealth() = runTest {
        val configuration = DownloadsSourceConfiguration(configured = true, enabled = true)
        val viewModel = DownloadsAccessViewModel(
            manager = DownloadsAccessManager(
                store = FakeDownloadsSourceStore(configuration),
                accessProbe = FakeDownloadsAccessProbe(),
            ),
        )

        viewModel.activate()
        advanceUntilIdle()

        assertEquals(
            DownloadsAccessUiState(
                snapshot = DownloadsAccessSnapshot(
                    configuration = configuration,
                    health = DownloadsAccessHealth.Ready,
                ),
                isLoading = false,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun missingPermissionLaunchesOneSettingsRequestAndDenialDoesNotConfigureSource() = runTest {
        val store = FakeDownloadsSourceStore()
        val viewModel = createViewModel(
            store = store,
            probe = FakeDownloadsAccessProbe(accessGranted = false),
        )
        viewModel.activate()
        advanceUntilIdle()
        val launch = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.settingsLaunches.first()
        }

        viewModel.requestAccess()
        viewModel.requestAccess()
        runCurrent()

        assertEquals(DownloadsSettingsPurpose.ConfigureOrRepair, launch.await().purpose)
        assertTrue(viewModel.uiState.value.isAwaitingSettings)
        viewModel.onSettingsResult()
        advanceUntilIdle()

        assertEquals(DownloadsAccessHealth.NotConfigured, viewModel.uiState.value.snapshot?.health)
        assertEquals(DownloadsAccessNotice.PermissionNotGranted, viewModel.uiState.value.notice)
        assertFalse(viewModel.uiState.value.isAwaitingSettings)
        assertEquals(0, store.writeCount)
    }

    @Test
    fun existingUnusedGrantIsAdoptedWithoutOpeningSettings() = runTest {
        val store = FakeDownloadsSourceStore()
        val viewModel = createViewModel(store = store)
        viewModel.activate()
        advanceUntilIdle()

        viewModel.requestAccess()
        advanceUntilIdle()

        assertEquals(DownloadsAccessHealth.Ready, viewModel.uiState.value.snapshot?.health)
        assertEquals(
            DownloadsSourceConfiguration(configured = true, enabled = true),
            store.configuration,
        )
        assertEquals(1, store.writeCount)
        assertFalse(viewModel.uiState.value.isAwaitingSettings)
    }

    @Test
    fun settingsReturnRepairsAConfiguredSourceAfterPermissionIsGranted() = runTest {
        val store = FakeDownloadsSourceStore(
            DownloadsSourceConfiguration(configured = true, enabled = false),
        )
        val probe = FakeDownloadsAccessProbe(accessGranted = false)
        val viewModel = createViewModel(store, probe)
        viewModel.activate()
        advanceUntilIdle()
        val launch = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.settingsLaunches.first()
        }

        viewModel.requestAccess()
        runCurrent()
        assertEquals(DownloadsSettingsPurpose.ConfigureOrRepair, launch.await().purpose)
        probe.accessGranted = true
        viewModel.onSettingsResult()
        advanceUntilIdle()

        assertEquals(DownloadsAccessHealth.Disabled, viewModel.uiState.value.snapshot?.health)
        assertFalse(store.configuration.enabled)
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun enableDisableAndRemoveUseTheManagerAndExposeResidualAndroidGrant() = runTest {
        val store = FakeDownloadsSourceStore(
            DownloadsSourceConfiguration(configured = true, enabled = true),
        )
        val viewModel = createViewModel(store = store)
        viewModel.activate()
        advanceUntilIdle()

        viewModel.setEnabled(false)
        advanceUntilIdle()
        assertEquals(DownloadsAccessHealth.Disabled, viewModel.uiState.value.snapshot?.health)

        viewModel.setEnabled(true)
        advanceUntilIdle()
        assertEquals(DownloadsAccessHealth.Ready, viewModel.uiState.value.snapshot?.health)

        viewModel.remove()
        advanceUntilIdle()
        assertEquals(
            DownloadsAccessHealth.PermissionGrantedButUnused,
            viewModel.uiState.value.snapshot?.health,
        )
        assertEquals(DownloadsAccessNotice.SourceRemoved, viewModel.uiState.value.notice)
    }

    @Test
    fun permissionReviewRefreshesHealthWithoutChangingConfiguration() = runTest {
        val store = FakeDownloadsSourceStore(
            DownloadsSourceConfiguration(configured = true, enabled = true),
        )
        val probe = FakeDownloadsAccessProbe()
        val viewModel = createViewModel(store, probe)
        viewModel.activate()
        advanceUntilIdle()
        val launch = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.settingsLaunches.first()
        }

        viewModel.reviewPermission()
        runCurrent()
        assertEquals(DownloadsSettingsPurpose.ReviewPermission, launch.await().purpose)
        probe.accessGranted = false
        viewModel.onSettingsResult()
        advanceUntilIdle()

        assertEquals(DownloadsAccessHealth.NeedsPermission, viewModel.uiState.value.snapshot?.health)
        assertEquals(0, store.writeCount)
    }

    @Test
    fun failedSettingsLaunchAndDeactivationCannotMutateProtectedState() = runTest {
        val store = FakeDownloadsSourceStore()
        val probe = FakeDownloadsAccessProbe(accessGranted = false)
        val viewModel = createViewModel(store, probe)
        viewModel.activate()
        advanceUntilIdle()
        val launch = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.settingsLaunches.first()
        }

        viewModel.requestAccess()
        runCurrent()
        val request = launch.await()
        viewModel.onSettingsLaunchFailed(request.id)
        assertEquals(DownloadsAccessNotice.SettingsUnavailable, viewModel.uiState.value.notice)

        viewModel.requestAccess()
        runCurrent()
        viewModel.deactivate()
        probe.accessGranted = true
        viewModel.onSettingsResult()
        advanceUntilIdle()

        assertEquals(DownloadsAccessUiState(), viewModel.uiState.value)
        assertEquals(0, store.writeCount)
    }
}

private fun createViewModel(
    store: FakeDownloadsSourceStore = FakeDownloadsSourceStore(),
    probe: FakeDownloadsAccessProbe = FakeDownloadsAccessProbe(),
) = DownloadsAccessViewModel(DownloadsAccessManager(store, probe))

private class FakeDownloadsSourceStore(
    var configuration: DownloadsSourceConfiguration = DownloadsSourceConfiguration(),
) : DownloadsSourceStore {
    var writeCount = 0

    override suspend fun read(): DownloadsSourceConfiguration = configuration

    override suspend fun write(configuration: DownloadsSourceConfiguration) {
        writeCount += 1
        this.configuration = configuration
    }
}

private class FakeDownloadsAccessProbe(
    override val platform: DownloadsAccessPlatform = DownloadsAccessPlatform.AllFiles,
    var accessGranted: Boolean = true,
    var storageAvailable: Boolean = true,
    var rootReadable: Boolean = true,
) : DownloadsAccessProbe {
    override fun hasAccess(): Boolean = accessGranted

    override fun isPrimaryStorageAvailable(): Boolean = storageAvailable

    override fun isDownloadsRootReadable(): Boolean = rootReadable
}
