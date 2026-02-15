package com.earzuhal.dto.analysis;

import java.util.Map;

/**
 * main-server (İngilizce enum) <-> NLP/GraphRAG (Türkçe snake_case) mapping.
 */
public final class ContractTypeMapping {

    private ContractTypeMapping() {}

    private static final Map<String, String> TO_TURKISH = Map.of(
            "SALES",       "satis_sozlesmesi",
            "RENTAL",      "kira_sozlesmesi",
            "SERVICE",     "hizmet_sozlesmesi",
            "EMPLOYMENT",  "is_sozlesmesi",
            "NDA",         "gizlilik_sozlesmesi",
            "OTHER",       "diger"
    );

    private static final Map<String, String> TO_ENGLISH = Map.of(
            "satis_sozlesmesi",    "SALES",
            "kira_sozlesmesi",     "RENTAL",
            "hizmet_sozlesmesi",   "SERVICE",
            "is_sozlesmesi",       "EMPLOYMENT",
            "borc_sozlesmesi",     "OTHER",
            "vekaletname",         "OTHER",
            "taahhutname",         "OTHER",
            "gizlilik_sozlesmesi", "NDA",
            "diger",               "OTHER"
    );

    public static String toTurkish(String englishType) {
        return TO_TURKISH.getOrDefault(englishType, "diger");
    }

    public static String toEnglish(String turkishType) {
        return TO_ENGLISH.getOrDefault(turkishType, "OTHER");
    }
}
