package com.earzuhal.dto.analysis;

import java.util.Map;

/**
 * main-server (İngilizce enum) <-> NLP/GraphRAG (Türkçe snake_case) mapping.
 */
public final class ContractTypeMapping {

    private ContractTypeMapping() {}

    private static final Map<String, String> TO_TURKISH = Map.ofEntries(
            Map.entry("SALES",       "satis_sozlesmesi"),
            Map.entry("RENTAL",      "kira_sozlesmesi"),
            Map.entry("SERVICE",     "hizmet_sozlesmesi"),
            Map.entry("EMPLOYMENT",  "is_sozlesmesi"),
            Map.entry("LOAN",        "borc_sozlesmesi"),
            Map.entry("POWER_OF_ATTORNEY", "vekaletname"),
            Map.entry("COMMITMENT",  "taahhutname"),
            Map.entry("SURETY",      "kefalet_sozlesmesi"),
            Map.entry("NDA",         "gizlilik_sozlesmesi"),
            Map.entry("OTHER",       "diger")
    );

    private static final Map<String, String> TO_ENGLISH = Map.ofEntries(
            Map.entry("satis_sozlesmesi",    "SALES"),
            Map.entry("kira_sozlesmesi",     "RENTAL"),
            Map.entry("hizmet_sozlesmesi",   "SERVICE"),
            Map.entry("is_sozlesmesi",       "EMPLOYMENT"),
            Map.entry("borc_sozlesmesi",     "LOAN"),
            Map.entry("vekaletname",         "POWER_OF_ATTORNEY"),
            Map.entry("taahhutname",         "COMMITMENT"),
            Map.entry("kefalet_sozlesmesi",  "SURETY"),
            Map.entry("gizlilik_sozlesmesi", "NDA"),
            Map.entry("diger",               "OTHER")
    );

    public static String toTurkish(String englishType) {
        if (englishType == null) return "diger";
        return TO_TURKISH.getOrDefault(englishType, "diger");
    }

    public static String toEnglish(String turkishType) {
        if (turkishType == null) return "OTHER";
        return TO_ENGLISH.getOrDefault(turkishType, "OTHER");
    }
}
