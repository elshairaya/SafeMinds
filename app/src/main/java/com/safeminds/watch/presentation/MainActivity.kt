package com.safeminds.watch.presentation

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.safeminds.watch.presentation.theme.SafeMindsWatchTheme
import com.safeminds.watch.scheduler.HourlyWorkerScheduler

class MainActivity : ComponentActivity() {

    private var uiState by mutableStateOf("onboarding")

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            uiState = if (hasRequiredPermissions()) "granted" else "denied"
            // Automatic scheduling should run after permissions are granted.
            if (hasRequiredPermissions()) {
                HourlyWorkerScheduler.register(this)
            }

        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        uiState = if (hasRequiredPermissions()) "granted" else "onboarding"
        // Register automatic scheduling only when required permissions are already granted.
        if (hasRequiredPermissions()) {
            HourlyWorkerScheduler.register(this)
        }

        setContent {
            SafeMindsWatchTheme {
                WearApp(
                    uiState = uiState,
                    onGrantClick = { requestPermissions() },
                    onOpenSettings = { openSettings() },
                    heartRateGranted = isHeartRateGranted(),
                    activityGranted = isActivityGranted()
                )
            }
        }
    }

    private fun isHeartRateGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.BODY_SENSORS
            // old hr code
            // "android.permission.health.READ_HEART_RATE"
        ) == PackageManager.PERMISSION_GRANTED
    }
    private fun isActivityGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasRequiredPermissions(): Boolean {
        return isHeartRateGranted() && isActivityGranted()
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            android.Manifest.permission.ACTIVITY_RECOGNITION,
            android.Manifest.permission.BODY_SENSORS
            // old hr code
            //"android.permission.health.READ_HEART_RATE"
        )
        permissionLauncher.launch(permissions)
    }

    private fun openSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }
}

@Composable
fun WearApp(
    uiState: String,
    onGrantClick: () -> Unit,
    onOpenSettings: () -> Unit,
    heartRateGranted: Boolean,
    activityGranted: Boolean
) {
    AppScaffold {
        val listState = rememberTransformingLazyColumnState()
        val spec = rememberTransformationSpec()

        ScreenScaffold(scrollState = listState) { padding ->
            TransformingLazyColumn(
                state = listState,
                contentPadding = padding
            ) {
                item {
                    ListHeader(
                        modifier = Modifier.fillMaxWidth(),
                        transformation = SurfaceTransformation(spec)
                    ) {
                        Text("SafeMinds")
                    }
                }

                item {
                    PermissionScreen(
                        uiState = uiState,
                        onGrantClick = onGrantClick,
                        onOpenSettings = onOpenSettings,
                        heartRateGranted = heartRateGranted,
                        activityGranted = activityGranted
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionScreen(
    uiState: String,
    onGrantClick: () -> Unit,
    onOpenSettings: () -> Unit,
    heartRateGranted: Boolean,
    activityGranted: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (uiState) {
            "granted" -> {
                Text("Automatic monitoring enabled")
                Text("HR: ${if (heartRateGranted) "OK" else "Missing"}")
                Text("Activity: ${if (activityGranted) "OK" else "Missing"}")
            }
            "denied" -> {
                Text("Permission Missing")
                Text("HR: ${if (heartRateGranted) "OK" else "Missing"}")
                Text("Activity: ${if (activityGranted) "OK" else "Missing"}")
                Button(onClick = onGrantClick) { Text("Retry") }
                Button(onClick = onOpenSettings) { Text("Settings") }
            }

            else -> {
                Text("Welcome")
                Button(onClick = onGrantClick) { Text("Grant") }
            }
        }
    }
}
