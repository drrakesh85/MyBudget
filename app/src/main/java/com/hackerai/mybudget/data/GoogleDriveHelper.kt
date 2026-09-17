package com.hackerai.mybudget.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.hackerai.mybudget.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Collections

class GoogleDriveAuthException(
    message: String,
    val recoverableIntent: Intent? = null,
    cause: Throwable? = null
) : Exception(message, cause)

class GoogleDriveHelper(private val context: Context) {

    private val gson = GsonBuilder()
        .registerTypeAdapter(Account::class.java, AccountAdapter())
        .create()
    private val appDataFolderName = "appDataFolder"
    private val syncFileName = "sync_data.json"

    private val driveScope = Scope(DriveScopes.DRIVE_APPDATA)
    private val syncPrefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getGoogleSignInClient() = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestScopes(driveScope)
            .build()
    )

    fun hasDrivePermission(account: GoogleSignInAccount): Boolean {
        return GoogleSignIn.hasPermissions(account, driveScope)
    }

    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun isDriveConnected(): Boolean {
        val account = getLastSignedInAccount() ?: return false
        return hasDrivePermission(account)
    }

    fun getLastSuccessfulSyncMillis(): Long {
        return syncPrefs.getLong(KEY_LAST_SUCCESSFUL_SYNC, 0L)
    }

    fun saveLastSuccessfulSyncMillis(millis: Long) {
        syncPrefs.edit().putLong(KEY_LAST_SUCCESSFUL_SYNC, millis).apply()
    }

    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val androidAccount = account.account
            ?: throw GoogleDriveAuthException("Google account is unavailable. Sign in again.")
        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = androidAccount
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("My Budget").build()
    }

    suspend fun uploadSyncData(account: GoogleSignInAccount, syncData: SyncData) = withContext(Dispatchers.IO) {
        if (!hasDrivePermission(account)) {
            throw GoogleDriveAuthException("Google Drive permission was not granted.")
        }
        try {
            val service = getDriveService(account)
            val content = gson.toJson(syncData)
            Log.d(TAG, "Uploading ${syncData.expenses.size} expenses to Drive appDataFolder/$syncFileName")

            val metadata = File()
                .setName(syncFileName)
                .setParents(Collections.singletonList(appDataFolderName))

            val contentStream = ByteArrayContent.fromString("application/json", content)

            val existingFile = findSyncFile(service)
            if (existingFile != null) {
                Log.d(TAG, "Updating existing Drive file id=${existingFile.id}")
                service.files().update(existingFile.id, null, contentStream).execute()
            } else {
                Log.d(TAG, "Creating new Drive file in appDataFolder")
                service.files().create(metadata, contentStream).execute()
            }
        } catch (e: UserRecoverableAuthIOException) {
            Log.e(TAG, "Recoverable Google Drive auth failure during upload", e)
            throw GoogleDriveAuthException(
                "Google Drive authorization required to upload sync data.",
                e.intent,
                e
            )
        } catch (e: GoogleDriveAuthException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Drive upload failed", e)
            throw Exception("Google Drive upload failed: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    suspend fun forceReplaceSyncData(account: GoogleSignInAccount, localSyncData: SyncData) = withContext(Dispatchers.IO) {
        val TAG_FORCE = "FORCE_CLOUD_RESTORE"
        if (!hasDrivePermission(account)) {
            throw GoogleDriveAuthException("Google Drive permission was not granted.")
        }
        
        Log.i(TAG_FORCE, "Starting FORCE REPLACE Google Drive from Local Data")
        Log.i(TAG_FORCE, "Local transaction count: ${localSyncData.expenses.size}")
        Log.i(TAG_FORCE, "Local account count: ${localSyncData.accounts.size}")

        try {
            val service = getDriveService(account)
            
            // 1. Download existing as backup
            val existingFile = findSyncFile(service)
            if (existingFile != null) {
                try {
                    val outputStream = ByteArrayOutputStream()
                    service.files().get(existingFile.id).executeMediaAndDownloadTo(outputStream)
                    val oldJson = outputStream.toString(Charsets.UTF_8.name())
                    Log.i(TAG_FORCE, "Old cloud data backup (JSON): $oldJson")
                    
                    if (oldJson.trim().startsWith("[")) {
                        val type = object : TypeToken<List<Expense>>() {}.type
                        val oldExpenses: List<Expense> = gson.fromJson(oldJson, type)
                        Log.i(TAG_FORCE, "Old cloud transaction count: ${oldExpenses.size} (Legacy format)")
                    } else {
                        val oldSyncData = gson.fromJson(oldJson, SyncData::class.java)
                        Log.i(TAG_FORCE, "Old cloud transaction count: ${oldSyncData?.expenses?.size ?: 0}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG_FORCE, "Failed to backup old cloud data, proceeding anyway: ${e.message}")
                }
            } else {
                Log.i(TAG_FORCE, "No existing cloud file to backup.")
            }

            // 2. Prepare Upload
            val localJson = gson.toJson(localSyncData)
            Log.i(TAG_FORCE, "Generated SyncData count: ${localSyncData.expenses.size}")
            
            val metadata = File()
                .setName(syncFileName)
                .setParents(Collections.singletonList(appDataFolderName))
            val contentStream = ByteArrayContent.fromString("application/json", localJson)

            // 3. Upload (Replace)
            if (existingFile != null) {
                Log.i(TAG_FORCE, "Replacing existing Drive file id=${existingFile.id}")
                service.files().update(existingFile.id, null, contentStream).execute()
            } else {
                Log.i(TAG_FORCE, "Creating new Drive file")
                service.files().create(metadata, contentStream).execute()
            }
            Log.i(TAG_FORCE, "Upload result: SUCCESS")

            // 4. Verify
            Log.i(TAG_FORCE, "Verifying uploaded data...")
            val verifiedSyncData = downloadSyncData(account)
            
            if (verifiedSyncData.expenses.size == localSyncData.expenses.size) {
                Log.i(TAG_FORCE, "Verification result: SUCCESS (Count matches: ${verifiedSyncData.expenses.size})")
                Log.i(TAG_FORCE, "Final status: COMPLETED SUCCESSFULLY")
            } else {
                val msg = "Verification FAILED: Uploaded count (${verifiedSyncData.expenses.size}) does not match local count (${localSyncData.expenses.size})"
                Log.e(TAG_FORCE, msg)
                throw Exception(msg)
            }

        } catch (e: Exception) {
            Log.e(TAG_FORCE, "Force replace failed: ${e.message}", e)
            Log.i(TAG_FORCE, "Final status: FAILED")
            throw e
        }
    }

    suspend fun downloadSyncData(account: GoogleSignInAccount): SyncData = withContext(Dispatchers.IO) {
        if (!hasDrivePermission(account)) {
            throw GoogleDriveAuthException("Google Drive permission was not granted.")
        }
        try {
            val service = getDriveService(account)
            val file = findSyncFile(service)
            if (file == null) {
                Log.i(TAG, "No existing sync file found in appDataFolder; starting with empty remote data")
                return@withContext SyncData()
            }

            Log.d(TAG, "Downloading Drive file id=${file.id}")
            val outputStream = ByteArrayOutputStream()
            service.files().get(file.id).executeMediaAndDownloadTo(outputStream)
            val json = outputStream.toString(Charsets.UTF_8.name())
            if (json.isBlank()) {
                throw Exception("Google Drive sync file is empty.")
            }

            // Detect if file is old format (List) or new format (SyncData)
            return@withContext if (json.trim().startsWith("[")) {
                Log.i(TAG, "Legacy Google Drive format detected (List)")
                val type = object : TypeToken<List<Expense>>() {}.type
                val expenses: List<Expense> = gson.fromJson(json, type) ?: emptyList()
                SyncData(expenses = expenses)
            } else {
                gson.fromJson(json, SyncData::class.java)
                    ?: throw Exception("Google Drive sync file did not contain valid data.")
            }
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse Google Drive sync JSON", e)
            throw Exception("Google Drive sync file JSON parsing failed: ${e.message}", e)
        } catch (e: UserRecoverableAuthIOException) {
            Log.e(TAG, "Recoverable Google Drive auth failure during download", e)
            throw GoogleDriveAuthException(
                "Google Drive authorization required to download sync data.",
                e.intent,
                e
            )
        } catch (e: GoogleDriveAuthException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Drive download failed", e)
            throw Exception("Google Drive download failed: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    private fun findSyncFile(service: Drive): File? {
        val result: FileList = service.files().list()
            .setSpaces(appDataFolderName)
            .setQ("name = '$syncFileName'")
            .setFields("files(id, name)")
            .execute()
        return result.files?.firstOrNull()
    }

    companion object {
        private const val TAG = "GoogleDriveHelper"
        private const val PREFS_NAME = "google_drive_sync_prefs"
        private const val KEY_LAST_SUCCESSFUL_SYNC = "last_successful_sync_millis"
    }
}
