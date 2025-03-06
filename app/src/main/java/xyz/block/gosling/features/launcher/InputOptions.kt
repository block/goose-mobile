package xyz.block.gosling.features.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A component that displays microphone and keyboard icons side by side.
 *
 * @param onMicrophoneClick Callback for when the microphone icon is clicked
 * @param onKeyboardClick Callback for when the keyboard icon is clicked
 */
@Composable
fun InputOptions(
    onMicrophoneClick: () -> Unit,
    onKeyboardClick: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 24.dp)
    ) {
        InputIcon(
            icon = Icons.Outlined.Mic,
            contentDescription = "Voice Input",
            onClick = onMicrophoneClick
        )

        Spacer(modifier = Modifier.width(100.dp))

        InputIcon(
            icon = Icons.Outlined.Keyboard,
            contentDescription = "Keyboard Input",
            onClick = onKeyboardClick
        )
    }
}

/**
 * A clean icon button without background.
 *
 * @param icon The icon to display
 * @param contentDescription Content description for accessibility
 * @param onClick Callback for when the icon is clicked
 */
@Composable
private fun InputIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(48.dp)
        )
    }
} 
