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
import com.google.gson.Gson
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

    private val gson = Gson()
    private val appDataFolderName = "appDataFolder"
    private val syncFileName = "sync_data.json"

    private val driveScope = Scope(DriveScopes.DRIVE_APPDATA)

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

    suspend fun uploadSyncData(account: GoogleSignInAccount, expenses: List<Expense>) = withContext(Dispatchers.IO) {
        if (!hasDrivePermission(account)) {
            throw GoogleDriveAuthException("Google Drive permission was not granted.")
        }
        try {
            val service = getDriveService(account)
            val content = gson.toJson(expenses)
            Log.d(TAG, "Uploading ${expenses.size} expenses to Drive appDataFolder/$syncFileName")

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

    suspend fun downloadSyncData(account: GoogleSignInAccount): List<Expense> = withContext(Dispatchers.IO) {
        if (!hasDrivePermission(account)) {
            throw GoogleDriveAuthException("Google Drive permission was not granted.")
        }
        try {
            val service = getDriveService(account)
            val file = findSyncFile(service)
            if (file == null) {
                Log.i(TAG, "No existing sync file found in appDataFolder; starting with empty remote data")
                return@withContext emptyList()
            }

            Log.d(TAG, "Downloading Drive file id=${file.id}")
            val outputStream = ByteArrayOutputStream()
            service.files().get(file.id).executeMediaAndDownloadTo(outputStream)
            val json = outputStream.toString(Charsets.UTF_8.name())
            if (json.isBlank()) {
                throw Exception("Google Drive sync file is empty.")
            }

            val type = object : TypeToken<List<Expense>>() {}.type
            gson.fromJson<List<Expense>>(json, type)
                ?: throw Exception("Google Drive sync file did not contain expense data.")
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
    }
}
