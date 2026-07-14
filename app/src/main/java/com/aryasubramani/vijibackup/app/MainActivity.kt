package com.aryasubramani.vijibackup.app

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.aryasubramani.vijibackup.auth.presentation.AuthUiState
import com.aryasubramani.vijibackup.auth.presentation.AuthViewModel
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerLaunch
import com.aryasubramani.vijibackup.folderaccess.domain.FolderPickerSelection
import com.aryasubramani.vijibackup.folderaccess.presentation.FolderAccessViewModel
import com.aryasubramani.vijibackup.folderaccess.saf.FolderPickerResult
import com.aryasubramani.vijibackup.folderaccess.saf.ReadOnlyFolderPickerRequest
import com.aryasubramani.vijibackup.folderaccess.saf.ReadOnlyOpenDocumentTreeContract
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var folderPickerRequestState: FolderPickerRequestState

    @VisibleForTesting
    internal var protectedWindowPolicyAppliedForTesting = false
        private set

    private val appContainer: AppContainer
        get() = (application as VijiBackupApplication).appContainer

    private val authViewModel by viewModels<AuthViewModel> {
        AuthViewModel.Factory(
            sessionManager = appContainer.authSessionManager,
            isGoogleSignInConfigured = appContainer.isGoogleSignInConfigured,
        )
    }
    private val folderAccessViewModel by viewModels<FolderAccessViewModel> {
        FolderAccessViewModel.Factory(appContainer.folderMappingRepository)
    }
    private val folderPickerLauncher = registerForActivityResult(
        ReadOnlyOpenDocumentTreeContract(),
    ) { result ->
        val requestToken = folderPickerRequestState.currentToken
            ?: return@registerForActivityResult
        lifecycleScope.launch {
            folderAccessViewModel.completePicker(
                requestToken = requestToken,
                selection = result.toDomainSelection(),
            )
            folderPickerRequestState.clearIfMatching(requestToken)
        }
    }
    private val credentialRequestDispatcher by lazy(LazyThreadSafetyMode.NONE) {
        AuthCredentialRequestDispatcher(
            coroutineScope = lifecycleScope,
            signIn = { mode ->
                appContainer.googleSignInClient.signIn(
                    activityContext = this@MainActivity,
                    mode = mode,
                )
            },
            onResult = authViewModel::onSignInResult,
            onInterrupted = authViewModel::onSignInInterrupted,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        folderPickerRequestState = FolderPickerRequestState.restore(savedInstanceState)
        super.onCreate(savedInstanceState)
        applyProtectedWindowPolicy()
        enableEdgeToEdge()
        setContent {
            val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
            val folderAccessUiState by folderAccessViewModel.uiState.collectAsStateWithLifecycle()
            val signInRequest = (uiState as? AuthUiState.SigningIn)?.request

            LaunchedEffect(uiState) {
                if (uiState is AuthUiState.ReauthenticationRequired) {
                    authViewModel.startAutomaticReauthentication()
                }
                if (uiState is AuthUiState.Approved) {
                    folderAccessViewModel.activate()
                } else {
                    folderAccessViewModel.deactivate()
                }
            }
            LaunchedEffect(signInRequest?.id) {
                val request = signInRequest ?: return@LaunchedEffect
                val mode = authViewModel.consumeSignInRequest(request.id)
                    ?: return@LaunchedEffect
                credentialRequestDispatcher.dispatch(request.id, mode)
            }
            LaunchedEffect(Unit) {
                folderAccessViewModel.pickerLaunches.collect { request ->
                    if (authViewModel.uiState.value is AuthUiState.Approved) {
                        launchFolderPicker(request)
                    }
                }
            }

            VijiBackupApp(
                uiState = uiState,
                folderAccessUiState = folderAccessUiState,
                onSignIn = authViewModel::signIn,
                onRetry = authViewModel::retry,
                onSignOut = authViewModel::signOut,
                onAddFolder = folderAccessViewModel::addFolder,
                onRepairFolder = folderAccessViewModel::repairFolder,
                onRemoveFolder = folderAccessViewModel::removeFolder,
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        folderPickerRequestState.saveTo(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        authViewModel.onAppForegrounded()
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            authViewModel.onAppBackgrounded()
        }
        super.onStop()
    }

    private fun launchFolderPicker(request: FolderPickerLaunch) {
        if (!folderPickerRequestState.stageForLaunch(request.requestToken)) return
        val initialUri = request.initialTreeUri?.let { rawUri ->
            try {
                Uri.parse(rawUri)
            } catch (_: Exception) {
                null
            }
        }
        folderPickerLauncher.launch(ReadOnlyFolderPickerRequest(initialUri = initialUri))
    }

    private fun applyProtectedWindowPolicy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        protectedWindowPolicyAppliedForTesting = true
    }

    private fun FolderPickerResult.toDomainSelection(): FolderPickerSelection = when (this) {
        FolderPickerResult.Cancelled -> FolderPickerSelection.Cancelled
        is FolderPickerResult.Selected -> FolderPickerSelection.Selected(
            treeUri = treeUri.toString(),
            grantedFlags = grantedFlags,
        )
    }
}
