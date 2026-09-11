package com.hackerai.mybudget.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.reflect.Type

class AccountRepository(context: Context) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences = createEncryptedPrefs(appContext)

    private val gson = GsonBuilder()
        .registerTypeAdapter(Account::class.java, AccountAdapter())
        .create()

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    init {
        migrateFromLegacyPrefs()
        loadAccounts()
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun migrateFromLegacyPrefs() {
        val legacyPrefs = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val legacyJson = legacyPrefs.getString(ACCOUNTS_KEY, null) ?: return
        if (prefs.contains(ACCOUNTS_KEY)) return
        prefs.edit().putString(ACCOUNTS_KEY, legacyJson).apply()
        legacyPrefs.edit().remove(ACCOUNTS_KEY).apply()
    }

    private fun loadAccounts() {
        val json = prefs.getString(ACCOUNTS_KEY, null)
        if (json != null) {
            try {
                val listType = object : TypeToken<List<Account>>() {}.type
                val list: List<Account> = gson.fromJson(json, listType)
                _accounts.value = list
            } catch (e: Exception) {
                Log.e("AccountRepository", "Failed to load accounts", e)
                _accounts.value = emptyList()
            }
        }
    }

    fun addAccount(account: Account) {
        val current = _accounts.value.toMutableList()
        val index = current.indexOfFirst { it.id == account.id }
        if (index != -1) {
            current[index] = account
        } else {
            current.add(account)
        }
        _accounts.value = current
        saveAccounts(current)
    }

    fun deleteAccount(accountId: String) {
        val current = _accounts.value.filter { it.id != accountId }
        _accounts.value = current
        saveAccounts(current)
    }

    fun updateOrder(newOrder: List<Account>) {
        _accounts.value = newOrder
        saveAccounts(newOrder)
    }

    private fun saveAccounts(list: List<Account>) {
        val json = gson.toJson(list)
        prefs.edit().putString(ACCOUNTS_KEY, json).apply()
    }

    fun getUniqueNickNames(): List<String> {
        return _accounts.value.map { it.nickName }.distinct()
    }

    companion object {
        private const val PREFS_NAME = "accounts_prefs_encrypted"
        private const val LEGACY_PREFS_NAME = "accounts_prefs"
        private const val ACCOUNTS_KEY = "accounts_list"
    }
}

class AccountAdapter : JsonSerializer<Account>, JsonDeserializer<Account> {
    override fun serialize(src: Account, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val obj = context.serialize(src).asJsonObject
        obj.addProperty("type", src.type.name)
        return obj
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Account {
        val obj = json.asJsonObject
        val typeStr = obj.get("type").asString
        val type = AccountType.valueOf(typeStr)

        return when (type) {
            AccountType.SAVING -> context.deserialize(json, SavingAccount::class.java)
            AccountType.LOAN -> context.deserialize(json, LoanAccount::class.java)
            AccountType.CREDIT_CARD -> context.deserialize(json, CreditCardAccount::class.java)
            AccountType.CASH -> context.deserialize(json, CashAccount::class.java)
        }
    }
}
