package com.aryasubramani.vijibackup.drive.presentation

import com.aryasubramani.vijibackup.auth.domain.GoogleAccount
import com.aryasubramani.vijibackup.auth.presentation.MainDispatcherRule
import com.aryasubramani.vijibackup.drive.domain.DriveConnectionResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriveConnectionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun activationEmitsOneSilentProbeBoundToTheApprovedAccount() = runTest {
        val account = account("subject-a", "a@example.test")
        val viewModel = DriveConnectionViewModel()
        val request = async { viewModel.requests.first() }

        viewModel.activate(account)
        viewModel.activate(account)
        runCurrent()

        assertEquals(
            DriveConnectionRequest(
                id = 1L,
                account = account,
                mode = DriveConnectionRequestMode.SilentProbe,
            ),
            request.await(),
        )
        assertEquals(DriveConnectionHealth.Checking, viewModel.uiState.value.health)
        assertTrue(viewModel.uiState.value.isBusy)
        assertFalse(viewModel.uiState.value.isAwaitingAuthorization)
    }

    @Test
    fun silentProbeNeedingConsentDoesNotEnterAwaitingAuthorization() = runTest {
        val viewModel = DriveConnectionViewModel()
        val request = async { viewModel.requests.first() }
        viewModel.activate(account())
        runCurrent()

        viewModel.onResult(request.await().id, DriveConnectionResult.NeedsAuthorization)

        assertEquals(DriveConnectionHealth.NeedsAuthorization, viewModel.uiState.value.health)
        assertFalse(viewModel.uiState.value.isBusy)
        assertFalse(viewModel.uiState.value.isAwaitingAuthorization)
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun connectEmitsOneInteractiveRequestAndSuppressesDuplicateTaps() = runTest {
        val viewModel = DriveConnectionViewModel()
        val probe = async { viewModel.requests.first() }
        viewModel.activate(account())
        runCurrent()
        viewModel.onResult(probe.await().id, DriveConnectionResult.NeedsAuthorization)
        val connect = async { viewModel.requests.first() }

        viewModel.connect()
        viewModel.connect()
        runCurrent()

        assertEquals(DriveConnectionRequestMode.Interactive, connect.await().mode)
        assertTrue(viewModel.uiState.value.isBusy)
        assertFalse(viewModel.uiState.value.isAwaitingAuthorization)
    }

    @Test
    fun launchedResolutionIsCorrelatedAndCancellationReturnsToNeedsAuthorization() = runTest {
        val viewModel = DriveConnectionViewModel()
        val probe = async { viewModel.requests.first() }
        viewModel.activate(account())
        runCurrent()
        viewModel.onResult(probe.await().id, DriveConnectionResult.NeedsAuthorization)
        val connect = async { viewModel.requests.first() }
        viewModel.connect()
        runCurrent()
        val request = connect.await()

        assertTrue(viewModel.onAuthorizationResolutionLaunched(request.id))

        assertEquals(request, viewModel.pendingAuthorizationRequest())
        assertEquals(request.id, viewModel.pendingAuthorizationRequestId())
        assertEquals(DriveConnectionHealth.Checking, viewModel.uiState.value.health)
        assertTrue(viewModel.uiState.value.isAwaitingAuthorization)

        viewModel.onAuthorizationCancelled(request.id)

        assertEquals(DriveConnectionHealth.NeedsAuthorization, viewModel.uiState.value.health)
        assertEquals(
            DriveConnectionNotice.AuthorizationCancelled,
            viewModel.uiState.value.notice,
        )
        assertFalse(viewModel.uiState.value.isBusy)
        assertFalse(viewModel.uiState.value.isAwaitingAuthorization)
        assertNull(viewModel.pendingAuthorizationRequestId())
        assertNull(viewModel.pendingAuthorizationRequest())
    }

    @Test
    fun successfulDirectResultBecomesReady() = runTest {
        val viewModel = DriveConnectionViewModel()
        val probe = async { viewModel.requests.first() }
        viewModel.activate(account())
        runCurrent()

        viewModel.onResult(probe.await().id, DriveConnectionResult.Ready)

        assertEquals(DriveConnectionHealth.Ready, viewModel.uiState.value.health)
        assertFalse(viewModel.uiState.value.isBusy)
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun authorizationResultCanBeClaimedOnlyOnce() = runTest {
        val viewModel = DriveConnectionViewModel()
        val probe = async { viewModel.requests.first() }
        viewModel.activate(account())
        runCurrent()
        viewModel.onResult(probe.await().id, DriveConnectionResult.NeedsAuthorization)
        val connect = async { viewModel.requests.first() }
        viewModel.connect()
        runCurrent()
        val request = connect.await()
        assertTrue(viewModel.onAuthorizationResolutionLaunched(request.id))

        assertEquals(request, viewModel.claimAuthorizationResult(request.id))
        assertNull(viewModel.claimAuthorizationResult(request.id))
        assertNull(viewModel.pendingAuthorizationRequest())
        assertTrue(viewModel.uiState.value.isBusy)
        assertFalse(viewModel.uiState.value.isAwaitingAuthorization)
    }

    @Test
    fun refreshRetriesATemporaryFailureWithASilentProbe() = runTest {
        val viewModel = DriveConnectionViewModel()
        val first = async { viewModel.requests.first() }
        viewModel.activate(account())
        runCurrent()
        viewModel.onResult(first.await().id, DriveConnectionResult.TemporaryFailure)
        val retry = async { viewModel.requests.first() }

        viewModel.refresh()
        runCurrent()

        assertEquals(DriveConnectionRequestMode.SilentProbe, retry.await().mode)
        assertEquals(DriveConnectionHealth.Checking, viewModel.uiState.value.health)
        assertTrue(viewModel.uiState.value.isBusy)
    }

    @Test
    fun activatingAnotherAccountRetiresTheOldRequestAndIgnoresItsResult() = runTest {
        val firstAccount = account("subject-a", "a@example.test")
        val secondAccount = account("subject-b", "b@example.test")
        val viewModel = DriveConnectionViewModel()
        val first = async { viewModel.requests.first() }
        viewModel.activate(firstAccount)
        runCurrent()
        val firstRequest = first.await()
        val second = async { viewModel.requests.first() }

        viewModel.activate(secondAccount)
        runCurrent()
        val secondRequest = second.await()
        viewModel.onResult(firstRequest.id, DriveConnectionResult.Ready)

        assertEquals(secondAccount, secondRequest.account)
        assertEquals(DriveConnectionHealth.Checking, viewModel.uiState.value.health)

        viewModel.onResult(secondRequest.id, DriveConnectionResult.AccountMismatch)

        assertEquals(DriveConnectionHealth.AccountMismatch, viewModel.uiState.value.health)
    }

    @Test
    fun deactivationClearsStateAndRejectsLateAuthorizationCallback() = runTest {
        val viewModel = DriveConnectionViewModel()
        val request = async { viewModel.requests.first() }
        viewModel.activate(account())
        runCurrent()
        val requestId = request.await().id

        viewModel.deactivate()
        assertFalse(viewModel.onAuthorizationResolutionLaunched(requestId))
        viewModel.onResult(requestId, DriveConnectionResult.Ready)

        assertEquals(DriveConnectionUiState(), viewModel.uiState.value)
        assertNull(viewModel.pendingAuthorizationRequestId())
    }

    @Test
    fun launchFailureIsNonReadyAndRetryableByTheUser() = runTest {
        val viewModel = DriveConnectionViewModel()
        val probe = async { viewModel.requests.first() }
        viewModel.activate(account())
        runCurrent()
        viewModel.onResult(probe.await().id, DriveConnectionResult.NeedsAuthorization)
        val connect = async { viewModel.requests.first() }
        viewModel.connect()
        runCurrent()
        val requestId = connect.await().id

        viewModel.onAuthorizationLaunchFailed(requestId)

        assertEquals(DriveConnectionHealth.ProviderUnavailable, viewModel.uiState.value.health)
        assertEquals(
            DriveConnectionNotice.AuthorizationUnavailable,
            viewModel.uiState.value.notice,
        )
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun staleCancellationCannotOverwriteANewerReadyResult() = runTest {
        val viewModel = DriveConnectionViewModel()
        val first = async { viewModel.requests.first() }
        viewModel.activate(account())
        runCurrent()
        val firstRequest = first.await()
        viewModel.onResult(firstRequest.id, DriveConnectionResult.TemporaryFailure)
        val second = async { viewModel.requests.first() }
        viewModel.refresh()
        runCurrent()
        val secondRequest = second.await()
        viewModel.onResult(secondRequest.id, DriveConnectionResult.Ready)

        viewModel.onAuthorizationCancelled(firstRequest.id)

        assertEquals(DriveConnectionHealth.Ready, viewModel.uiState.value.health)
        assertNull(viewModel.uiState.value.notice)
    }
}

private fun account(
    subject: String = "subject",
    email: String = "approved@example.test",
) = requireNotNull(
    GoogleAccount.create(
        subject = subject,
        email = email,
        displayName = "Approved User",
    ),
)
