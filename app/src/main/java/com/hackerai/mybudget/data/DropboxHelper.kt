package com.hackerai.mybudget.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hackerai.mybudget.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DropboxHelper(private val context: Context) {
    private val appKey: String by lazy { context.getString(R.string.dropbox_app_key) }
    private val gson = Gson()
    private val syncFileName = "/sync_data.json"
    private val clientIdentifier = "my-budget-app"

    private val prefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "dropbox_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun startAuth() {
        val scopes = listOf(
            "account_info.read",
            "files.metadata.read",
            "files.content.read",
            "files.content.write"
        )
        val config = DbxRequestConfig.newBuilder(clientIdentifier).build()
        Auth.startOAuth2PKCE(context, appKey, config, scopes)
    }

    fun handleAuthResponse(): Boolean {
        // Auth.getDbxCredential() returns DbxCredential for SDK 7.0.0+ PKCE flow
        val credential = Auth.getDbxCredential()
        return if (credential != null) {
            saveCredential(credential)
            true
        } else {
            false
        }
    }

    private fun saveCredential(credential: DbxCredential) {
        // Serialize manually to avoid GSON issues with internal SDK classes
        val map = mapOf(
            "accessToken" to credential.accessToken,
            "expiresAt" to credential.expiresAt,
            "refreshToken" to credential.refreshToken,
            "appKey" to credential.appKey
        )
        val json = gson.toJson(map)
        prefs.edit().putString("dropbox_credential_json_v2", json).apply()
    }

    fun isConnected(): Boolean {
        return getCredential() != null
    }

    fun disconnect() {
        prefs.edit().remove("dropbox_credential_json_v2").apply()
    }

    private fun getCredential(): DbxCredential? {
        val json = prefs.getString("dropbox_credential_json_v2", null) ?: return null
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val map: Map<String, Any> = gson.fromJson(json, type)
            
            val accessToken = map["accessToken"] as String
            val refreshToken = map["refreshToken"] as? String
            val expiresAt = (map["expiresAt"] as? Double)?.toLong() ?: -1L
            val appKey = map["appKey"] as? String
            
            DbxCredential(accessToken, expiresAt, refreshToken, appKey)
        } catch (e: Exception) {
            null
        }
    }

    private fun getClient(): DbxClientV2? {
        val credential = getCredential() ?: return null
        val config = DbxRequestConfig.newBuilder(clientIdentifier).build()
        return DbxClientV2(config, credential)
    }

    suspend fun uploadSyncData(syncData: DropboxSyncData) = withContext(Dispatchers.IO) {
        val client = getClient() ?: throw Exception("Dropbox not connected")
        val content = gson.toJson(syncData)
        val inputStream = ByteArrayInputStream(content.toByteArray())
        
        client.files().uploadBuilder(syncFileName)
            .withMode(WriteMode.OVERWRITE)
            .uploadAndFinish(inputStream)
    }

    suspend fun downloadSyncData(): DropboxSyncData? = withContext(Dispatchers.IO) {
        val client = getClient() ?: throw Exception("Dropbox not connected")
        
        try {
            val outputStream = ByteArrayOutputStream()
            client.files().download(syncFileName).download(outputStream)
            val json = outputStream.toString()
            
            val type = object : TypeToken<DropboxSyncData>() {}.type
            gson.fromJson<DropboxSyncData>(json, type)
        } catch (e: com.dropbox.core.v2.files.DownloadErrorException) {
            if (e.errorValue.isPath && e.errorValue.pathValue.isNotFound) {
                null
            } else {
                throw e
            }
        }
    }
}

data class DropboxSyncData(
    val schemaVersion: Int = 1,
    val lastSyncTimestamp: Long,
    val expenses: List<Expense>
)
