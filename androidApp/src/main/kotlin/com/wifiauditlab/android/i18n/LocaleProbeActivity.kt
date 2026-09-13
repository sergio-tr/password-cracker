package com.wifiauditlab.android.i18n

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.wifiauditlab.android.R

/**
 * Minimal [AppCompatActivity] for FIX-01A instrumentation: proves
 * [androidx.appcompat.app.AppCompatDelegate.setApplicationLocales] updates Compose
 * [stringResource] without app-level [android.app.Activity.recreate].
 *
 * Not a launcher activity — started only by instrumentation tests.
 */
class LocaleProbeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Text(
                text = stringResource(R.string.nav_nearby),
                modifier = Modifier.testTag(TAG_NAV_LABEL),
            )
        }
    }

    companion object {
        const val TAG_NAV_LABEL = "locale_probe_nav_label"
    }
}
