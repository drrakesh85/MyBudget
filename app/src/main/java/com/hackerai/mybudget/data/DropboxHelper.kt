package com.hackerai.mybudget.data

import android.content.Context
import android.content.SharedPreferences
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DropboxHelper(private val context: Context) {
    private val appKey = "YOUR_DROPBOX_APP_KEY" // TO BE REPLACED BY USER
    private val prefs: SharedPreferences = context.getSharedPreferences("dropbox_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val syncFileName = "/sync_data.json"

    fun startAuth() {
        Auth.startOAuth2PKCE(context, appKey, DbxRequestConfig("my-budget-app"))
    }

    fun handleAuth() {
        val credential = Auth.getDbxCredential()
        if (credential != null) {
            prefs.edit().putString("access_token", credential.accessToken).apply()
        }
    }

    private fun getClient(): DbxClientV2? {
        val token = prefs.getString("access_token", null) ?: return null
        val config = DbxRequestConfig.newBuilder("my-budget-app").build()
        return DbxClientV2(config, token)
    }

    suspend fun uploadSyncData(expenses: List<Expense>) = withContext(Dispatchers.IO) {
        val client = getClient() ?: return@withContext
        val content = gson.toJson(expenses)
        val inputStream = ByteArrayInputStream(content.toByteArray())
        
        client.files().uploadBuilder(syncFileName)
            .withMode(WriteMode.OVERWRITE)
            .uploadAndFinish(inputStream)
    }

    suspend fun downloadSyncData(): List<Expense> = withContext(Dispatchers.IO) {
        val client = getClient() ?: return@withContext emptyList<Expense>()
        
        try {
            val outputStream = ByteArrayOutputStream()
            client.files().download(syncFileName).download(outputStream)
            val json = outputStream.toString()
            
            val type = object : TypeToken<List<Expense>>() {}.type
            gson.fromJson<List<Expense>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
