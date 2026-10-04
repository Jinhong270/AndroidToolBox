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
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jh270.toolbox.ui.RemoteFileManagerScreen
import com.jh270.toolbox.ui.SshConnectionScreen
import com.jh270.toolbox.ui.SshViewModel
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

                    if (uiState.isConnected) {
                        RemoteFileManagerScreen(
                            viewModel = sshViewModel,
                            uiState = uiState
                        )
                    } else {
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
