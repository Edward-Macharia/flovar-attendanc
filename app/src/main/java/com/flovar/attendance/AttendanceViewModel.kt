package com.flovar.attendance

import android.annotation.SuppressLint
import android.app.Application
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AttendanceViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SecureStore(app)
    private val api = Api(store)
    private val fused = LocationServices.getFusedLocationProviderClient(app)

    @SuppressLint("HardwareIds")
    private val deviceId: String =
        Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()

    var signedIn by mutableStateOf(store.hasSession)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var messageIsError by mutableStateOf(false)
        private set
    var today by mutableStateOf<Today?>(null)
        private set

    val hasSession: Boolean get() = store.hasSession
    val lastType: String? get() = today?.logs?.lastOrNull()?.type

    fun report(text: String, error: Boolean) { message = text; messageIsError = error }

    fun login(user: String, pass: String) = launchOp {
        api.login(user, pass)
        signedIn = true
        today = api.today()
    }

    fun logout() {
        api.logout(); signedIn = false; today = null; message = null
    }

    fun refresh() {
        if (!signedIn) return
        viewModelScope.launch { busy = true; guarded { today = api.today() }; busy = false }
    }

    @SuppressLint("MissingPermission")
    fun punch(type: String) = launchOp {
        val loc = fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
            ?: throw ApiException("Could not get a GPS fix. Turn on Location, move outdoors and try again.")
        if (loc.mocked) throw ApiException("Mock location detected. Turn off fake-GPS apps and try again.")
        if (loc.accuracy > Config.MAX_ACCURACY_M)
            throw ApiException("GPS accuracy is ${loc.accuracy.toInt()} m (need ${Config.MAX_ACCURACY_M.toInt()} m or better). Wait a few seconds and retry.")
        val msg = api.punch(type, loc, deviceId)
        today = api.today()
        report(msg, false)
    }

    private fun launchOp(block: suspend () -> Unit) {
        viewModelScope.launch { busy = true; message = null; guarded(block); busy = false }
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: ApiException) {
            report(e.message ?: "Error", true)
            if (e.sessionExpired) signedIn = false
        } catch (e: Exception) {
            report(e.message ?: "Network error. Check your connection.", true)
        }
    }
}
