package com.earzuhal.Service;

import com.earzuhal.Model.Contract;
import com.earzuhal.Model.Petition;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

        String ownerFullName = buildFullName(
                contract.getUser().getFirstName(),
                contract.getUser().getLastName(),
                contract.getUser().getUsername());
        String dateStr = contract.getCreatedAt() != null
                ? contract.getCreatedAt().format(DATE_FORMATTER)
                : java.time.OffsetDateTime.now().format(DATE_FORMATTER);
        String contractId = String.format("EA-%06d", contract.getId());
        String hash = computeContractHash(contract);

        Context ctx = new Context(new Locale("tr", "TR"));
        ctx.setVariable("contract", contract);
        ctx.setVariable("ownerFullName", ownerFullName);
        ctx.setVariable("currentDate", dateStr);
        ctx.setVariable("contractId", contractId);
        ctx.setVariable("documentHash", hash);
        ctx.setVariable("hashShort", hash.length() >= 16 ? hash.substring(0, 16).toUpperCase() : hash.toUpperCase());
        ctx.setVariable("isApproved",
                "APPROVED".equals(contract.getStatus()) || "COMPLETED".equals(contract.getStatus()));

        String templateName = resolveContractTemplate(contract.getType());
        String html = templateEngine.process(templateName, ctx);
        byte[] raw = renderToPdf(html);

        String title = contract.getTitle() != null ? contract.getTitle() : "Sözleşme";
        String subject = typeLabelTr(contract.getType()) + " — " + contractId;
        return injectPdfMetadata(raw, title, ownerFullName, subject,
                "e-Arzuhal sözleşme hukuk dijital",
                hash, contractId);
    }

    public byte[] generatePetitionPdf(Petition petition) {
        log.debug("Generating PDF for petition id={}", petition.getId());

        String ownerFullName = buildFullName(
                petition.getUser().getFirstName(),
                petition.getUser().getLastName(),
                petition.getUser().getUsername());
        String dateStr = petition.getCreatedAt() != null
                ? petition.getCreatedAt().format(DATE_FORMATTER)
                : java.time.OffsetDateTime.now().format(DATE_FORMATTER);
        String petitionId = String.format("EAD-%06d", petition.getId());
        String hash = computePetitionHash(petition);

        Context ctx = new Context(new Locale("tr", "TR"));
        ctx.setVariable("petition", petition);
        ctx.setVariable("ownerFullName", ownerFullName);
        ctx.setVariable("currentDate", dateStr);
        ctx.setVariable("petitionId", petitionId);
        ctx.setVariable("documentHash", hash);
        ctx.setVariable("hashShort", hash.length() >= 16 ? hash.substring(0, 16).toUpperCase() : hash.toUpperCase());

        String html = templateEngine.process("pdf/petitions/dilekce", ctx);
        byte[] raw = renderToPdf(html);

        String title = petition.getKonu() != null ? petition.getKonu() : "Dilekçe";
        return injectPdfMetadata(raw, title, ownerFullName,
                "Dilekçe — " + petitionId,
                "e-Arzuhal dilekçe hukuk dijital",
                hash, petitionId);
    }

    // ── Rendering ───────────────────────────────────────────────────────────

    private byte[] renderToPdf(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            // PDF/A-1b (ISO 19005) — arşiv standardı, mahkemeye sunulabilir belge
            builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_1_B);
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

    // ── PDF Metadata (Apache PDFBox post-processing) ─────────────────────────

    private byte[] injectPdfMetadata(byte[] rawPdf, String title, String author,
                                     String subject, String keywords,
                                     String hash, String docId) {
        try (PDDocument doc = PDDocument.load(rawPdf);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle(title);
            info.setAuthor(author);
            info.setSubject(subject);
            info.setKeywords(keywords);
            info.setCreator("e-Arzuhal Dijital Hukuk Sistemi v1.0");
            info.setProducer("openhtmltopdf + Apache PDFBox");
            info.setCustomMetadataValue("DocumentId", docId);
            info.setCustomMetadataValue("DocumentHash", hash);
            info.setCustomMetadataValue("HashAlgorithm", "SHA-256");

            doc.save(out);
            log.debug("PDF metadata injected for docId={}", docId);
            return out.toByteArray();

        } catch (Exception e) {
            log.warn("PDF metadata injection failed, returning raw bytes: {}", e.getMessage());
            return rawPdf;
        }
    }

    // ── SHA-256 hash ─────────────────────────────────────────────────────────

    public String computeContractHash(Contract contract) {
        String input = contract.getId()
                + "|" + nullSafe(contract.getType())
                + "|" + nullSafe(contract.getTitle())
                + "|" + nullSafe(contract.getContent())
                + "|" + nullSafe(contract.getAmount())
                + "|" + (contract.getUser() != null ? nullSafe(contract.getUser().getUsername()) : "")
                + "|" + nullSafe(contract.getCounterpartyName());
        return sha256(input);
    }

    public String computePetitionHash(Petition petition) {
        String input = petition.getId()
                + "|" + nullSafe(petition.getKonu())
                + "|" + nullSafe(petition.getGovde())
                + "|" + (petition.getUser() != null ? nullSafe(petition.getUser().getUsername()) : "");
        return sha256(input);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 unavailable", e);
            return "hash-unavailable";
        }
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    // ── Font registration ────────────────────────────────────────────────────

    private void registerFonts(PdfRendererBuilder builder) {
        registerClasspathFont(builder, "/fonts/NotoSerif-Regular.ttf",
                "Noto Serif", 400, BaseRendererBuilder.FontStyle.NORMAL);
        registerClasspathFont(builder, "/fonts/NotoSerif-Bold.ttf",
                "Noto Serif", 700, BaseRendererBuilder.FontStyle.NORMAL);
        registerClasspathFont(builder, "/fonts/NotoSerif-Italic.ttf",
                "Noto Serif", 400, BaseRendererBuilder.FontStyle.ITALIC);
        registerClasspathFont(builder, "/fonts/NotoSerif-BoldItalic.ttf",
                "Noto Serif", 700, BaseRendererBuilder.FontStyle.ITALIC);

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

    // ── Template routing ─────────────────────────────────────────────────────

    private String resolveContractTemplate(String type) {
        if (type == null) return "pdf/contracts/genel_sozlesme";
        return switch (type.toLowerCase()) {
            case "kira_sozlesmesi", "rental"                    -> "pdf/contracts/kira_sozlesmesi";
            case "borc_sozlesmesi", "loan"                      -> "pdf/contracts/borc_sozlesmesi";
            case "hizmet_sozlesmesi", "service"                 -> "pdf/contracts/hizmet_sozlesmesi";
            case "satis_sozlesmesi", "sales"                    -> "pdf/contracts/satis_sozlesmesi";
            case "is_sozlesmesi", "employment"                  -> "pdf/contracts/is_sozlesmesi";
            case "vekaletname", "power_of_attorney"             -> "pdf/contracts/vekaletname";
            case "taahhutname", "commitment"                    -> "pdf/contracts/taahhutname";
            case "other", "diger", "genel_sozlesme", "general"  -> "pdf/contracts/genel_sozlesme";
            default                                             -> "pdf/contracts/genel_sozlesme";
        };
    }

    private String typeLabelTr(String type) {
        if (type == null) return "Genel Sözleşme";
        return switch (type.toLowerCase()) {
            case "kira_sozlesmesi", "rental"     -> "Kira Sözleşmesi";
            case "borc_sozlesmesi", "loan"       -> "Borç Sözleşmesi";
            case "hizmet_sozlesmesi", "service"  -> "Hizmet Sözleşmesi";
            case "satis_sozlesmesi", "sales"     -> "Satış Sözleşmesi";
            case "is_sozlesmesi", "employment"   -> "İş Sözleşmesi";
            case "vekaletname"                   -> "Vekaletname";
            case "taahhutname"                   -> "Taahhütname";
            default                              -> "Genel Sözleşme";
        };
    }

    private String buildFullName(String firstName, String lastName, String fallback) {
        if (firstName != null && lastName != null) return firstName + " " + lastName;
        if (firstName != null) return firstName;
        if (lastName != null) return lastName;
        return fallback != null ? fallback : "—";
    }
}
