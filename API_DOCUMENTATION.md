# API Documentation - MUTEXA

Dokumen ini berisi daftar endpoint API yang tersedia di project Mutexa. Anda dapat meng-copy perintah `cURL` di bawah ini dan menjalankannya langsung di terminal atau meng-import-nya ke dalam **Postman / Bruno**.

Semua endpoint berjalan di: `http://localhost:9091`

---

## 1. Document Management

Terkait dengan pengunggahan dokumen PDF atau Gambar Scan untuk di-parse.

### 1.1 Upload Dokumen Mutasi

Mengunggah file mutasi rekening (PDF, JPG, PNG). Sistem akan otomatis mendeteksi apakah itu file PDF Teks atau gambar/scan.

- **Endpoint:** `POST /api/documents/upload`
- **Content-Type:** `multipart/form-data`

**Parameter Form-Data:**

- `file` (File) : File yang mau diupload (.pdf, .jpg, .png)
- `bankName` (Text) : Nama Bank (contoh: "BRI" atau "BCA")
- `accountNumber` (Text) : Nomor rekening nasabah
- `accountName` (Text) : Nama nasabah pemilik rekening

**Contoh cURL (BRI - Teks PDF Digital):**
Pastikan Anda mengubah path `@/path/to/your/file.pdf` dengan lokasi rill file di komputer Anda.

```bash
curl --location 'http://localhost:9091/api/documents/upload' \
--form 'file=@"C:/Users/agung/Documents/PDP_BCA_Finance/MutexaApp/Novalino/Mutasi Rek BRI Lisdri.pdf"' \
--form 'bankName="BRI"' \
--form 'accountNumber="664301022530536"' \
--form 'accountName="LISDRI SUSTIWI"'
```

**Contoh cURL (BCA - Scan/Image PDF untuk diproses via AI Ollama OCR):**
Ubah nama file dan data dummy account di bawah sesuai dengan file BCA yang Anda miliki.

```bash
curl --location 'http://localhost:9091/api/documents/upload' \
--form 'file=@"C:/Users/agung/Documents/PDP_BCA_Finance/MutexaApp/Novalino/NAMA_FILE_BCA_ANDA.pdf"' \
--form 'bankName="BCA"' \
--form 'accountNumber="1234567890"' \
--form 'accountName="NAMA NASABAH BCA"'
```

**Contoh Response Sukses (201 Created):**

```json
{
  "success": true,
  "message": "Dokumen Mutasi Rek BRI Lisdri.pdf berhasil diupload dan sedang diproses.",
  "code": 201,
  "data": {
    "documentId": "c4d3r4-f9eb-4a1b-90f7-ebf3453xca31",
    "fileName": "Mutasi Rek BRI Lisdri.pdf",
    "status": "UPLOADED",
    "fileType": "PDF_DIGITAL",
    "accountName": "LISDRI SUSTIWI",
    "accountNumber": "664301022530536"
  },
  "timestamp": "2026-03-16T10:00:00Z"
}
```

**Contoh Response Gagal Validasi (400 Bad Request):**

```json
{
  "success": false,
  "message": "Format file harus PDF, PNG, atau JPG.",
  "data": null,
  "code": 400,
  "timestamp": "2026-03-16T10:05:00Z"
}
```

---

_Catatan: File ini akan terus ditambahkan secara otomatis jika ada endpoint baru._
