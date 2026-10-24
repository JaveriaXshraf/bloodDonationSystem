// ============================================================
//  PdfHelper.java  –  Save donor / request receipts as PDF
//  Pure Java (no extra libraries). Files go to ./receipts/
// ============================================================

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PdfHelper {

    private static final Path RECEIPTS_DIR = Paths.get("receipts");
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private PdfHelper() {}

    /** Saves donor registration receipt; returns absolute path or null on failure. */
    public static String saveDonorReceipt(Donor d) {
        List<String> lines = new ArrayList<>();
        lines.add("BLOOD BANK MANAGEMENT SYSTEM");
        lines.add("DONOR REGISTRATION RECEIPT");
        lines.add("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")));
        lines.add("");
        lines.add("Full Name     : " + d.getName());
        lines.add("Age           : " + d.getAge());
        lines.add("Phone         : " + d.getPhone());
        lines.add("Email         : " + (d.getEmail() == null || d.getEmail().isBlank() ? "—" : d.getEmail()));
        lines.add("Blood Group   : " + d.getBloodGroup());
        lines.add("City          : " + nullToDash(d.getContactInfo().getCity()));
        lines.add("Address       : " + nullToDash(d.getContactInfo().getAddress()));
        lines.add("Eligible      : " + (d.isEligible() ? "Yes (18–65)" : "No"));
        lines.add("Donations     : " + d.getDonationCount());
        lines.add("");
        lines.add("Thank you for registering as a blood donor.");
        lines.add("Keep this PDF for your records.");

        String fileName = "donor_" + safeFilePart(d.getName()) + "_" + TS.format(LocalDateTime.now()) + ".pdf";
        return writePdf(RECEIPTS_DIR.resolve(fileName), "Donor Registration", lines);
    }

    /** Saves blood request receipt; returns absolute path or null on failure. */
    public static String saveBloodRequestReceipt(BloodRequest r) {
        List<String> lines = new ArrayList<>();
        lines.add("BLOOD BANK MANAGEMENT SYSTEM");
        lines.add("BLOOD REQUEST RECEIPT");
        lines.add("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")));
        lines.add("");
        if (r.getId() > 0) {
            lines.add("Request ID    : " + r.getId());
        }
        lines.add("Patient Name  : " + r.getPatientName());
        lines.add("Blood Needed  : " + r.getBloodGroupNeeded());
        lines.add("Hospital      : " + r.getHospital());
        lines.add("Contact Phone : " + r.getContactPhone());
        lines.add("Urgency       : " + r.getUrgency());
        lines.add("Status        : " + r.getStatus());
        lines.add("");
        lines.add("Your request has been recorded. Staff will match donors.");
        lines.add("Keep this PDF for your records.");

        String fileName = "request_" + safeFilePart(r.getPatientName()) + "_" + TS.format(LocalDateTime.now()) + ".pdf";
        return writePdf(RECEIPTS_DIR.resolve(fileName), "Blood Request", lines);
    }

    public static void openPdfIfPossible(String absolutePath) {
        if (absolutePath == null) return;
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(Paths.get(absolutePath).toFile());
            }
        } catch (IOException ex) {
            System.err.println("[PDF] Could not open file: " + ex.getMessage());
        }
    }

    private static String nullToDash(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    private static String safeFilePart(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_").replaceAll("_+", "_");
    }

    private static String writePdf(Path path, String title, List<String> lines) {
        try {
            Files.createDirectories(path.getParent());
            byte[] pdf = buildSimplePdf(title, lines);
            Files.write(path, pdf);
            return path.toAbsolutePath().toString();
        } catch (IOException e) {
            System.err.println("[PDF ERROR] " + e.getMessage());
            return null;
        }
    }

    /** Minimal PDF 1.4 document with one page of text lines. */
    private static byte[] buildSimplePdf(String title, List<String> lines) {
        StringBuilder stream = new StringBuilder();
        stream.append("BT\n");
        stream.append("/F1 16 Tf\n");
        stream.append("50 780 Td\n");
        stream.append("(").append(escapePdf(title)).append(") Tj\n");
        stream.append("/F1 11 Tf\n");
        stream.append("0 -22 Td\n");

        for (String line : lines) {
            stream.append("(").append(escapePdf(line)).append(") Tj\n");
            stream.append("0 -16 Td\n");
        }
        stream.append("ET\n");

        String streamBody = stream.toString();
        int streamLen = streamBody.getBytes(StandardCharsets.US_ASCII).length;

        List<String> objects = new ArrayList<>();
        objects.add("1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj\n");
        objects.add("2 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj\n");
        objects.add("3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                + "/Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>endobj\n");
        objects.add("4 0 obj<< /Length " + streamLen + " >>stream\n" + streamBody + "endstream\nendobj\n");
        objects.add("5 0 obj<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>endobj\n");

        StringBuilder pdf = new StringBuilder();
        pdf.append("%PDF-1.4\n");
        List<Long> offsets = new ArrayList<>();
        for (String obj : objects) {
            offsets.add((long) pdf.length());
            pdf.append(obj);
        }

        long xrefPos = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n");
        pdf.append("0000000000 65535 f \n");
        for (Long off : offsets) {
            pdf.append(String.format("%010d", off)).append(" 00000 n \n");
        }
        pdf.append("trailer<< /Size ").append(objects.size() + 1)
           .append(" /Root 1 0 R >>\n");
        pdf.append("startxref\n").append(xrefPos).append("\n%%EOF\n");

        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static String escapePdf(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("\r", "")
                .replace("\n", " ");
    }
}
