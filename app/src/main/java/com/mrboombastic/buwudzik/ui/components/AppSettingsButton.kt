package com.mrboombastic.buwudzik.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mrboombastic.buwudzik.R

/** Way out for a denied permission: after a denial, Android may no longer show its own prompt. */
@Composable
fun AppSettingsButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
            )
        },
        modifier = modifier
    ) {
        Text(stringResource(R.string.open_app_settings))
    }
}
