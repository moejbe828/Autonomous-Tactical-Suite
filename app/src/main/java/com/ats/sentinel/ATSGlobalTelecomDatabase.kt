
package com.ats.sentinel

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

/**
 * ATS GLOBAL TELECOM DATABASE
 *
 * Country numbering metadata is provided by libphonenumber.
 * Operator-prefix records are maintained independently by
 * ATSGlobalOperatorPrefixDatabase.
 *
 * This database does not claim to identify a live caller location
 * or the subscriber's current mobile operator.
 */
object ATSGlobalTelecomDatabase {

    private const val DATABASE_VERSION = "1.1.0"

    private const val NUMBERING_SOURCE =
        "Google libphonenumber numbering metadata"

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
     * All geographic regions supported by the installed
     * libphonenumber metadata.
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
     * A calling code may be shared by multiple regions.
     */
    fun getCountriesByCallingCode(
        callingCode: String
    ): List<CountryRecord> {

        val normalized = callingCode
            .trim()
            .removePrefix("+")

        if (
            normalized.isBlank() ||
            !normalized.all { it.isDigit() }
        ) {
            return emptyList()
        }

        return getAllCountries().filter {
            it.callingCode == "+$normalized"
        }
    }

    /**
     * Returns the country profile and currently loaded
     * operator allocation records.
     *
     * PARTIAL is used when records exist because the presence
     * of some operators does not prove the entire national
     * operator allocation list is complete.
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
                operatorSource =
                    ATSGlobalOperatorPrefixDatabase.DATABASE_SOURCE,
                databaseVersion = DATABASE_VERSION
            )
        }

        val operators =
            ATSGlobalOperatorPrefixDatabase.getOperatorsForCountry(
                country.regionCode
            )

        val status = when {
            operators.isEmpty() ->
                RecordStatus.DATA_UNAVAILABLE

            else ->
                RecordStatus.PARTIAL
        }

        val sources = operators
            .map { it.source }
            .distinct()
            .joinToString("; ")
            .ifBlank {
                "No verified operator allocation record loaded"
            }

        return CountryProfile(
            country = country,
            operators = operators,
            operatorStatus = status,
            operatorSource = sources,
            databaseVersion = DATABASE_VERSION
        )
    }

    /**
     * Finds the originally allocated operator prefix.
     *
     * This is NOT a live current-carrier lookup.
     */
    fun findOperatorByPrefix(
        regionCode: String,
        nationalNumber: String
    ): OperatorRecord? {

        return ATSGlobalOperatorPrefixDatabase.findByPrefix(
            regionCode = regionCode,
            nationalNumber = nationalNumber
        )
    }

    fun getAllOperators(): List<OperatorRecord> {
        return ATSGlobalOperatorPrefixDatabase.getAllOperators()
    }

    fun getCountryCount(): Int {
        return phoneUtil.supportedRegions.size
    }

    fun getOperatorCount(): Int {
        return ATSGlobalOperatorPrefixDatabase.getOperatorCount()
    }

    /**
     * Metadata displayed by ATS TRACK SCAN.
     */
    fun getDatabaseInfo(): Map<String, String> {

        val operatorCount = getOperatorCount()

        val countryCountWithOperators =
            ATSGlobalOperatorPrefixDatabase
                .getCountryCountWithOperatorData()

        val operatorStatus = when {
            operatorCount == 0 ->
                "NO VERIFIED OPERATOR DATA"

            else ->
                "PARTIAL — VERIFIED RECORDS ONLY"
        }

        val mccMncStatus = when {
            ATSGlobalOperatorPrefixDatabase
                .getAllOperators()
                .any { it.mcc != null && it.mnc != null } ->
                "PARTIAL — SOURCED MCC/MNC RECORDS"

            else ->
                "NOT LOADED"
        }

        return mapOf(
            "DATABASE_NAME" to
                "ATS GLOBAL TELECOM DATABASE",

            "DATABASE_VERSION" to
                DATABASE_VERSION,

            "COUNTRY_SOURCE" to
                NUMBERING_SOURCE,

            "COUNTRY_METADATA_VERSION" to
                "libphonenumber 9.0.43",

            "OPERATOR_SOURCE" to
                ATSGlobalOperatorPrefixDatabase.DATABASE_SOURCE,

            "COUNTRY_RECORD_COUNT" to
                getCountryCount().toString(),

            "OPERATOR_RECORD_COUNT" to
                operatorCount.toString(),

            "OPERATOR_COUNTRY_COUNT" to
                countryCountWithOperators.toString(),

            "OPERATOR_DATABASE_STATUS" to
                operatorStatus,

            "MCC_MNC_STATUS" to
                mccMncStatus,

            "OFFLINE_STATUS" to
                "LOCAL NUMBERING AND OPERATOR DATA AVAILABLE"
        )
    }

    private fun createCountryRecord(
        region: String
    ): CountryRecord? {

        val normalized = region.uppercase(Locale.ROOT)

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
