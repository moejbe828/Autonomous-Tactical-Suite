
package com.ats.sentinel

import android.content.Context
import android.content.SharedPreferences
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Locale
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

data class ATSBlockedNumber(
    val number: String
)

data class ATSBlockingAuditEvent(
    val action: String,
    val maskedNumber: String,
    val timestamp: Long,
    val source: String
)

class ATSBlockedNumbersStoreException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

object ATSBlockedNumbersStore {

    private const val KEY_ALIAS = "ATS_BLOCKING_AES_KEY"
    private const val PREFS_NAME = "ats_secure_blocking_store"
    private const val PREF_IV = "encrypted_iv"
    private const val PREF_DATA = "encrypted_data"

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val MAX_AUDIT_EVENTS = 300

    private data class StoreState(
        val numbers: MutableList<String> = mutableListOf(),
        val audit: MutableList<ATSBlockingAuditEvent> = mutableListOf()
    )

    private fun preferences(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    /**
     * Country selection priority:
     * 1. SIM country
     * 2. Network country
     * 3. Device locale
     *
     * No country is hardcoded.
     */
    private fun getDefaultCountryIso(context: Context): String? {
        val telephonyManager = try {
            context.applicationContext.getSystemService(
                Context.TELEPHONY_SERVICE
            ) as? TelephonyManager
        } catch (_: Exception) {
            null
        }

        val simCountry = try {
            telephonyManager?.simCountryIso
                ?.trim()
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it.matches(Regex("^[a-z]{2}$")) }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }

        if (!simCountry.isNullOrBlank()) {
            return simCountry
        }

        val networkCountry = try {
            telephonyManager?.networkCountryIso
                ?.trim()
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it.matches(Regex("^[a-z]{2}$")) }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }

        if (!networkCountry.isNullOrBlank()) {
            return networkCountry
        }

        return Locale.getDefault()
            .country
            .trim()
            .lowercase(Locale.ROOT)
            .takeIf { it.matches(Regex("^[a-z]{2}$")) }
    }

    /**
     * Converts phone numbers into a consistent representation.
     *
     * Valid local numbers are converted to E.164 using the
     * automatically detected country ISO.
     *
     * International numbers retain their international identity.
     * If conversion is unavailable, a conservative fallback is used.
     */
    private fun normalizeNumber(
        context: Context,
        rawNumber: String
    ): String {
        val input = rawNumber.trim()

        if (input.isEmpty()) {
            throw ATSBlockedNumbersStoreException(
                "Phone number cannot be empty."
            )
        }

        val normalized = PhoneNumberUtils.normalizeNumber(input)
        val digits = normalized.filter { it.isDigit() }

        if (digits.length < 3) {
            throw ATSBlockedNumbersStoreException(
                "Invalid phone number."
            )
        }

        val isInternational =
            input.startsWith("+") || normalized.startsWith("+")

        val countryIso = getDefaultCountryIso(context)

        if (isInternational) {
            val e164 = countryIso?.let {
                try {
                    PhoneNumberUtils.formatNumberToE164(input, it.uppercase())
                } catch (_: Exception) {
                    null
                }
            }

            return e164 ?: "+$digits"
        }

        if (!countryIso.isNullOrBlank()) {
            val e164 = try {
                PhoneNumberUtils.formatNumberToE164(
                    input,
                    countryIso.uppercase(Locale.ROOT)
                )
            } catch (_: Exception) {
                null
            }

            if (!e164.isNullOrBlank()) {
                return e164
            }
        }

        // Conservative fallback: do not guess a country code.
        return digits
    }

    /**
     * Public normalization entry point for UI components.
     * CONTACTS and CALL HISTORY should use this same function
     * instead of implementing their own number comparison.
     */
    @Synchronized
    @Throws(ATSBlockedNumbersStoreException::class)
    fun normalizeForComparison(
        context: Context,
        rawNumber: String
    ): String {
        return normalizeNumber(context, rawNumber)
    }

    private fun getSecretKey(): SecretKey {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            val existingKey = keyStore.getKey(KEY_ALIAS, null)

            if (existingKey is SecretKey) {
                return existingKey
            }

            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )

            val specification = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setRandomizedEncryptionRequired(true)
                .build()

            generator.init(specification)

            return generator.generateKey()

        } catch (e: Exception) {
            throw ATSBlockedNumbersStoreException(
                "Unable to access the secure Android Keystore key.",
                e
            )
        }
    }

    private fun encrypt(plainText: ByteArray): Pair<String, String> {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)

            cipher.init(
                Cipher.ENCRYPT_MODE,
                getSecretKey()
            )

            val encrypted = cipher.doFinal(plainText)

            val iv = Base64.encodeToString(
                cipher.iv,
                Base64.NO_WRAP
            )

            val data = Base64.encodeToString(
                encrypted,
                Base64.NO_WRAP
            )

            return Pair(iv, data)

        } catch (e: Exception) {
            throw ATSBlockedNumbersStoreException(
                "Secure encryption failed.",
                e
            )
        }
    }

    private fun decrypt(
        ivText: String,
        encryptedText: String
    ): ByteArray {
        try {
            val iv = Base64.decode(ivText, Base64.NO_WRAP)
            val encrypted = Base64.decode(
                encryptedText,
                Base64.NO_WRAP
            )

            if (iv.size != 12) {
                throw ATSBlockedNumbersStoreException(
                    "Invalid encryption IV."
                )
            }

            val cipher = Cipher.getInstance(TRANSFORMATION)

            cipher.init(
                Cipher.DECRYPT_MODE,
                getSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH, iv)
            )

            return cipher.doFinal(encrypted)

        } catch (e: AEADBadTagException) {
            throw ATSBlockedNumbersStoreException(
                "SECURITY ALERT: Stored blocking data failed integrity verification.",
                e
            )
        } catch (e: ATSBlockedNumbersStoreException) {
            throw e
        } catch (e: Exception) {
            throw ATSBlockedNumbersStoreException(
                "Unable to decrypt the secure blocking database.",
                e
            )
        }
    }

    private fun loadState(context: Context): StoreState {
        val prefs = preferences(context)

        val iv = prefs.getString(PREF_IV, null)
        val encrypted = prefs.getString(PREF_DATA, null)

        if (iv == null && encrypted == null) {
            return StoreState()
        }

        if (iv.isNullOrEmpty() || encrypted.isNullOrEmpty()) {
            throw ATSBlockedNumbersStoreException(
                "SECURITY ALERT: Incomplete encrypted database."
            )
        }

        val plainText = decrypt(iv, encrypted)

        try {
            val json = JSONObject(
                String(plainText, StandardCharsets.UTF_8)
            )

            val numbers = mutableListOf<String>()
            val numberArray = json.optJSONArray("numbers")
                ?: JSONArray()

            for (i in 0 until numberArray.length()) {
                val number = numberArray.getString(i)

                numbers.add(
                    normalizeNumber(context, number)
                )
            }

            val audit = mutableListOf<ATSBlockingAuditEvent>()
            val auditArray = json.optJSONArray("audit")
                ?: JSONArray()

            for (i in 0 until auditArray.length()) {
                val item = auditArray.getJSONObject(i)

                audit.add(
                    ATSBlockingAuditEvent(
                        action = item.getString("action"),
                        maskedNumber = item.getString("number"),
                        timestamp = item.getLong("timestamp"),
                        source = item.getString("source")
                    )
                )
            }

            return StoreState(
                numbers = numbers.distinct().toMutableList(),
                audit = audit
            )

        } catch (e: ATSBlockedNumbersStoreException) {
            throw e
        } catch (e: Exception) {
            throw ATSBlockedNumbersStoreException(
                "Encrypted database format is invalid.",
                e
            )
        }
    }

    private fun saveState(
        context: Context,
        state: StoreState
    ) {
        try {
            val root = JSONObject()
            val numbers = JSONArray()
            val audit = JSONArray()

            state.numbers.forEach {
                numbers.put(it)
            }

            state.audit.forEach { event ->
                val item = JSONObject()
                    .put("action", event.action)
                    .put("number", event.maskedNumber)
                    .put("timestamp", event.timestamp)
                    .put("source", event.source)

                audit.put(item)
            }

            root.put("version", 1)
            root.put("numbers", numbers)
            root.put("audit", audit)

            val encrypted = encrypt(
                root.toString().toByteArray(
                    StandardCharsets.UTF_8
                )
            )

            val saved = preferences(context)
                .edit()
                .putString(PREF_IV, encrypted.first)
                .putString(PREF_DATA, encrypted.second)
                .commit()

            if (!saved) {
                throw ATSBlockedNumbersStoreException(
                    "Unable to persist encrypted blocking data."
                )
            }

        } catch (e: ATSBlockedNumbersStoreException) {
            throw e
        } catch (e: Exception) {
            throw ATSBlockedNumbersStoreException(
                "Secure database save failed.",
                e
            )
        }
    }

    private fun maskNumber(number: String): String {
        if (number.length <= 4) {
            return "****"
        }

        return "****" + number.takeLast(4)
    }

    private fun addAudit(
        state: StoreState,
        action: String,
        number: String,
        source: String
    ) {
        state.audit.add(
            ATSBlockingAuditEvent(
                action = action,
                maskedNumber = maskNumber(number),
                timestamp = System.currentTimeMillis(),
                source = source.take(40)
            )
        )

        while (state.audit.size > MAX_AUDIT_EVENTS) {
            state.audit.removeAt(0)
        }
    }

    @Synchronized
    @Throws(ATSBlockedNumbersStoreException::class)
    fun blockNumber(
        context: Context,
        rawNumber: String,
        source: String = "MANUAL"
    ): Boolean {
        val number = normalizeNumber(context, rawNumber)
        val state = loadState(context)

        if (number in state.numbers) {
            return false
        }

        state.numbers.add(number)

        addAudit(
            state,
            "NUMBER_BLOCKED",
            number,
            source
        )

        saveState(context, state)

        return true
    }

    @Synchronized
    @Throws(ATSBlockedNumbersStoreException::class)
    fun unblockNumber(
        context: Context,
        rawNumber: String,
        source: String = "MANUAL"
    ): Boolean {
        val number = normalizeNumber(context, rawNumber)
        val state = loadState(context)

        if (!state.numbers.remove(number)) {
            return false
        }

        addAudit(
            state,
            "NUMBER_UNBLOCKED",
            number,
            source
        )

        saveState(context, state)

        return true
    }

    @Synchronized
    @Throws(ATSBlockedNumbersStoreException::class)
    fun isBlocked(
        context: Context,
        rawNumber: String
    ): Boolean {
        val number = normalizeNumber(context, rawNumber)
        return number in loadState(context).numbers
    }

    @Synchronized
    @Throws(ATSBlockedNumbersStoreException::class)
    fun getBlockedNumbers(
        context: Context
    ): List<ATSBlockedNumber> {
        return loadState(context)
            .numbers
            .map { ATSBlockedNumber(it) }
    }

    @Synchronized
    @Throws(ATSBlockedNumbersStoreException::class)
    fun getAuditLog(
        context: Context
    ): List<ATSBlockingAuditEvent> {
        return loadState(context).audit
            .sortedByDescending { it.timestamp }
    }

    @Synchronized
    @Throws(ATSBlockedNumbersStoreException::class)
    fun getDatabaseStatus(
        context: Context
    ): String {
        val state = loadState(context)

        return "SECURE | AES-256-GCM | " +
            "Integrity: VERIFIED | " +
            "Blocked: ${state.numbers.size} | " +
            "Audit Events: ${state.audit.size}"
    }
}
