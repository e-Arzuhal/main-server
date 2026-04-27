package com.earzuhal.service;

import com.earzuhal.dto.explanation.AnalysisContextDto;
import com.earzuhal.dto.explanation.ClauseExplanationItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Analiz bağlamından (NLP + GraphRAG) sözleşme maddesi açıklamaları üretir.
 * GraphRAG bağlantısı olmadan da çalışır; statik hukuki referanslarla tamamlar.
 */
@Service
public class ExplanationService {

    /**
     * Sözleşme tipine göre temel hukuki referanslar (Türk mevzuatı).
     * Key = İngilizce sözleşme tipi, Value = Türkçe kanun referansı.
     */
    private static final Map<String, String> BASE_LAW_REFS = Map.of(
            "RENTAL",     "Türk Borçlar Kanunu Madde 299-378 (Kira Sözleşmesi)",
            "SALES",      "Türk Borçlar Kanunu Madde 207-281 (Satış Sözleşmesi)",
            "SERVICE",    "Türk Borçlar Kanunu Madde 393-447 (Hizmet Sözleşmesi)",
            "EMPLOYMENT", "4857 Sayılı İş Kanunu + Türk Borçlar Kanunu Madde 393",
            "NDA",        "Türk Borçlar Kanunu Madde 96 (Sır Saklama Yükümlülüğü)",
            "OTHER",      "Türk Borçlar Kanunu Genel Hükümler Madde 1-206"
    );

    /** Alan adına karşılık gelen spesifik kanun maddesi */
    private static final Map<String, String> FIELD_LAW_REFS = Map.of(
            "kira_bedeli",     "TBK Madde 343 (Kira Bedelinin Belirlenmesi)",
            "depozito",        "TBK Madde 342 (Güvence Bedeli / Depozito)",
            "sozlesme_suresi", "TBK Madde 300 (Kira Süresinin Belirlenmesi)",
            "faiz_orani",      "TBK Madde 88 (Faiz Oranı)",
            "tutar",           "TBK Madde 207 (Satış Bedeli)",
            "taraflar",        "TBK Madde 26 (Sözleşme Taraflarının Belirlenmesi)",
            "tarih",           "TBK Madde 1 (Sözleşmenin Kurulması)",
            "odeme_takvimi",   "TBK Madde 90 (Borçlunun Temerrüdü)",
            "ceza_maddesi",    "TBK Madde 179 (Ceza Koşulu)"
    );

    /**
     * Verilen analiz bağlamından madde açıklamaları üretir.
     *
     * @param context  Frontend'den gelen analiz özeti
     * @return Her madde için ClauseExplanationItem listesi
     */
    public List<ClauseExplanationItem> generate(AnalysisContextDto context) {
        List<ClauseExplanationItem> items = new ArrayList<>();

        String contractType = context.getContractType() != null ? context.getContractType() : "OTHER";
        String baseLaw = BASE_LAW_REFS.getOrDefault(contractType, BASE_LAW_REFS.get("OTHER"));
        Double confidence = context.getConfidence() != null ? context.getConfidence() : 0.0;
        Double completeness = context.getCompletenessScore() != null ? context.getCompletenessScore() : 0.0;

        // 1) GraphRAG önerilerinden açıklama üret
        if (context.getSuggestions() != null) {
            for (Map<String, Object> sug : context.getSuggestions()) {
                String fieldName  = getString(sug, "fieldName");
                String message    = getString(sug, "message");
                String necessity  = getString(sug, "necessity");
                String priority   = getString(sug, "priority");

                if (fieldName == null || fieldName.isBlank()) continue;

                String lawRef = FIELD_LAW_REFS.getOrDefault(fieldName, baseLaw);
                String addedBecause = buildReason(fieldName, necessity, contractType,
                        context.getContractTypeDisplay());
                String statsSupport = buildStatsSupport(fieldName, necessity, completeness);

                items.add(ClauseExplanationItem.builder()
                        .clause(fieldName)
                        .addedBecause(message != null && !message.isBlank() ? message : addedBecause)
                        .triggerInput(buildTriggerFromEntities(fieldName, context.getEntities()))
                        .lawReference(lawRef)
                        .statisticsSupport(statsSupport)
                        .confidenceScore(confidence)
                        .necessity(necessity != null ? necessity : "recommended")
                        .build());
            }
        }

        // 2) NLP entity'lerinden tespit edilen alanlar için açıklama üret (suggestion'da yoksa)
        if (context.getEntities() != null) {
            for (Map<String, Object> entity : context.getEntities()) {
                String text       = getString(entity, "text");
                String label      = getString(entity, "label");
                String mappedField = getString(entity, "mappedField");

                if (text == null || text.isBlank()) continue;
                // Aynı alan zaten suggestion'dan eklenmiş ise tekrar ekleme
                String fieldKey = mappedField != null ? mappedField : label;
                if (fieldKey == null) continue;
                boolean alreadyAdded = items.stream()
                        .anyMatch(i -> fieldKey.equalsIgnoreCase(i.getClause()));
                if (alreadyAdded) continue;

                String lawRef = FIELD_LAW_REFS.getOrDefault(fieldKey, baseLaw);

                items.add(ClauseExplanationItem.builder()
                        .clause(fieldKey)
                        .addedBecause("Metinde tespit edilen \"" + text + "\" ifadesi (" + labelTurkish(label) + ") bu alanı oluşturdu.")
                        .triggerInput(text)
                        .lawReference(lawRef)
                        .statisticsSupport("Otomatik tespit — kullanım istatistiği mevcut değil.")
                        .confidenceScore(confidence)
                        .necessity("detected")
                        .build());
            }
        }

        // 3) Hiç açıklama üretilemezse genel sözleşme açıklaması ekle
        if (items.isEmpty()) {
            items.add(ClauseExplanationItem.builder()
                    .clause("genel_hukumler")
                    .addedBecause("Sözleşme, " + displayType(contractType, context.getContractTypeDisplay())
                            + " kategorisinde oluşturulmuştur.")
                    .triggerInput("Kullanıcı metni")
                    .lawReference(baseLaw)
                    .statisticsSupport("Analiz bağlamı bulunamadı.")
                    .confidenceScore(confidence)
                    .necessity("required")
                    .build());
        }

        return items;
    }

    // ─── yardımcı metotlar ────────────────────────────────────────────────────

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private String buildReason(String field, String necessity, String contractType, String display) {
        String type = displayType(contractType, display);
        if ("required".equalsIgnoreCase(necessity)) {
            return type + " için zorunlu bir maddedir: " + field + ".";
        } else {
            return type + " sözleşmelerinde sıklıkla kullanılan önerilen bir maddedir: " + field + ".";
        }
    }

    private String buildStatsSupport(String field, String necessity, double completeness) {
        if ("required".equalsIgnoreCase(necessity)) {
            return "Zorunlu madde — benzer sözleşmelerin büyük çoğunluğunda yer alır.";
        }
        if (completeness > 75) {
            return "Yüksek tamamlanma skoru (" + (int) completeness + "/100) — bu madde sıklıkla kullanılıyor.";
        }
        return "Önerilen madde — benzer sözleşmelerde yaygın olarak yer alır.";
    }

    private String buildTriggerFromEntities(String fieldName, List<Map<String, Object>> entities) {
        if (entities == null) return null;
        for (Map<String, Object> e : entities) {
            String mapped = getString(e, "mappedField");
            String text   = getString(e, "text");
            if (fieldName.equalsIgnoreCase(mapped) && text != null) {
                return text;
            }
        }
        return null;
    }

    private String labelTurkish(String label) {
        if (label == null) return "varlık";
        return switch (label.toUpperCase()) {
            case "PERSON" -> "kişi adı";
            case "MONEY"  -> "para miktarı";
            case "DATE"   -> "tarih";
            case "ORG"    -> "kurum";
            case "GPE"    -> "yer";
            default       -> label.toLowerCase();
        };
    }

    private String displayType(String contractType, String display) {
        if (display != null && !display.isBlank()) return display;
        return switch (contractType) {
            case "RENTAL"     -> "kira_sozlesmesi";
            case "SALES"      -> "satis_sozlesmesi";
            case "SERVICE"    -> "hizmet_sozlesmesi";
            case "EMPLOYMENT" -> "is_sozlesmesi";
            case "NDA"        -> "gizlilik_sozlesmesi";
            default           -> "sozlesme";
        };
    }

}
