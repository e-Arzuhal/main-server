package com.earzuhal.dto.contract;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * GET /api/contracts/{id}/pdf-confirm yanıtı.
 *
 * Frontend bu veriyi bir onay dialogunda gösterir:
 * "Bu bilgilerle PDF oluşturulacak — doğru mu?"
 * Kullanıcı onaylarsa /pdf endpoint'i çağrılır.
 */
@Data
@Builder
public class PdfConfirmResponse {

    private Long contractId;
    private String contractType;
    private String title;
    private String status;

    private PartyInfo owner;
    private PartyInfo counterparty;

    private String amount;
    private boolean amountPresent;

    /** İçeriğin ilk 300 karakteri — kullanıcı ne oluşturulacağını görmeli */
    private String contentPreview;
    private int contentLength;

    /**
     * NLP veya kullanıcı girdisinden kaynaklı olası sorunlar.
     * Boşsa her şey yolunda demektir.
     */
    private List<String> warnings;

    /** warnings boş VE zorunlu alanlar doluysa true */
    private boolean readyForPdf;

    @Data
    @Builder
    public static class PartyInfo {
        private String displayName;
        private String role;
        /** Karşı taraf için her zaman maskeli (123***01), sahip için null */
        private String tcMasked;
    }
}
