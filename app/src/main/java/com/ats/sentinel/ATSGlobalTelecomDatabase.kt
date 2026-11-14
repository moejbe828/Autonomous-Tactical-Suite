
package com.ats.sentinel

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

/**
 * ATS GLOBAL TELECOM DATABASE
 *
 * Provides locally available international numbering metadata.
 *
 * IMPORTANT:
 * - Country calling codes come from libphonenumber metadata.
 * - This is not a complete live operator-allocation database.
 * - MCC/MNC values are never guessed or fabricated.
 * - Original carrier prefix data must not be treated as current carrier data.
 */
object ATSGlobalTelecomDatabase {

    private const val DATABASE_VERSION = "1.0.0"

    private const val NUMBERING_SOURCE =
        "Google libphonenumber numbering metadata"

    private const val OPERATOR_SOURCE =
        "No verified public operator allocation record loaded"

    private val phoneUtil: PhoneNumberUtil by lazy {
        PhoneNumberUtil.getInstance()
    }

    enum class RecordStatus {
        VERIFIED,
        PARTIAL,
        UNVERIFIED,
        DATA_UNAVAILABLE
    }

    data class CountryRecord(
        val regionCode: String,
        val countryName: String,
        val callingCode: String,
        val source: String,
        val sourceVersion: String,
        val status: RecordStatus,
        val confidence: Int
    )

    data class OperatorRecord(
        val countryRegion: String,
        val operatorName: String,
        val mcc: String?,
        val mnc: String?,
        val numberPrefixes: List<String>,
        val source: String,
        val sourceUrl: String?,
        val verifiedDate: String?,
        val status: RecordStatus,
        val notes: String
    )

    data class CountryProfile(
        val country: CountryRecord?,
        val operators: List<OperatorRecord>,
        val operatorStatus: RecordStatus,
        val operatorSource: String,
        val databaseVersion: String
    )

    /**
     * Operator records must be added only after verification
     * against an authoritative source.
     *
     * Empty by default intentionally.
     */
    private val verifiedOperators =
        emptyList<OperatorRecord>()

    /**
     * Returns all geographic regions supported by the
     * installed libphonenumber numbering metadata.
     *
     * This is a library-supported region list, not a claim
     * that every sovereign state has a separate calling code.
     */
    fun getAllCountries(): List<CountryRecord> {

        return phoneUtil.supportedRegions
            .asSequence()
            .mapNotNull { region ->
                createCountryRecord(region)
            }
            .sortedBy { it.countryName }
            .toList()
    }

    /**
     * Looks up a region by ISO region code.
     */
    fun getCountry(regionCode: String): CountryRecord? {

        val normalized = regionCode
            .trim()
            .uppercase(Locale.ROOT)

        if (normalized.length != 2) {
            return null
        }

        if (!phoneUtil.supportedRegions.contains(normalized)) {
            return null
        }

        return createCountryRecord(normalized)
    }

    /**
     * Finds all regions using a given international calling code.
     *
     * A calling code may be shared by multiple regions.
     */
    fun getCountriesByCallingCode(
        callingCode: String
    ): List<CountryRecord> {

        val normalized = callingCode
            .trim()
            .removePrefix("+")

        if (normalized.isBlank() ||
            !normalized.all { it.isDigit() }
        ) {
            return emptyList()
        }

        return getAllCountries().filter {
            it.callingCode == "+$normalized"
        }
    }

    /**
     * Returns a country profile and its verified operator records.
     *
     * When no verified operator record exists, the result
     * explicitly reports DATA_UNAVAILABLE.
     */
    fun getCountryProfile(
        regionCode: String
    ): CountryProfile {

        val country = getCountry(regionCode)

        if (country == null) {
            return CountryProfile(
                country = null,
                operators = emptyList(),
                operatorStatus = RecordStatus.DATA_UNAVAILABLE,
                operatorSource = OPERATOR_SOURCE,
                databaseVersion = DATABASE_VERSION
            )
        }

        val operators = verifiedOperators.filter {
            it.countryRegion.equals(
                country.regionCode,
                ignoreCase = true
            )
        }

        val status = when {
            operators.isEmpty() ->
                RecordStatus.DATA_UNAVAILABLE

            operators.all {
                it.status == RecordStatus.VERIFIED
            } ->
                RecordStatus.VERIFIED

            else ->
                RecordStatus.PARTIAL
        }

        return CountryProfile(
            country = country,
            operators = operators,
            operatorStatus = status,
            operatorSource = if (operators.isEmpty()) {
                OPERATOR_SOURCE
            } else {
                operators.joinToString {
                    it.source
                }
            },
            databaseVersion = DATABASE_VERSION
        )
    }

    /**
     * Finds a verified operator record by exact region and
     * an explicitly supplied national number prefix.
     *
     * This does not determine the current carrier after
     * mobile number portability.
     */
    fun findOperatorByPrefix(
        regionCode: String,
        nationalNumber: String
    ): OperatorRecord? {

        val region = regionCode
            .trim()
            .uppercase(Locale.ROOT)

        val number = nationalNumber
            .filter { it.isDigit() }

        if (region.isBlank() || number.isBlank()) {
            return null
        }

        return verifiedOperators
            .asSequence()
            .filter {
                it.countryRegion.equals(
                    region,
                    ignoreCase = true
                )
            }
            .filter {
                it.status == RecordStatus.VERIFIED
            }
            .filter { record ->
                record.numberPrefixes.any { prefix ->
                    number.startsWith(
                        prefix.filter { it.isDigit() }
                    )
                }
            }
            .maxByOrNull { record ->
                record.numberPrefixes.maxOfOrNull {
                    it.length
                } ?: 0
            }
    }

    /**
     * Returns the number of regions currently available
     * in the installed numbering metadata.
     */
    fun getCountryCount(): Int {
        return phoneUtil.supportedRegions.size
    }

    /**
     * Returns database metadata for the UI and audit log.
     */
    fun getDatabaseInfo(): Map<String, String> {

        return mapOf(
            "DATABASE_NAME" to "ATS GLOBAL TELECOM DATABASE",
            "DATABASE_VERSION" to DATABASE_VERSION,
            "COUNTRY_SOURCE" to NUMBERING_SOURCE,
            "COUNTRY_METADATA_VERSION" to "libphonenumber 9.0.43",
            "OPERATOR_SOURCE" to OPERATOR_SOURCE,
            "COUNTRY_RECORD_COUNT" to getCountryCount().toString(),
            "OPERATOR_RECORD_COUNT" to verifiedOperators.size.toString(),
            "MCC_MNC_STATUS" to "NOT LOADED — NO VERIFIED DATA",
            "OFFLINE_STATUS" to "LOCAL NUMBERING METADATA AVAILABLE"
        )
    }

    private fun createCountryRecord(
        region: String
    ): CountryRecord? {

        val normalized = region
            .uppercase(Locale.ROOT)

        val callingCode =
            phoneUtil.getCountryCodeForRegion(normalized)

        if (callingCode <= 0) {
            return null
        }

        val countryName = Locale
            .Builder()
            .setRegion(normalized)
            .build()
            .getDisplayCountry(Locale.ENGLISH)
            .ifBlank { normalized }

        return CountryRecord(
            regionCode = normalized,
            countryName = countryName,
            callingCode = "+$callingCode",
            source = NUMBERING_SOURCE,
            sourceVersion = "9.0.43",
            status = RecordStatus.VERIFIED,
            confidence = 100
        )
    }
}
