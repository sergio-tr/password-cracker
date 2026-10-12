package com.wifiauditlab.android.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wifiauditlab.android.R

/** Visual recognition / connection chip state for network list rows. */
enum class NetworkRecognitionStatus {
    /** Device is currently associated with this network. */
    Connected,

    /** Matched a saved Vault network (not necessarily connected). */
    Known,

    /** Not recognized as a saved network. */
    Unknown,
}

/**
 * Icon-only recognition indicator. Accessibility text is carried by [contentDescription]
 * (TalkBack), never as a visible label for Connected / Unknown.
 */
@Composable
fun NetworkRecognitionIndicator(
    status: NetworkRecognitionStatus,
    modifier: Modifier = Modifier,
) {
    when (status) {
        NetworkRecognitionStatus.Connected -> {
            Icon(
                imageVector = Icons.Filled.Wifi,
                contentDescription = stringResource(R.string.connection_status_connected),
                tint = MaterialTheme.colorScheme.primary,
                modifier = modifier.size(20.dp),
            )
        }
        NetworkRecognitionStatus.Unknown -> {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.connection_status_unknown),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.size(20.dp),
            )
        }
        NetworkRecognitionStatus.Known -> {
            // Known uses a text chip elsewhere (Saved); reserved for future icon-only use.
        }
    }
}
