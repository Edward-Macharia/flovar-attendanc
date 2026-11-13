package com.flovar.attendance

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(message: String, val sessionExpired: Boolean = false) : Exception(message)

data class Punch(val type: String, val time: String)
data class Today(val name: String, val site: String?, val logs: List<Punch>)

private class SessionJar(private val store: SecureStore) : CookieJar {
    private val host = Config.BASE_URL.removePrefix("https://")
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.firstOrNull { it.name == "sid" }?.let { store.sid = if (it.value == "Guest" || it.value.isEmpty()) null else it.value }
    }
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val sid = store.sid ?: return emptyList()
        return listOf(Cookie.Builder().name("sid").value(sid).domain(host).secure().httpOnly().build())
    }
}

class Api(private val store: SecureStore) {
    private val client = OkHttpClient.Builder()
        .cookieJar(SessionJar(store))
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .build()
    private var csrf: String? = null

    suspend fun login(user: String, pass: String) = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("usr", user.trim()).add("pwd", pass).build()
        val req = Request.Builder().url("${Config.BASE_URL}/api/method/login").post(body).build()
        client.newCall(req).execute().use { r ->
            val txt = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException(if (r.code == 401) "Invalid email or password" else serverMessage(txt) ?: "Login failed (${r.code})")
            store.fullName = runCatching { JSONObject(txt).optString("full_name") }.getOrNull()
        }
        fetchCsrf()
    }

    fun logout() { store.clear(); csrf = null }

    private fun fetchCsrf() {
        val req = Request.Builder().url("${Config.BASE_URL}/app").build()
        client.newCall(req).execute().use { r ->
            val html = r.body?.string().orEmpty()
            csrf = Regex("""csrf_token\s*[=:]\s*["']([^"']+)["']""").find(html)?.groupValues?.get(1)
            if (csrf == null || csrf == "{{ csrf_token }}") throw ApiException("Could not start a secure session", true)
        }
    }

    private suspend fun call(method: String, form: Map<String, String>? = null, retry: Boolean = true): JSONObject =
        withContext(Dispatchers.IO) {
            if (csrf == null) fetchCsrf()
            val b = Request.Builder().url("${Config.BASE_URL}/api/method/$method")
            if (form != null) {
                val fb = FormBody.Builder(); form.forEach { (k, v) -> fb.add(k, v) }
                b.post(fb.build()).header("X-Frappe-CSRF-Token", csrf!!)
            }
            client.newCall(b.build()).execute().use { r ->
                val txt = r.body?.string().orEmpty()
                when {
                    r.code == 401 || r.code == 403 -> { store.clear(); throw ApiException("Session expired. Please sign in again.", true) }
                    (r.code == 400 || r.code == 417) && retry && serverMessage(txt)?.contains("CSRF", true) == true -> { fetchCsrf(); return@withContext call(method, form, false) }
                    !r.isSuccessful -> throw ApiException(serverMessage(txt) ?: "Server error (${r.code})")
                }
                JSONObject(txt).optJSONObject("message") ?: JSONObject()
            }
        }

    suspend fun today(): Today {
        val m = call("flovar_today")
        val logs = m.optJSONArray("logs") ?: JSONArray()
        return Today(m.optString("employee_name"), m.optString("site").ifBlank { null },
            (0 until logs.length()).map { logs.getJSONObject(it).let { o -> Punch(o.getString("log_type"), o.getString("time")) } })
    }

    suspend fun punch(logType: String, loc: Location, deviceId: String): String {
        val m = call("flovar_checkin", mapOf(
            "log_type" to logType,
            "latitude" to loc.latitude.toString(), "longitude" to loc.longitude.toString(),
            "accuracy" to loc.accuracy.toString(),
            "is_mock" to (if (loc.mocked) "1" else "0"),
            "device_id" to deviceId))
        return m.optString("message", "Recorded")
    }


    private fun serverMessage(body: String): String? = runCatching {
        val sm = JSONObject(body).optString("_server_messages")
        if (sm.isNotEmpty()) JSONObject(JSONArray(sm).getString(0)).getString("message").replace(Regex("<[^>]*>"), "")
        else JSONObject(body).optString("exception").substringAfter(": ", "").ifBlank { null }
    }.getOrNull()
}
