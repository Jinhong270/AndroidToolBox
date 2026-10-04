package com.jh270.toolbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
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
                    color = MaterialTheme.colorScheme.background
                ) {
                    val sshViewModel: SshViewModel = viewModel()
                    val uiState by sshViewModel.uiState.collectAsState()
                    var showAgreement by remember { mutableStateOf(true) }

                    if (showAgreement) {
                        UserAgreementDialog(
                            onAccept = { showAgreement = false },
                            onDismiss = { finish() }
                        )
                    }

                    when {
                        uiState.currentScreen == AppScreen.HOME -> {
                            ToolBoxHomeScreen(
                                onSelectSshFileManager = {
                                    sshViewModel.selectScreen(AppScreen.SSH_MANAGER)
                                }
                            )
                        }
                        uiState.showTerminalScreen -> {
                            SshTerminalScreen(
                                viewModel = sshViewModel,
                                uiState = uiState
                            )
                        }
                        uiState.isConnected -> {
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
