package com.example.mutexa_be.service.parser;

import com.example.mutexa_be.entity.BankTransaction;
import com.example.mutexa_be.entity.MutationDocument;
import com.example.mutexa_be.entity.enums.MutationType;
import com.example.mutexa_be.entity.enums.TransactionCategory;
import com.example.mutexa_be.repository.BankTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class BcaImageParserService {

    private final BankTransactionRepository bankTransactionRepository;

    public List<BankTransaction> parseAndSave(MutationDocument document, String filePath) {
        log.info("Memulai ekstraksi Tesseract OCR untuk file BCA Scanner: {}", filePath);
        List<BankTransaction> transactions = new ArrayList<>();

        // 1. Inisialisasi Tesseract dengan Konfigurasi Presisi Tinggi untuk Tabel
        ITesseract tesseract = new Tesseract();
        
        // PENTING: Arahkan ke tempat program Tesseract diinstall.
        // Jika Anda install di "C:\Program Files\Tesseract-OCR", maka arahkan datapath ke "\tessdata" di dalamnya
        tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
        tesseract.setLanguage("eng"); 
        
        // --- KONFIGURASI TINGKAT DEWA UNTUK MEMBACA TABEL (SPASI TERJAGA) ---
        // Setting ini mempertahankan jarak spasi sehingga kolom Saldo dan Jumlah tidak menyatu
        tesseract.setTessVariable("preserve_interword_spaces", "1"); 
        // Mode 6: Assume a single uniform block of text. Ini ideal buat mutasi berderet panjang ke bawah
        tesseract.setPageSegMode(6); 

        try (PDDocument pdDocument = Loader.loadPDF(new File(filePath))) {
            PDFRenderer renderer = new PDFRenderer(pdDocument);
            
            for (int page = 0; page < pdDocument.getNumberOfPages(); page++) {
                log.info("Memproses halaman {} dengan Tesseract...", (page + 1));
                
                // 2. Rendering Hal PDF menjadi Gambar Resolusi Tinggi (300 DPI supaya hurufnya tebal)
                BufferedImage originalImage = renderer.renderImageWithDPI(page, 300);
                
                // 3. Pre-processing: Ubah menjadi Hitam Putih murni (Grayscale/Binarization)
                // Ini teknik rahasia agar Tesseract tidak bingung dengan artefak bayangan mesin scan BCA
                BufferedImage processedImage = preProcessImageForOcr(originalImage);

                // 4. Lakukan Analisa Teks
                String ocrResult = tesseract.doOCR(processedImage);
                log.info("Berhasil membaca teks halaman {}. Total baris teks didapatkan.", page + 1);

                // 5. Ubah teks Mentah tadi menjadi Entitas Database menggunakan Regex
                List<BankTransaction> pageTransactions = extractBankTransactions(ocrResult, document);
                transactions.addAll(pageTransactions);
            }

            if (!transactions.isEmpty()) {
                bankTransactionRepository.saveAll(transactions);
                log.info("Sukses menyimpan {} total transaksi dari PDF BCA.", transactions.size());
            } else {
                log.warn("Tidak dapat menemukan baris transaksi BCA berformat pada hasil OCR. Bisa jadi kualitas gambar terlalu sulit atau instalasi Tesseract kurang tepat.");
            }

        } catch (Exception e) {
            log.error("Terjadi kegagalan fatal saat Tesseract OCR berjalan: {}", e.getMessage(), e);
        }

        return transactions;
    }

    /**
     * Memperjelas kualitas gambar (Hitam & Putih) sebelum dilumat oleh Tesseract.
     * Gambar scan biasanya memiliki noise abu-abu di latar belakang.
     */
    private BufferedImage preProcessImageForOcr(BufferedImage original) {
        BufferedImage bWImage = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D graphics = bWImage.createGraphics();
        // Meningkatkan kontras hitam putih dengan memindahkannya ke tipe BINARY image
        graphics.drawImage(original, 0, 0, Color.WHITE, null);
        graphics.dispose();
        return bWImage;
    }

    /**
     * Ekstraktor Logika/Regex untuk memecah teks Tesseract (spasi terjaga) ke tabel Java (BCA Format).
     */
    private List<BankTransaction> extractBankTransactions(String ocrText, MutationDocument document) {
        List<BankTransaction> list = new ArrayList<>();
        String[] lines = ocrText.split("\n");

        // Regex BCA: "05/11     KETERANGAN MUTASI      1.250.000,00" atau sejenisnya
        // Contoh tebak pola: (DD/MM) + (Banyak Spasi & Keterangan) + (Angka)
        // Format angka Indonesia/Scan BCA bisa pakai koma(,) atau titik(.)
        Pattern patternDate = Pattern.compile("^\\s*(\\d{2}/\\d{2})\\s+(.*)");

        int year = document.getPeriodStart() != null ? document.getPeriodStart().getYear() : LocalDate.now().getYear();

        for (String line : lines) {
            // Bersihkan text (kadang Tesseract salah membaca huruf l, |, atau 1 yg aneh pada tepi kertas)
            line = line.replace("|", "").trim();
            if (line.isEmpty()) continue;

            Matcher matcher = patternDate.matcher(line);
            if (matcher.find()) {
                String dateStr = matcher.group(1); // "05/11"
                String remainingText = matcher.group(2).trim();

                try {
                    // Konversi Tanggal
                    int day = Integer.parseInt(dateStr.substring(0, 2));
                    int month = Integer.parseInt(dateStr.substring(3, 5));
                    LocalDate txDate = LocalDate.of(year, month, day);

                    // Memecah Keterangan dan Angka
                    // Karena `preserve_interword_spaces` dinyalakan, jarak antar kolom dipisah dengan spasi banyak (lebih dari 2)
                    String[] columns = remainingText.split(" {2,}");
                    
                    String rawDesc = remainingText; // Default jika gagal pecah kolom
                    BigDecimal amount = BigDecimal.ZERO;
                    MutationType type = MutationType.DB; // Asumsi Default

                    // Jika kita berhasil memisahkan kolom berdasar jarak spasi yang lebar
                    if (columns.length >= 2) {
                        // Kolom paling ujung terakhir biasanya nominal/jumlah atau saldo akhir.
                        // BCA punya kolom khusus "MUTASI" yg berisi nominal dengan DB/CR. Atau kadang CR ada di deskripsi.
                        
                        // Coba kumpulkan keterangan sampai ke depan angka
                        StringBuilder descBuilder = new StringBuilder();
                        for (int i = 0; i < columns.length; i++) {
                            String col = columns[i].trim();
                            // Kalau kolom mengandung angka yang panjang, asumsikan itu adalah nilai uang (Amount)
                            if (col.matches(".*\\d+[.,]\\d{2}.*") || col.matches(".*\\d{4,}.*")) {
                                // Ekstrak hanya digit angka
                                String cleanNum = col.replaceAll("[^\\d]", "");
                                if (!cleanNum.isEmpty()) {
                                    // Ambil 2 angka terakhir sebagai desimal
                                    amount = new BigDecimal(cleanNum).divide(new BigDecimal(100)); // Misal 1250000 -> 12500.00
                                }
                                
                                // Tipe Mutasi: tebak jika di teksnya atau di sekitarnya ada tulisan CR.
                                if (line.toUpperCase().contains(" CR ") || line.toUpperCase().contains(" CR")) {
                                    type = MutationType.CR;
                                } else {
                                    type = MutationType.DB; // Keluar
                                }
                            } else {
                                // Selain angka panjang berarti Keterangan (nama org, "TRANSFER E-BANKING", dsb)
                                descBuilder.append(col).append(" ");
                            }
                        }
                        
                        if (descBuilder.length() > 0) {
                            rawDesc = descBuilder.toString().trim();
                        }
                    }

                    // Build Objek
                    BankTransaction tx = BankTransaction.builder()
                            .mutationDocument(document)
                            .bankAccount(document.getBankAccount())
                            .transactionDate(txDate)
                            .rawDescription(rawDesc)
                            .normalizedDescription(rawDesc)
                            .mutationType(type)
                            .amount(amount)
                            .balance(BigDecimal.ZERO) // Optional
                            .category(TransactionCategory.UNCLASSIFIED)
                            .build();

                    // Generate MD5 Anti-Duplicate
                    String uniqueRawString = document.getId() + "_" + txDate.toString() + "_" + amount.toString() + "_" + type.name() + "_" + rawDesc;
                    tx.setDuplicateHash(generateMd5Hash(uniqueRawString));

                    // Hanya save jika transaksi ini belum pernah di-insert
                    if (!bankTransactionRepository.existsByDuplicateHash(tx.getDuplicateHash())) {
                        list.add(tx);
                    }

                } catch (Exception ignored) {
                    // Kalau salah baca baris, abaikan dan lanjut ke baris rekening bawahnya
                }
            }
        }
        return list;
    }

    private String generateMd5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 alg missing", e);
        }
    }
}