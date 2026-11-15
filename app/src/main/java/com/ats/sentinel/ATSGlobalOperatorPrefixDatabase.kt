
package com.ats.sentinel

/**
 * ATS GLOBAL OPERATOR PREFIX DATABASE
 *
 * Independent registry for verified historical number allocations.
 *
 * IMPORTANT:
 * - Prefix allocation identifies the originally allocated range.
 * - It does NOT prove the subscriber's current carrier.
 * - Mobile Number Portability can change the current operator.
 * - MCC/MNC is included only when supported by a cited source.
 * - Missing data is intentionally left null.
 */
object ATSGlobalOperatorPrefixDatabase {

    const val DATABASE_VERSION = "1.0.0"

    const val DATABASE_SOURCE =
        "ITU-T E.164 national numbering plans + ITU-T E.212"

    /**
     * National significant number prefixes are used here.
     *
     * Egypt examples:
     * Domestic 010 -> national significant prefix 10
     * Domestic 011 -> national significant prefix 11
     * Domestic 012 -> national significant prefix 12
     * Domestic 015 -> national significant prefix 15
     *
     * The leading national trunk zero is not part of the
     * national significant number used by libphonenumber.
     */
    val records: List<ATSGlobalTelecomDatabase.OperatorRecord> =
        listOf(

            // -------------------------------------------------
            // EGYPT (+20)
            // -------------------------------------------------

            ATSGlobalTelecomDatabase.OperatorRecord(
                countryRegion = "EG",
                operatorName = "Vodafone Egypt",
                mcc = "602",
                mnc = "02",
                numberPrefixes = listOf("10"),
                source =
                    "ITU-T E.164 Egypt numbering plan 2014; " +
                    "ITU-T E.212 MNC allocation 2023",
                sourceUrl =
                    "https://www.itu.int/dms_pub/itu-t/opb/sp/" +
                    "T-SP-OB.1058-2014-OAS-PDF-E.pdf",
                verifiedDate = "2014-07-20",
                status =
                    ATSGlobalTelecomDatabase.RecordStatus.VERIFIED,
                notes =
                    "Domestic prefix 010. Historical original " +
                    "allocation. MCC/MNC 602-02 is separately " +
                    "listed by ITU. Does not identify the current " +
                    "carrier after number portability."
            ),

            ATSGlobalTelecomDatabase.OperatorRecord(
                countryRegion = "EG",
                operatorName = "Etisalat Egypt (e&)",
                mcc = "602",
                mnc = "03",
                numberPrefixes = listOf("11"),
                source =
                    "ITU-T E.164 Egypt numbering plan 2014; " +
                    "ITU-T E.212 MNC allocation 2023",
                sourceUrl =
                    "https://www.itu.int/dms_pub/itu-t/opb/sp/" +
                    "T-SP-OB.1058-2014-OAS-PDF-E.pdf",
                verifiedDate = "2014-07-20",
                status =
                    ATSGlobalTelecomDatabase.RecordStatus.VERIFIED,
                notes =
                    "Domestic prefix 011. Historical original " +
                    "allocation. MCC/MNC 602-03 is separately " +
                    "listed by ITU. Does not identify the current " +
                    "carrier after number portability."
            ),

            ATSGlobalTelecomDatabase.OperatorRecord(
                countryRegion = "EG",
                operatorName = "Orange Egypt (formerly Mobinil)",
                mcc = "602",
                mnc = "01",
                numberPrefixes = listOf("12"),
                source =
                    "ITU-T E.164 Egypt numbering plan 2014; " +
                    "ITU-T E.212 MNC allocation 2023",
                sourceUrl =
                    "https://www.itu.int/dms_pub/itu-t/opb/sp/" +
                    "T-SP-OB.1058-2014-OAS-PDF-E.pdf",
                verifiedDate = "2014-07-20",
                status =
                    ATSGlobalTelecomDatabase.RecordStatus.VERIFIED,
                notes =
                    "Domestic prefix 012 was published under " +
                    "Mobinil. Brand subsequently became Orange Egypt. " +
                    "MCC/MNC 602-01 is listed by ITU under Mobinil. " +
                    "Historical prefix allocation only."
            ),

            ATSGlobalTelecomDatabase.OperatorRecord(
                countryRegion = "EG",
                operatorName = "Telecom Egypt (WE)",
                mcc = null,
                mnc = null,
                numberPrefixes = listOf("15"),
                source =
                    "ITU Operational Bulletin 1130, Egypt " +
                    "number series assignment, 2017",
                sourceUrl =
                    "https://www.ituob.org/issues/1130-en/",
                verifiedDate = "2017-07-18",
                status =
                    ATSGlobalTelecomDatabase.RecordStatus.VERIFIED,
                notes =
                    "Domestic prefix 015. ITU published the " +
                    "15-series assignment for Telecom Egypt. " +
                    "MCC/MNC intentionally omitted here because " +
                    "this prefix-allocation source does not verify it."
            )
        )

    /**
     * All currently loaded operator records.
     */
    fun getAllOperators():
        List<ATSGlobalTelecomDatabase.OperatorRecord> {
        return records.toList()
    }

    /**
     * Returns records for one ISO region.
     */
    fun getOperatorsForCountry(
        regionCode: String
    ): List<ATSGlobalTelecomDatabase.OperatorRecord> {

        val region = regionCode.trim().uppercase()

        if (region.isBlank()) {
            return emptyList()
        }

        return records.filter {
            it.countryRegion.equals(region, ignoreCase = true)
        }
    }

    /**
     * Finds the longest matching verified national prefix.
     *
     * nationalNumber must be the national significant number,
     * as returned by libphonenumber.
     */
    fun findByPrefix(
        regionCode: String,
        nationalNumber: String
    ): ATSGlobalTelecomDatabase.OperatorRecord? {

        val region = regionCode.trim().uppercase()
        val number = nationalNumber.filter { it.isDigit() }

        if (region.isBlank() || number.isBlank()) {
            return null
        }

        return records
            .asSequence()
            .filter {
                it.countryRegion.equals(region, ignoreCase = true)
            }
            .filter {
                it.status ==
                    ATSGlobalTelecomDatabase.RecordStatus.VERIFIED
            }
            .filter { record ->
                record.numberPrefixes.any { prefix ->
                    val cleanPrefix = prefix.filter { it.isDigit() }
                    cleanPrefix.isNotBlank() &&
                        number.startsWith(cleanPrefix)
                }
            }
            .maxByOrNull { record ->
                record.numberPrefixes.maxOfOrNull {
                    it.filter { digit -> digit.isDigit() }.length
                } ?: 0
            }
    }

    fun getOperatorCount(): Int {
        return records.size
    }

    fun getCountryCountWithOperatorData(): Int {
        return records
            .map { it.countryRegion }
            .distinct()
            .size
    }
}
