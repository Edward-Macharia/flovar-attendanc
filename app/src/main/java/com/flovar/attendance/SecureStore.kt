package com.flovar.attendance

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Session cookie + profile, encrypted with an Android Keystore-backed key. No password is ever stored. */
class SecureStore(ctx: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        ctx, "flovar_secure",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    var sid: String?
        get() = prefs.getString("sid", null)
        set(v) = prefs.edit().apply { if (v == null) remove("sid") else putString("sid", v) }.apply()
    var fullName: String?
        get() = prefs.getString("name", null)
        set(v) = prefs.edit().apply { if (v == null) remove("name") else putString("name", v) }.apply()
    val hasSession get() = sid != null
    fun clear() = prefs.edit().clear().apply()
}
