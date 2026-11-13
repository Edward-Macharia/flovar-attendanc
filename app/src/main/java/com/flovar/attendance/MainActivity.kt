package com.flovar.attendance

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private val Blue = Color(0xFF0B5CAD)
private val Green = Color(0xFF1B8A3E)
private val Red = Color(0xFFC62828)

class MainActivity : FragmentActivity() {
    private val vm: AttendanceViewModel by viewModels()
    private var unlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF7FB2F0))
            else lightColorScheme(primary = Blue)
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) { Box(Modifier.systemBarsPadding()) { Root() } }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (vm.hasSession && !unlocked) authenticate()
    }

    override fun onStop() {
        super.onStop()
        unlocked = false   // re-lock whenever the app leaves the foreground
    }

    private fun authenticate() {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Flovar Attendance")
            .setSubtitle("Confirm it's you")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                unlocked = true
                vm.refresh()
            }
        }).authenticate(info)
    }

    @Composable
    private fun Root() {
        when {
            !vm.signedIn -> LoginScreen()
            !unlocked -> LockedScreen()
            else -> HomeScreen()
        }
    }

    @Composable
    private fun LockedScreen() {
        Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("Locked", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { authenticate() }) { Text("Unlock") }
        }
    }

    @Composable
    private fun LoginScreen() {
        var user by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
            Arrangement.Center
        ) {
            Text("Flovar Services", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Employee attendance", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = user, onValueChange = { user = it }, label = { Text("Work email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = pass, onValueChange = { pass = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { unlocked = true; vm.login(user, pass); pass = "" },
                enabled = !vm.busy && user.isNotBlank() && pass.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (vm.busy) "Signing in…" else "Sign in") }
            vm.message?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    @Composable
    private fun HomeScreen() {
        var pending by remember { mutableStateOf<String?>(null) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true) pending?.let { vm.punch(it) }
            else vm.report("Precise location permission is required to record attendance.", true)
            pending = null
        }
        fun go(type: String) {
            val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (ok) vm.punch(type) else {
                pending = type
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
        val t = vm.today
        val statusText = when (vm.lastType) { "IN" -> "Clocked in"; "OUT" -> "Clocked out"; else -> "Not clocked in today" }
        val statusColor = when (vm.lastType) { "IN" -> Green; "OUT" -> Red; else -> MaterialTheme.colorScheme.onSurfaceVariant }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(t?.name?.ifBlank { null }?.let { "Hello, $it" } ?: "Attendance",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Status", style = MaterialTheme.typography.labelLarge)
                    Text(statusText, style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold, color = statusColor)
                    t?.site?.let { Text("Site: $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { go("IN") }, enabled = !vm.busy && vm.lastType != "IN",
                    colors = ButtonDefaults.buttonColors(containerColor = Green),
                    modifier = Modifier.weight(1f).height(64.dp)
                ) { Text("Clock In", style = MaterialTheme.typography.titleMedium) }
                Button(
                    onClick = { go("OUT") }, enabled = !vm.busy && vm.lastType == "IN",
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                    modifier = Modifier.weight(1f).height(64.dp)
                ) { Text("Clock Out", style = MaterialTheme.typography.titleMedium) }
            }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.message?.let {
                Text(it, color = if (vm.messageIsError) MaterialTheme.colorScheme.error else Green,
                    fontWeight = FontWeight.Medium)
            }
            Text("Your precise location is recorded with every clock in/out. GPS accuracy of ${Config.MAX_ACCURACY_M.toInt()} m or better is required.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider()
            Text("Today", style = MaterialTheme.typography.titleMedium)
            if (t?.logs.isNullOrEmpty()) Text("No punches yet.", style = MaterialTheme.typography.bodyMedium)
            t?.logs?.forEach {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(if (it.type == "IN") "Clock In" else "Clock Out")
                    Text(it.time, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.refresh() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Refresh") }
            OutlinedButton(
                onClick = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.BASE_URL))) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open ERP in browser") }
            TextButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
        }
    }
}
