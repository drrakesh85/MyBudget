package com.hackerai.mybudget.data

data class AccountCleanupResult(
    val totalRequested: Int,
    val matchedCount: Int,
    val unmatchedCount: Int,
    val removedAccountNames: List<String>,
    val unmatchedRequestedNames: List<String>,
    val remainingAccountCount: Int,
    val isDryRun: Boolean
)

object UnwantedAccountsData {
    val UNWANTED_ACCOUNT_NAMES = listOf(
        "\$ShoppingList", "JEDGE", "CSB", "8676", "7686", "543", "4351", "2799",
        "5181", "48", "2501", "3440", "6761", "JK-NSESMS-S", "CP-CDSLEV-S", "7784",
        "AD-SBIINB-S", "AD-FCSLTD-S", "2518", "7825", "AX-NSESMS", "AD-JTEDGE", "200",
        "JX-SWIGGY", "3002", "VA-YESBNK-S", "AD-UIICHO-S", "JK-MLKBKT-S", "JD-MLKBKT-S",
        "9730", "VK-KOTAKB-S", "5554", "8082", "AX-NBHOME-S", "VM-TNUCRD-S", "2025",
        "4386", "4022", "3063", "CP-FCSLTD-S", "740", "402", "JM-NSETRA", "JD-SBIUPI",
        "VK-SBIUPI", "TX-TNUCRD", "TX-TPPLAY", "JK-SBIINB", "AX-FCSLTD", "JX-INDANE-S",
        "2379", "1022", "360", "JD-SCBANK-S", "JM-FEDBNK-S", "AG-AIRINF", "VD-TATACD-S",
        "AX-ITDCPC-G", "VM-ITDCPC-G", "799", "AX-TNUCRD-S", "AX-FCSLTD-S", "AD-MYTNEU-S",
        "VM-MYTNEU-S", "VM-CDMILK-S", "JX-KOTAKB-S", "CP-NBEXAM-S", "4999", "2026",
        "81", "CP-FCSLTD", "VA-TNUCRD-S", "300", "9.19027E+11", "5000", "AD-SCBANK",
        "AD-ACKOGI", "AX-MYTNEU", "163", "VM-NSESMS-S", "AD-AIRBIL-S", "AD-TATACD-S",
        "400", "BW-TNUCRD-S", "1280", "JM-ZEPTON-S", "VA-FCSLTD-S", "JD-INDANE-S",
        "7298", "AD-TNUCRD-S", "354", "9.17666E+11", "973", "JM-TNUCRD-S", "AX-NSETRA",
        "8230", "AX-RBLCRD", "AX-TNUCRD", "1021", "2880", "8404", "TX-MYTNEU",
        "100", "BX-SBIINB", "167", "VK-MYTNEU-S", "TX-TNUCRD-S", "AX-JTEDGE-S",
        "JM-NSESMS-S", "198", "1608", "1025", "1219", "850", "3140", "395", "2059",
        "4282", "JM-NSETRA-S", "1698", "235", "1900", "5505", "AX-GUJGAS-S",
        "AX-JUSPAY-S", "4504", "1995", "AD-AIRBIL", "AX-RBLBNK", "VA-ICICIT",
        "1856", "2705", "JD-ICICIT", "555", "JK-BATAIn-S", "1643", "6422",
        "JD-PAYUIB", "485", "VK-YESBNK-S", "2164", "245", "3402", "32", "AX-NSESMS-S",
        "AX-DOMINO-S", "640", "2391", "275", "JM-NSESMS", "AD-VAAHAN", "AD-FCSLTD",
        "5602", "BT-MNGLpu-S", "120", "VM-TATACC-S", "VM-FCSLTD-S", "AD-NSETRA-S",
        "JM-SCBANK-S", "310", "AD-GUJGAS-S", "JK-NSETRA", "JK-NSESMS", "CP-ICICIL",
        "AX-GUJGAS", "5503", "JX-MYTNEU-S", "140", "210", "324", "110", "AX-CDSLEV-S",
        "JK-NSETRA-S", "JK-DGVCLG-S", "JD-DGVCLG-S", "CP-PPOOJA-S", "2000", "VM-FEDBNK-S",
        "JD-MYTNEU-S", "9004", "AX-NSETRA-S", "500", "105", "VM-ICICIT-S", "130",
        "508", "CP-JUSPAY-S", "9.19099E+11", "AD-JTEDGE-S", "JM-CLRNET-S", "170",
        "AX-MYTNEU-S", "600", "VM-DGVCLG-S", "AD-SCBANK-S", "VA-SCBANK-S", "155",
        "5603", "1645", "7600", "AX-FEDSCP-S", "VM-SCBANK-S", "9514", "390",
        "AD-NSESMS-S", "5604", "CP-TPOWER-S", "VA-SBIUPI", "JK-SBIUPI", "1035",
        "1130", "JM-RSHCLR-S", "VA-SCBANK", "1100", "TX-MYTNEU-S", "AD-FEDSCP-S",
        "AD-OneCrd-S", "TX-FEDSCP-S", "234", "1095", "6957", "570", "CP-NIALtd-S",
        "AD-ACKOGI-S", "AX-RBLCRD-S", "CP-MYTNEU-S", "1705", "VM-JUSPAY", "VM-SCBANK",
        "VM-SWIGGY", "133", "JX-BATAIn-S", "VD-FEDBNK", "CP-FEDSCP-S", "JM-DGVCLG-S",
        "320", "CP-SCBANK-S", "VM-KOTAKB-S", "VM-JTEDGE-S", "340", "150", "JK-SCBANK-S",
        "AX-SCBANK-S", "JD-BSELTD-S", "AD-PPOOJA-S", "9.19341E+11", "AX-MSEDCL-S",
        "JX-DGVCLG-S", "BG-CDSLEV-S", "5508", "250", "5202", "175", "2176", "7133",
        "3499", "JX-MSEDCL", "VM-TATACD-S", "JA-JioNew-S", "JD-JIOSVC-S", "189",
        "3025", "AX-BSELTD-S", "AD-KOTAKB-S", "AD-MNRERT-S", "190", "5219", "JD-BSELTD",
        "9000", "270", "JD-ICICIT-S", "JK-JUSPAY-S", "JM-ICICIT-S", "900", "5511",
        "230", "VM-IPRUMF-S", "AD-BATAIn-S", "VK-INDUSB", "CP-RBLCRD", "AX-TATACD-S",
        "9149", "240", "CP-BSELTD", "VM-BSELTD", "9.17566E+11", "9.19637E+11",
        "VM-BIGBKT-S", "JK-MYTNEU-S", "JX-TATACD-S", "5005", "JM-MYTNEU-S", "AX-IPRUMF-S",
        "3803", "AD-SWIGGY", "722", "220", "5510", "5509", "TM-ITDCPC-S", "JX-CANBNK-S",
        "AD-ICICIP", "JM-GUJGAS-S", "AX-IRCTCi-S", "9.18341E+11", "JM-KOTAKB-S",
        "5506", "AD-CDSLEV-S", "JD-KOTAKB-S", "JM-INDANE-S", "260", "350",
        "BV-MNGLpu-S", "4048", "JD-SBICRD-S", "VM-SBICRD-S", "VA-SBIINB-S", "5507",
        "VM-GUJGAS-S", "VD-SBICRD-S", "VA-BSELTD-S", "JM-SBICRD-S", "AD-SBIUPI-S",
        "JK-MYJPTR-T", "AX-KOTAKB-S", "VA-SBIUPI-S", "AX-SBICRD", "1000", "JX-BSELTD",
        "VM-SBICRD", "JX-RSHCLR-S", "1547", "VA-CNFTKT-S", "1236", "5941",
        "VM-FEDSCP-S", "57575711", "2503", "VA-RPZXPN", "CP-TPOWER", "JX-JTEDGE-S",
        "2932", "JX-ITDCPC-G", "40", "430", "1440", "BZ-SBIINB", "JD-FEDBNK-S",
        "BT-VAAHAN-G", "CP-JTEDGE-S", "8515", "JD-SBIUPI-S", "3800", "5504",
        "CP-JTEDGE", "JD-JTEDGE-S", "1818", "1012", "VM-JTEDGE", "JK-JTEDGE-S",
        "8325", "AD-NBHOME-S", "JX-SMCORP-S", "JD-SMCORP-S", "AX-MNRERT-S",
        "AX-TATACD", "700", "VA-ICICIT-S", "AD-ICICIT-S", "JK-SBIINB-S", "203",
        "VK-SBIINB-S", "VK-TATACD-S", "JK-MSEDCL", "6970", "VM-BATAIn-S", "123",
        "AD-DOMINO-S", "JX-CLRNET-S", "9.16003E+11", "AX-UIICHO-S", "302", "AD-SBIINB",
        "AX-OneCrd-S", "BG-QCAMZN-S", "CP-UIICHO-S", "2396", "225", "418", "280",
        "VM-SBIINB-S", "5566", "6299", "6999", "VM-KOTAKS", "950", "330", "AG-AIRINF-S",
        "AX-BIGBKT", "1799", "AX-SCBANK", "VK-FEDBNK-S", "AD-OneCrd-T", "160",
        "9157575022", "AX-MNRERT", "1361", "JX-SCBANK-S", "147", "CP-BIGBKT-S",
        "VM-COINDC-S", "VD-NSESMS-S", "5184", "1074", "VK-FCSLTD-S", "VA-MYTNEU-S",
        "1585", "9782", "8971", "9.18145E+11", "550", "AX-TPPLAY", "AD-TNUCRD",
        "610", "HDFC", "CSB Jupiter card"
    )
}

class UnwantedAccountCleanupManager(private val accountRepository: AccountRepository) {

    fun performCleanup(
        unwantedNames: List<String> = UnwantedAccountsData.UNWANTED_ACCOUNT_NAMES,
        isDryRun: Boolean = true
    ): AccountCleanupResult {
        // Build normalized lookup set (trimmed, case-insensitive)
        val normalizedUnwantedMap = mutableMapOf<String, String>()
        unwantedNames.forEach { name ->
            val trimmed = name.trim()
            if (trimmed.isNotBlank()) {
                normalizedUnwantedMap[trimmed.lowercase()] = trimmed
            }
        }

        val currentAccounts = accountRepository.accounts.value
        val originalCount = currentAccounts.size

        val matchingAccounts = mutableListOf<Account>()
        val remainingAccounts = mutableListOf<Account>()

        for (account in currentAccounts) {
            val normNickName = account.nickName.trim().lowercase()
            if (normalizedUnwantedMap.containsKey(normNickName)) {
                matchingAccounts.add(account)
            } else {
                remainingAccounts.add(account)
            }
        }

        val matchedNormSet = matchingAccounts.map { it.nickName.trim().lowercase() }.toSet()

        val matchedRequestedNames = mutableListOf<String>()
        val unmatchedRequestedNames = mutableListOf<String>()

        normalizedUnwantedMap.forEach { (normKey, originalReqName) ->
            if (matchedNormSet.contains(normKey)) {
                matchedRequestedNames.add(originalReqName)
            } else {
                unmatchedRequestedNames.add(originalReqName)
            }
        }

        if (!isDryRun) {
            // Backup current account JSON before cleanup
            val currentJson = accountRepository.getRawAccountsJson()
            if (!currentJson.isNullOrBlank()) {
                accountRepository.saveAccountsBackupJson(currentJson)
            }

            // Save filtered accounts
            accountRepository.saveAccountsList(remainingAccounts)
        }

        return AccountCleanupResult(
            totalRequested = unwantedNames.distinctBy { it.trim().lowercase() }.size,
            matchedCount = matchingAccounts.size,
            unmatchedCount = unmatchedRequestedNames.size,
            removedAccountNames = matchingAccounts.map { it.nickName },
            unmatchedRequestedNames = unmatchedRequestedNames,
            remainingAccountCount = if (isDryRun) (originalCount - matchingAccounts.size) else remainingAccounts.size,
            isDryRun = isDryRun
        )
    }
}
