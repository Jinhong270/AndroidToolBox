package com.jh270.toolbox

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jh270.toolbox.data.AppSettings
import com.jh270.toolbox.ui.AppScreen
import com.jh270.toolbox.ui.RemoteFileManagerScreen
import com.jh270.toolbox.ui.SshConnectionScreen
import com.jh270.toolbox.ui.SshTerminalScreen
import com.jh270.toolbox.ui.SshViewModel
import com.jh270.toolbox.ui.ToolBoxHomeScreen
import com.jh270.toolbox.ui.UserAgreementDialog
import com.jh270.toolbox.ui.theme.ToolBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ToolBoxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val sshViewModel: SshViewModel = viewModel()
                    val uiState by sshViewModel.uiState.collectAsState()
                    val lifecycleOwner = LocalLifecycleOwner.current
                    val context = LocalContext.current
                    val notificationPermission = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { }
                    var showAgreement by remember {
                        mutableStateOf(!AppSettings.agreementAccepted(context))
                    }

                    LaunchedEffect(uiState.terminalSessionStarted) {
                        if (!uiState.terminalSessionStarted || Build.VERSION.SDK_INT < 33) return@LaunchedEffect
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    DisposableEffect(lifecycleOwner, sshViewModel) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                sshViewModel.onHostResume()
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    if (showAgreement) {
                        UserAgreementDialog(
                            onAccept = {
                                AppSettings.setAgreementAccepted(context)
                                showAgreement = false
                            }
                        ) {
                            finish()
                        }
                    }

                    val destination = when {
                        uiState.currentScreen == AppScreen.HOME -> "home"
                        uiState.showTerminalScreen -> "terminal"
                        uiState.isConnected -> "files"
                        else -> "connect"
                    }
                    AnimatedContent(
                        targetState = destination,
                        transitionSpec = {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                        },
                        label = "screen"
                    ) { dest ->
                        when (dest) {
                            "home" -> {
                                ToolBoxHomeScreen(
                                    terminalBackground = uiState.terminalBackground,
                                    terminalSelection = uiState.terminalSelection,
                                    onTerminalBackground = sshViewModel::setTerminalBackground,
                                    onTerminalSelection = sshViewModel::setTerminalSelection,
                                    onSelectSshFileManager = {
                                        sshViewModel.selectScreen(AppScreen.SSH_MANAGER)
                                    }
                                )
                            }
                            "terminal" -> {
                                SshTerminalScreen(
                                    viewModel = sshViewModel,
                                    uiState = uiState
                                )
                            }
                            "files" -> {
                                RemoteFileManagerScreen(
                                    viewModel = sshViewModel,
                                    uiState = uiState
                                )
                            }
                            else -> {
                                SshConnectionScreen(
                                    viewModel = sshViewModel,
                                    uiState = uiState
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
