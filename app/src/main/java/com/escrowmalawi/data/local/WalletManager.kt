package com.escrowmalawi.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_wallet_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun savePrivateKey(uid: String, privateKey: String) {
        prefs.edit().putString("pk_$uid", privateKey).apply()
    }

    fun getPrivateKey(uid: String): String? {
        return prefs.getString("pk_$uid", null)
    }

    fun deletePrivateKey(uid: String) {
        prefs.edit().remove("pk_$uid").apply()
    }
}
