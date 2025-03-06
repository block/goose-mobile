package xyz.block.gosling.features.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.block.gosling.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A composable that displays the current time and date with an icon.
 * The icon color automatically adapts to light/dark theme.
 *
 * @param currentTime The current time formatted as a string
 */
@Composable
fun ClockWidget(currentTime: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(64.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.goose),
            contentDescription = "Goose",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentTime,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Current date
        Text(
            text = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date()),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Helper function to get the current time formatted as a string.
 * Returns time in "h:mm a" format (e.g., "3:30 PM").
 */
fun getCurrentTime(): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date())
} 
