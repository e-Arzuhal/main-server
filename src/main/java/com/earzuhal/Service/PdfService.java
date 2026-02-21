package com.earzuhal.Service;

import com.earzuhal.Model.Contract;
import com.earzuhal.Model.Petition;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class PdfService {

    private static final Logger log = LoggerFactory.getLogger(PdfService.class);

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMMM yyyy", new Locale("tr", "TR"));

    private final TemplateEngine templateEngine;

    public PdfService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public byte[] generateContractPdf(Contract contract) {
        log.debug("Generating PDF for contract id={} type={}", contract.getId(), contract.getType());
        Context ctx = new Context(new Locale("tr", "TR"));
        ctx.setVariable("contract", contract);
        ctx.setVariable("ownerFullName", buildFullName(
                contract.getUser().getFirstName(),
                contract.getUser().getLastName(),
                contract.getUser().getUsername()
        ));
        ctx.setVariable("currentDate", contract.getCreatedAt() != null
                ? contract.getCreatedAt().format(DATE_FORMATTER)
                : java.time.OffsetDateTime.now().format(DATE_FORMATTER));
        ctx.setVariable("contractId", String.format("EA-%06d", contract.getId()));

        String templateName = resolveContractTemplate(contract.getType());
        String html = templateEngine.process(templateName, ctx);
        return renderToPdf(html);
    }

    public byte[] generatePetitionPdf(Petition petition) {
        log.debug("Generating PDF for petition id={}", petition.getId());
        Context ctx = new Context(new Locale("tr", "TR"));
        ctx.setVariable("petition", petition);
        ctx.setVariable("ownerFullName", buildFullName(
                petition.getUser().getFirstName(),
                petition.getUser().getLastName(),
                petition.getUser().getUsername()
        ));
        ctx.setVariable("currentDate", petition.getCreatedAt() != null
                ? petition.getCreatedAt().format(DATE_FORMATTER)
                : java.time.OffsetDateTime.now().format(DATE_FORMATTER));
        ctx.setVariable("petitionId", String.format("EAD-%06d", petition.getId()));

        String html = templateEngine.process("pdf/petitions/dilekce", ctx);
        return renderToPdf(html);
    }

    private byte[] renderToPdf(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerFonts(builder);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("PDF render failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF oluşturulurken hata oluştu: " + e.getMessage(), e);
        }
    }

    /**
     * Türkçe karakterleri (İ ı Ğ ğ Ş ş) desteklemek için font kaydeder.
     * Öncelik sırası:
     *   1. Classpath'teki Noto Serif (üretim/Docker ortamı)
     *   2. Windows sistem fontları – Times New Roman (geliştirme ortamı)
     *
     * Noto Serif için: src/main/resources/fonts/ altına
     *   NotoSerif-Regular.ttf ve NotoSerif-Bold.ttf dosyalarını ekleyin.
     * İndirme: https://fonts.google.com/specimen/Noto+Serif
     */
    private void registerFonts(PdfRendererBuilder builder) {
        // 1. Classpath fontları (üretim)
        registerClasspathFont(builder, "/fonts/NotoSerif-Regular.ttf",
                "Noto Serif", 400, BaseRendererBuilder.FontStyle.NORMAL);
        registerClasspathFont(builder, "/fonts/NotoSerif-Bold.ttf",
                "Noto Serif", 700, BaseRendererBuilder.FontStyle.NORMAL);

        // 2. Windows sistem fontları (geliştirme ortamı)
        String winFonts = "C:\\Windows\\Fonts\\";
        registerFileFont(builder, winFonts + "times.ttf",
                "Times New Roman", 400, BaseRendererBuilder.FontStyle.NORMAL);
        registerFileFont(builder, winFonts + "timesbd.ttf",
                "Times New Roman", 700, BaseRendererBuilder.FontStyle.NORMAL);
        registerFileFont(builder, winFonts + "timesi.ttf",
                "Times New Roman", 400, BaseRendererBuilder.FontStyle.ITALIC);
        registerFileFont(builder, winFonts + "timesbi.ttf",
                "Times New Roman", 700, BaseRendererBuilder.FontStyle.ITALIC);
    }

    private void registerFileFont(PdfRendererBuilder builder, String path,
                                   String family, int weight, BaseRendererBuilder.FontStyle style) {
        File f = new File(path);
        if (f.exists()) {
            builder.useFont(f, family, weight, style, true);
            log.debug("Registered file font: {}", path);
        }
    }

    private void registerClasspathFont(PdfRendererBuilder builder, String classpathPath,
                                        String family, int weight, BaseRendererBuilder.FontStyle style) {
        try (InputStream test = getClass().getResourceAsStream(classpathPath)) {
            if (test != null) {
                builder.useFont(() -> getClass().getResourceAsStream(classpathPath),
                        family, weight, style, true);
                log.debug("Registered classpath font: {}", classpathPath);
            }
        } catch (IOException e) {
            log.warn("Could not verify classpath font {}: {}", classpathPath, e.getMessage());
        }
    }

    private String resolveContractTemplate(String type) {
        if (type == null) return "pdf/contracts/genel_sozlesme";
        return switch (type.toLowerCase()) {
            case "kira_sozlesmesi", "rental"      -> "pdf/contracts/kira_sozlesmesi";
            case "borc_sozlesmesi", "other"       -> "pdf/contracts/borc_sozlesmesi";
            case "hizmet_sozlesmesi", "service"   -> "pdf/contracts/hizmet_sozlesmesi";
            case "satis_sozlesmesi", "sales"      -> "pdf/contracts/satis_sozlesmesi";
            case "is_sozlesmesi", "employment"    -> "pdf/contracts/is_sozlesmesi";
            case "vekaletname"                    -> "pdf/contracts/vekaletname";
            case "taahhutname"                    -> "pdf/contracts/taahhutname";
            default                               -> "pdf/contracts/genel_sozlesme";
        };
    }

    private String buildFullName(String firstName, String lastName, String fallback) {
        if (firstName != null && lastName != null) return firstName + " " + lastName;
        if (firstName != null) return firstName;
        if (lastName != null) return lastName;
        return fallback != null ? fallback : "—";
    }
}
