package xyz.block.gosling.features.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import xyz.block.gosling.ChatMessage
import xyz.block.gosling.GoslingUI
import xyz.block.gosling.features.settings.SettingsStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    settingsStore: SettingsStore,
    onNavigateToSettings: () -> Unit,
    isAccessibilityEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply {
            addAll(activity.loadMessages())
        }
    }

    // Effect to save messages when they change
    LaunchedEffect(messages.size) {
        activity.saveMessages(messages)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    BadgedBox(
                        badge = {
                            if (!isAccessibilityEnabled) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    modifier = modifier.absoluteOffset((-6).dp, 0.dp)

                                ) {
                                    Text("!")
                                }
                            }
                        }
                    ) {
                        IconButton(
                            onClick = onNavigateToSettings
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main UI content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                GoslingUI(
                    context = LocalContext.current,
                    messages = messages,
                    onMessageAdded = { messages.add(it) },
                    onMessageRemoved = { messages.remove(it) }
                )
            }
        }
    }
} 
