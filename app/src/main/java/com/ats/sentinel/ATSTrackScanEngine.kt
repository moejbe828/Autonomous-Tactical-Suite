
package com.ats.sentinel

import android.content.Context
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import com.google.i18n.phonenumbers.PhoneNumberToCarrierMapper
import java.util.Locale

data class ATSTrackScanReport(
    val inputNumber: String,
    val formattedNumber: String,
    val isPossible: Boolean,
    val isValid: Boolean,
    val countryName: String,
    val regionCode: String,
    val callingCode: String,
    val numberType: String,
    val originalCarrier: String,
    val carrierNotice: String,
    val countrySource: String,
    val metadataSource: String,
    val metadataVersion: String,
    val confidence: Int,
    val riskAssessment: String,
    val analysisStatus: String
)

object ATSTrackScanEngine {

    private const val METADATA_VERSION =
        "Google libphonenumber 9.0.43"

    private const val CARRIER_VERSION =
        "Google carrier metadata 2.43"

    private val phoneUtil: PhoneNumberUtil by lazy {
        PhoneNumberUtil.getInstance()
    }

    private val carrierMapper: PhoneNumberToCarrierMapper by lazy {
        PhoneNumberToCarrierMapper.getInstance()
    }

    private data class RegionReference(
        val region: String?,
        val source: String
    )

    fun analyze(
        context: Context,
        rawNumber: String
    ): ATSTrackScanReport {

        val input = rawNumber.trim()

        if (
            input.isBlank() ||
            input.equals("PRIVATE / UNAVAILABLE", true) ||
            input.equals("UNKNOWN", true) ||
            input == "—"
        ) {
            return unavailable(
                input,
                "NUMBER UNAVAILABLE"
            )
        }

        val normalized = normalize(input)

        if (
            normalized.isBlank() ||
            normalized.any {
                it == '*' || it == '#'
            }
        ) {
            return unavailable(
                input,
                "UNSUPPORTED SERVICE CODE"
            )
        }

        val isInternational =
            normalized.startsWith("+")

        val reference = if (isInternational) {
            RegionReference(
                null,
                "INTERNATIONAL NUMBER PREFIX"
            )
        } else {
            resolveRegionReference(context)
        }

        if (
            !isInternational &&
            reference.region.isNullOrBlank()
        ) {
            return unavailable(
                input,
                "LOCAL NUMBER NEEDS A TRUSTED COUNTRY REFERENCE"
            )
        }

        return try {

            val parsed: Phonenumber.PhoneNumber =
                phoneUtil.parse(
                    normalized,
                    reference.region
                )

            val possible =
                phoneUtil.isPossibleNumber(parsed)

            val valid =
                phoneUtil.isValidNumber(parsed)

            val region =
                phoneUtil
                    .getRegionCodeForNumber(parsed)
                    .orEmpty()

            // -------------------------------------------------
            // GLOBAL COUNTRY DATABASE
            // -------------------------------------------------

            val countryRecord =
                if (
                    region.isNotBlank() &&
                    region != "001"
                ) {
                    ATSGlobalTelecomDatabase.getCountry(
                        region
                    )
                } else {
                    null
                }

            val countryName = when {
                region == "001" ->
                    "NON-GEOGRAPHIC NUMBER"

                countryRecord != null ->
                    countryRecord.countryName

                region.isNotBlank() ->
                    Locale("", region)
                        .displayCountry
                        .ifBlank { region }

                else ->
                    "UNKNOWN"
            }

            val callingCode =
                countryRecord?.callingCode
                    ?: "+${parsed.countryCode}"

            val countrySource =
                countryRecord?.let {
                    "${it.source} / ${it.sourceVersion}"
                } ?: reference.source

            // -------------------------------------------------
            // NUMBER CLASSIFICATION
            // -------------------------------------------------

            val parsedType =
                phoneUtil.getNumberType(parsed)

            val numberType =
                describeNumberType(parsedType)

            val formatted =
                phoneUtil.format(
                    parsed,
                    PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL
                )

            // -------------------------------------------------
            // VERIFIED GLOBAL OPERATOR DATABASE
            // -------------------------------------------------

            val operatorRecord =
                if (
                    valid &&
                    parsedType ==
                        PhoneNumberUtil.PhoneNumberType.MOBILE &&
                    region.isNotBlank() &&
                    region != "001"
                ) {
                    val nationalNumber =
                        phoneUtil.getNationalSignificantNumber(
                            parsed
                        )

                    ATSGlobalTelecomDatabase.findOperatorByPrefix(
                        regionCode = region,
                        nationalNumber = nationalNumber
                    )
                } else {
                    null
                }

            // -------------------------------------------------
            // GOOGLE CARRIER PREFIX FALLBACK
            // -------------------------------------------------

            val carrierMetadata =
                if (
                    valid &&
                    parsedType ==
                        PhoneNumberUtil.PhoneNumberType.MOBILE
                ) {
                    try {
                        carrierMapper
                            .getNameForNumber(
                                parsed,
                                Locale.ENGLISH
                            )
                            .trim()
                    } catch (_: Exception) {
                        ""
                    }
                } else {
                    ""
                }

            val originalCarrier =
                operatorRecord?.operatorName
                    ?.takeIf { it.isNotBlank() }
                    ?: carrierMetadata
                        .takeIf { it.isNotBlank() }
                    ?: "NOT AVAILABLE IN VERIFIED DATA"

            val carrierNotice = when {
                operatorRecord != null -> {
                    "Matched verified ATS operator allocation data. " +
                        "This does not establish the subscriber's current " +
                        "carrier after number portability."
                }

                carrierMetadata.isNotBlank() -> {
                    "Original carrier estimate from Google prefix metadata. " +
                        "No verified ATS operator allocation match is loaded. " +
                        "Current carrier is not guaranteed because of number portability."
                }

                else -> {
                    "No verified operator allocation match found. " +
                        "Current carrier is unknown."
                }
            }

            // -------------------------------------------------
            // DATABASE AUDIT INFORMATION
            // -------------------------------------------------

            val databaseInfo =
                ATSGlobalTelecomDatabase.getDatabaseInfo()

            val databaseVersion =
                databaseInfo["DATABASE_VERSION"]
                    ?: "UNKNOWN"

            val operatorCount =
                databaseInfo["OPERATOR_RECORD_COUNT"]
                    ?: "0"

            val operatorDatabaseStatus =
                when {
                    operatorRecord != null ->
                        "VERIFIED OPERATOR MATCH"

                    operatorCount == "0" ->
                        "OPERATOR REGISTRY NOT LOADED"

                    else ->
                        "NO VERIFIED PREFIX MATCH"
                }

            val metadataSource =
                "ATS Global Telecom Database + " +
                    "Google libphonenumber + Google carrier metadata"

            val metadataVersion =
                "$METADATA_VERSION / " +
                    "$CARRIER_VERSION / " +
                    "ATS Global DB $databaseVersion"

            // -------------------------------------------------
            // CONFIDENCE
            // -------------------------------------------------

            val confidence = when {
                !valid && !possible -> 20
                !valid && possible -> 60
                operatorRecord != null -> 96
                carrierMetadata.isNotBlank() -> 90
                valid -> 88
                else -> 20
            }

            // -------------------------------------------------
            // RISK ASSESSMENT
            // -------------------------------------------------

            val risk = when {
                !possible || !valid ->
                    "NUMBER VALIDATION FAILED — REVIEW REQUIRED"

                numberType == "PREMIUM RATE" ->
                    "PREMIUM-RATE NUMBER TYPE — REVIEW REQUIRED"

                else ->
                    "NO AUTOMATED FRAUD VERDICT — MANUAL CONTEXT REQUIRED"
            }

            // -------------------------------------------------
            // FINAL REPORT
            // -------------------------------------------------

            ATSTrackScanReport(
                inputNumber = input,
                formattedNumber = formatted,
                isPossible = possible,
                isValid = valid,
                countryName = countryName,
                regionCode = region,
                callingCode = callingCode,
                numberType = numberType,
                originalCarrier = originalCarrier,
                carrierNotice = carrierNotice,
                countrySource = countrySource,
                metadataSource =
                    "$metadataSource | $operatorDatabaseStatus",
                metadataVersion = metadataVersion,
                confidence = confidence,
                riskAssessment = risk,
                analysisStatus = if (valid) {
                    "ANALYSIS COMPLETE"
                } else {
                    "PARTIAL OR INVALID NUMBER"
                }
            )

        } catch (_: Exception) {

            unavailable(
                input,
                "NUMBER COULD NOT BE PARSED"
            )
        }
    }

    // =========================================================
    // NUMBER NORMALIZATION
    // =========================================================

    private fun normalize(value: String): String {

        return value
            .removePrefix("tel:")
            .trim()
            .replace(
                Regex("[\\s().-]"),
                ""
            )
    }

    // =========================================================
    // COUNTRY REFERENCE
    // =========================================================

    private fun resolveRegionReference(
        context: Context
    ): RegionReference {

        val telephony = try {

            context.getSystemService(
                Context.TELEPHONY_SERVICE
            ) as? TelephonyManager

        } catch (_: Exception) {
            null
        }

        val simCountry = try {

            telephony?.simCountryIso
                ?.uppercase(Locale.ROOT)
                .orEmpty()

        } catch (_: SecurityException) {
            ""
        } catch (_: Exception) {
            ""
        }

        if (isValidRegion(simCountry)) {

            return RegionReference(
                simCountry,
                "SIM COUNTRY"
            )
        }

        val networkCountry = try {

            telephony?.networkCountryIso
                ?.uppercase(Locale.ROOT)
                .orEmpty()

        } catch (_: SecurityException) {
            ""
        } catch (_: Exception) {
            ""
        }

        if (isValidRegion(networkCountry)) {

            return RegionReference(
                networkCountry,
                "NETWORK COUNTRY"
            )
        }

        val localeCountry =
            Locale.getDefault()
                .country
                .uppercase(Locale.ROOT)

        if (isValidRegion(localeCountry)) {

            return RegionReference(
                localeCountry,
                "DEVICE LOCALE — FALLBACK ONLY"
            )
        }

        return RegionReference(
            null,
            "NO TRUSTED COUNTRY REFERENCE"
        )
    }

    private fun isValidRegion(
        region: String
    ): Boolean {

        return region.length == 2 &&
            phoneUtil.getCountryCodeForRegion(region) > 0
    }

    // =========================================================
    // NUMBER TYPE
    // =========================================================

    private fun describeNumberType(
        type: PhoneNumberUtil.PhoneNumberType
    ): String {

        return when (type) {

            PhoneNumberUtil.PhoneNumberType.FIXED_LINE ->
                "FIXED LINE"

            PhoneNumberUtil.PhoneNumberType.MOBILE ->
                "MOBILE"

            PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE ->
                "FIXED LINE OR MOBILE"

            PhoneNumberUtil.PhoneNumberType.TOLL_FREE ->
                "TOLL FREE"

            PhoneNumberUtil.PhoneNumberType.PREMIUM_RATE ->
                "PREMIUM RATE"

            PhoneNumberUtil.PhoneNumberType.SHARED_COST ->
                "SHARED COST"

            PhoneNumberUtil.PhoneNumberType.VOIP ->
                "VOIP"

            PhoneNumberUtil.PhoneNumberType.PERSONAL_NUMBER ->
                "PERSONAL NUMBER"

            PhoneNumberUtil.PhoneNumberType.PAGER ->
                "PAGER"

            PhoneNumberUtil.PhoneNumberType.UAN ->
                "UAN"

            PhoneNumberUtil.PhoneNumberType.VOICEMAIL ->
                "VOICEMAIL"

            else ->
                "UNKNOWN / UNCLASSIFIED"
        }
    }

    // =========================================================
    // UNAVAILABLE REPORT
    // =========================================================

    private fun unavailable(
        input: String,
        reason: String
    ): ATSTrackScanReport {

        val databaseInfo =
            ATSGlobalTelecomDatabase.getDatabaseInfo()

        val databaseVersion =
            databaseInfo["DATABASE_VERSION"]
                ?: "UNKNOWN"

        return ATSTrackScanReport(
            inputNumber = input,
            formattedNumber = "NOT AVAILABLE",
            isPossible = false,
            isValid = false,
            countryName = "UNKNOWN",
            regionCode = "",
            callingCode = "UNKNOWN",
            numberType = "UNKNOWN",
            originalCarrier = "UNKNOWN",
            carrierNotice =
                "No verified carrier information.",
            countrySource =
                "NO TRUSTED REFERENCE",
            metadataSource =
                "ATS Global Telecom Database + Google libphonenumber",
            metadataVersion =
                "$METADATA_VERSION / ATS Global DB $databaseVersion",
            confidence = 0,
            riskAssessment =
                "INSUFFICIENT DATA — NO FRAUD CONCLUSION",
            analysisStatus = reason
        )
    }
}
