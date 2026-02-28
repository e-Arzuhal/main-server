package com.earzuhal.dto.explanation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bir sözleşme maddesinin neden eklendiğini ve hangi hukuki dayanağa sahip
 * olduğunu açıklayan metadata nesnesi.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClauseExplanationItem {

    /** Madde adı / alan adı (ör. "kira_bedeli", "depozito") */
    private String clause;

    /** Bu maddenin neden eklendiğinin Türkçe açıklaması */
    private String addedBecause;

    /** Kullanıcının metnindeki hangi ifadenin bu maddeyi tetiklediği */
    private String triggerInput;

    /** Dayandığı kanun maddesi (ör. "TBK Madde 342 (Depozito)") */
    private String lawReference;

    /** Bu maddenin istatistiksel yaygınlık bilgisi (ör. "%87 kullanım oranı") */
    private String statisticsSupport;

    /** NLP güven skoru (0.0 – 1.0) */
    private Double confidenceScore;

    /** "required" | "recommended" | "optional" */
    private String necessity;
}
