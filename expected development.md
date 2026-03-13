# Expected Development Plan - MUTEXA

## 1. Tujuan Fase 1

Fase 1 difokuskan untuk membangun aplikasi web analisis mutasi bank **untuk 1 rekening dan 1 bank terlebih dahulu** (contoh: BCA) dengan target:

- membaca mutasi 3 bulan
- menampilkan dashboard analitik seperti mockup yang telah disepakati
- mendukung proses parsing dari dokumen mutasi
- menyediakan fitur exclusion rule agar transaksi tertentu tidak ikut dihitung
- menghasilkan detail transaksi dan ringkasan analitik yang konsisten

Fase 1 **tidak mencakup**:

- multi rekening
- deteksi transfer antar rekening sendiri lintas bank
- rule engine kompleks berbasis hubungan rekening
- workflow approval berlapis
- AI generatif untuk narasi analisis

---

## 2. Prinsip Implementasi

Agar development aman, cepat, dan minim error, implementasi akan mengikuti prinsip berikut:

1. **Backend-first contract**
   - API dan struktur data distabilkan lebih dulu sebelum frontend lengkap dikerjakan.
2. **Dual input support sejak fase 1**
   - sistem wajib menerima **PDF digital** dan **image / scan** sejak awal, karena keduanya termasuk kebutuhan inti.
3. **Staging pipeline parsing**
   - upload -> deteksi tipe dokumen -> ekstraksi -> normalisasi -> klasifikasi -> exclusion -> agregasi -> dashboard.
4. **Rule-based lebih dulu, AI-assisted dengan validasi ketat**
   - kategori seperti pajak, bunga, admin, income, anomali dimulai dari rules yang deterministik, sedangkan OCR/vision AI hanya dipakai untuk ekstraksi dokumen image lalu diverifikasi lagi di backend.
5. **Idempotent processing**
   - file yang sama tidak menghasilkan duplikasi transaksi.
6. **Observability sederhana sejak awal**
   - logging, audit upload, status parsing, dan error handling wajib ada.
7. **Schema management mengikuti project existing**
   - untuk tahap development saat ini mengikuti **JPA + Hibernate** yang sudah digunakan project, bukan menambah migration tool baru di tengah jalan.

---

## 3. Arsitektur Solusi

## 3.1 Arsitektur Umum

```text
Angular 21 Frontend
        |
        v
Spring Boot REST API
        |
        +--> SQL Server
        |
        +--> PDF Parser (PDFBox)
        |
        +--> OCR Service (Ollama / Tesseract fallback)
        |
        +--> Export Service (Excel)
```

## 3.2 Alur Data Utama

1. User upload file mutasi.
2. Backend mendeteksi tipe file:
   - PDF digital
   - image / scan
3. Jika PDF digital:
   - ekstraksi teks menggunakan parser PDF.
4. Jika image / scan:
   - preprocessing image
   - OCR / vision extraction
5. Hasil ekstraksi diubah ke format transaksi baku.
6. Sistem menyimpan transaksi mentah dan transaksi ternormalisasi.
7. Exclusion rule diterapkan.
8. Engine agregasi membentuk dashboard summary.
9. Frontend memanggil endpoint dashboard dan menampilkan visualisasi.

---

## 4. Teknologi yang Direkomendasikan

## 4.1 Backend

- **Java 21**
- **Spring Boot 4.0.3**
- **Spring Web**
- **Spring Validation**
- **Spring Data JPA**
- **Hibernate** sebagai ORM sekaligus schema management yang mengikuti konfigurasi project saat ini
- **Spring Security** jika login dibutuhkan sejak awal
- **Maven**
- **MapStruct** untuk mapping DTO
- **Lombok** untuk mengurangi boilerplate
- **Apache PDFBox** untuk PDF digital
- **Apache POI** untuk export Excel
- **Micrometer + Actuator** opsional untuk health monitoring

Catatan implementasi schema:

- untuk development plan ini, pengelolaan schema mengikuti pendekatan project existing melalui **JPA/Hibernate**
- konfigurasi seperti `spring.jpa.hibernate.ddl-auto` harus dikontrol dengan disiplin sesuai environment
- bila nanti project masuk fase stabilisasi/production hardening, migration tool terpisah dapat dipertimbangkan, tetapi **bukan baseline plan saat ini**

## 4.2 Database

- **Microsoft SQL Server**
- **SQL Server Developer Edition** untuk development
- **SQL Server Express** bisa dipakai jika resource terbatas

## 4.3 OCR / AI

### Jalur PDF Digital

- **Apache PDFBox** untuk PDF digital
- dipakai untuk dokumen yang memiliki text layer dan tetap menjadi jalur paling akurat

### Jalur Image / Scan

- **Ollama** sebagai local AI runtime untuk vision-based extraction
- Model awal yang direkomendasikan:
  - `llama3.2-vision`
  - alternatif ringan: `llava`
- **Tesseract OCR** disiapkan sebagai fallback teknis jika hasil model vision tidak konsisten pada dokumen tertentu

### Keputusan fase 1

- **PDF digital wajib didukung**
- **image / scan juga wajib didukung**
- karena itu backend harus memiliki **dua pipeline parsing** yang sama-sama aktif sejak fase 1

## 4.4 Frontend

- **Angular 21**
- **TypeScript**
- **Angular standalone architecture**
- **PrimeNG** untuk tabel, form, dialog
- **ApexCharts** untuk chart
- **RxJS** untuk aliran data
- **Angular HttpClient** untuk konsumsi API
- **Angular Reactive Forms** untuk filter dan exclusion input

## 4.5 Tooling Tambahan

- **Git + GitHub**
- **Postman / Bruno** untuk uji API
- **Docker Desktop** opsional untuk menjalankan service pendukung
- **Swagger / OpenAPI** untuk dokumentasi endpoint

---

## 5. Ruang Lingkup Fitur Fase 1

## 5.1 Input

- upload 1 file mutasi
- format yang **wajib diterima** pada fase 1:
  - PDF digital
  - image JPG / PNG / scan dokumen
- input data rekening:
  - nama nasabah
  - nomor rekening
  - bank
  - periode mutasi

## 5.2 Output Dashboard

### Ringkasan Saldo

- rata-rata credit 3 bulan
- rata-rata debit 3 bulan
- rata-rata saldo per hari
- total credit 3 bulan
- total debit 3 bulan

### Summary Per Bulan

- periode
- saldo akhir
- total credit
- total debit
- freq credit
- freq debit

### Analitik

- top 10 credit amount
- top 10 debit amount
- top 10 credit frequency
- top 10 debit frequency
- anomali credit
- anomali debit
- income
- pajak
- bunga pinjaman

### Detail

- tanggal
- keterangan
- flag (`CR` / `DB`)
- jumlah
- export Excel

### Exclusion

- exclude berdasarkan nama
- exclude berdasarkan nomor rekening
- exclude berdasarkan keyword

---

## 6. Desain Modul Backend

## 6.1 Modul yang Disarankan

### A. `document` module

Tanggung jawab:

- upload file
- validasi tipe file
- simpan metadata file
- trigger parsing pipeline

### B. `parser` module

Tanggung jawab:

- ekstrak teks dari PDF digital
- ekstrak teks dari image / scan
- hasilkan struktur raw line items

### C. `normalization` module

Tanggung jawab:

- parsing tanggal
- parsing nominal
- identifikasi flag credit / debit
- normalisasi deskripsi transaksi
- mapping ke model transaksi baku

### D. `classification` module

Tanggung jawab:

- mendeteksi income
- mendeteksi pajak
- mendeteksi bunga pinjaman
- mendeteksi admin / biaya
- mendeteksi anomali

### E. `exclusion` module

Tanggung jawab:

- menyimpan exclusion rules
- memeriksa apakah transaksi dikecualikan
- memberi flag `excluded=true/false`

### F. `analytics` module

Tanggung jawab:

- summary per bulan
- top 10 amount
- top 10 frequency
- aggregate total dan average

### G. `export` module

Tanggung jawab:

- export transaksi ke Excel

---

## 7. Struktur Data dan Tabel Database

## 7.1 Tabel Utama

### `accounts`

Menyimpan identitas rekening yang sedang dianalisis.

Field minimal:

- `id`
- `account_name`
- `account_number`
- `bank_code`
- `created_at`

### `uploaded_documents`

Menyimpan histori upload file.

Field minimal:

- `id`
- `account_id`
- `original_filename`
- `stored_filename`
- `file_type`
- `document_source` (`PDF`, `IMAGE`)
- `processing_status` (`UPLOADED`, `PARSING`, `PARSED`, `FAILED`)
- `error_message`
- `created_at`

### `transactions`

Tabel inti semua transaksi ternormalisasi.

Field minimal:

- `id`
- `document_id`
- `account_id`
- `transaction_date`
- `description`
- `amount`
- `flag` (`CR`, `DB`)
- `balance_after` nullable
- `normalized_description`
- `category`
- `subcategory`
- `is_anomaly`
- `is_excluded`
- `exclusion_reason`
- `raw_text`
- `created_at`

### `exclusion_rules`

Field minimal:

- `id`
- `account_id`
- `rule_type` (`NAME`, `ACCOUNT_NUMBER`, `KEYWORD`)
- `rule_value`
- `is_active`
- `notes`
- `created_at`

### `processing_logs`

Opsional tetapi sangat disarankan.

Field minimal:

- `id`
- `document_id`
- `step_name`
- `status`
- `message`
- `created_at`

---

## 8. Best Practice Parsing dan OCR

## 8.1 Urutan Strategi Parsing

### Prioritas 1: PDF digital

Best practice:

- cek apakah file PDF memiliki selectable text
- jika ya, gunakan parser PDF langsung
- hindari OCR jika teks PDF sudah tersedia

Alasan:

- paling akurat
- paling cepat
- paling murah
- paling minim error

### Prioritas 2: Image / Scan

Jika tidak ada text layer:

- lakukan preprocessing
- kirim ke OCR / vision service
- validasi hasil OCR sebelum simpan

## 8.2 Preprocessing untuk OCR

Jika memakai image / scan:

- grayscale
- thresholding
- deskew jika gambar miring
- crop margin berlebih jika perlu
- resize bila resolusi terlalu kecil

## 8.3 Ollama Strategy

Ollama dipakai **bukan sebagai sumber kebenaran utama**, tetapi sebagai alat bantu ekstraksi saat dokumen bukan PDF digital.

Best practice:

- gunakan prompt yang ketat
- minta output JSON sederhana
- jangan langsung percaya 100%
- lakukan validasi backend setelah AI mengembalikan hasil

Contoh target struktur hasil OCR / vision:

```json
{
  "accountName": "...",
  "accountNumber": "...",
  "period": "2025-12 to 2026-02",
  "transactions": [
    {
      "date": "2025-12-20",
      "description": "Transfer BRI - PONATIN",
      "flag": "CR",
      "amount": 45993000
    }
  ]
}
```

## 8.4 Validasi Hasil OCR

Semua hasil OCR harus divalidasi:

- tanggal valid
- nominal valid
- flag hanya `CR` atau `DB`
- tidak ada nominal negatif jika desain sistem tidak mengizinkan
- data duplikat dicegah
- deskripsi kosong diberi treatment khusus

## 8.5 Rekomendasi Praktis Fase 1

Agar tetap realistis namun sesuai kebutuhan bisnis:

- **wajib**: dukung PDF digital sejak awal
- **wajib**: dukung image / scan sejak awal
- **wajib**: siapkan validasi hasil OCR agar data image tidak langsung dipercaya mentah
- **teknik minim error**: implementasikan parser PDF dan parser image sebagai dua alur terpisah tetapi berakhir pada model transaksi normalisasi yang sama
- **fallback teknis**: jika hasil Ollama untuk dokumen image tidak konsisten, gunakan kombinasi preprocessing + Tesseract atau lakukan status `NEEDS_REVIEW`, tetapi kemampuan menerima image tetap harus ada di fase 1

---

## 9. Desain Klasifikasi Transaksi

## 9.1 Pendekatan Fase 1

Gunakan **rule-based classification** terlebih dahulu.

Contoh rule:

- deskripsi mengandung `PAJAK` -> kategori `PAJAK`
- deskripsi mengandung `BUNGA` -> kategori `BUNGA_PINJAMAN`
- deskripsi mengandung `BIAYA ADMIN`, `BIAYA TRANSFER`, `ADMIN` -> kategori `BIAYA`
- credit dengan nominal besar dan pola tertentu -> kandidat `INCOME`

## 9.2 Income

Untuk fase awal, income ditentukan dengan pendekatan praktis:

- hanya transaksi `CR`
- tidak termasuk transaksi yang sudah di-exclude
- tidak termasuk pajak / bunga / pembalikan jika ada rule
- dapat memakai daftar keyword whitelist / blacklist

## 9.3 Anomali

Gunakan rule sederhana agar konsisten:

- `Anomali Credit`: transaksi credit di atas threshold tertentu
- `Anomali Debit`: transaksi debit di atas threshold tertentu

Threshold dibuat configurable, misalnya:

- credit anomaly > 50 juta
- debit anomaly > 10 juta

Threshold jangan di-hardcode permanen di UI; simpan di backend config.

---

## 10. Perhitungan Dashboard

## 10.1 Ringkasan Saldo

- `Rata-rata Credit (3 bln)` = total credit 3 bulan / jumlah bulan yang dianalisis
- `Rata-rata Debit (3 bln)` = total debit 3 bulan / jumlah bulan yang dianalisis
- `Rata-rata Saldo Per Hari` = total saldo harian / jumlah hari data tersedia
- `Total Credit (3 bln)` = total semua transaksi `CR` non-excluded
- `Total Debit (3 bln)` = total semua transaksi `DB` non-excluded

## 10.2 Summary Per Bulan

Per bulan hitung:

- saldo akhir
- total credit
- total debit
- frekuensi credit
- frekuensi debit

## 10.3 Top 10 Amount

- urutkan transaksi berdasarkan nominal terbesar
- pisahkan `CR` dan `DB`
- ambil 10 teratas

## 10.4 Top 10 Frequency

- group by `normalized_description`
- hitung frekuensi
- urutkan dari tertinggi

Best practice:

- gunakan `normalized_description` agar variasi kecil teks tidak memecah grup terlalu banyak

---

## 11. Strategi Exclusion

## 11.1 Jenis Rule

- `NAME`
- `ACCOUNT_NUMBER`
- `KEYWORD`

## 11.2 Cara Kerja

Setelah transaksi berhasil dinormalisasi:

1. cek semua rule aktif
2. jika match, beri `is_excluded = true`
3. isi `exclusion_reason`
4. transaksi tetap disimpan, tetapi tidak ikut dashboard utama

## 11.3 Kenapa transaksi tetap disimpan?

Agar:

- audit trail tetap lengkap
- user masih bisa melihat transaksi asli
- exclusion bisa diubah tanpa upload ulang file

---

## 12. Desain API Backend

## 12.1 API Upload

- `POST /api/documents/upload`
- `GET /api/documents/{id}`
- `GET /api/documents/{id}/status`

## 12.2 API Transactions

- `GET /api/transactions`
- `GET /api/transactions/export`

Filter yang disarankan:

- periode
- flag
- excluded / non-excluded
- keyword pencarian

## 12.3 API Dashboard

- `GET /api/dashboard/summary-cards`
- `GET /api/dashboard/monthly-summary`
- `GET /api/dashboard/top-credit-amount`
- `GET /api/dashboard/top-debit-amount`
- `GET /api/dashboard/top-credit-frequency`
- `GET /api/dashboard/top-debit-frequency`
- `GET /api/dashboard/anomaly-credit`
- `GET /api/dashboard/anomaly-debit`
- `GET /api/dashboard/income`
- `GET /api/dashboard/pajak`
- `GET /api/dashboard/interest`

## 12.4 API Exclusion

- `GET /api/exclusions`
- `POST /api/exclusions`
- `PUT /api/exclusions/{id}`
- `DELETE /api/exclusions/{id}`
- `POST /api/exclusions/recalculate`

Best practice:

- sediakan endpoint recalculate agar dashboard diperbarui setelah rule berubah

---

## 13. Desain Frontend

## 13.1 Halaman Minimum

### A. Upload Page

- upload file
- input metadata rekening
- tampilkan status parsing

### B. Dashboard Page

- cards summary
- chart tren bulanan
- donut pemasukan vs pengeluaran
- summary per bulan
- tabel top 10
- tabel anomali
- tabel income / pajak / bunga
- tabel detail transaksi
- tombol export Excel

### C. Exclusion Management Page / Drawer

- tambah rule exclusion
- edit rule
- hapus rule
- jalankan recalculation

## 13.2 Komponen Frontend yang Disarankan

- `summary-cards`
- `monthly-trend-chart`
- `income-vs-expense-chart`
- `monthly-summary-table`
- `top-amount-table`
- `top-frequency-table`
- `anomaly-table`
- `transaction-table`
- `exclusion-rule-form`

## 13.3 Best Practice Frontend

- gunakan typed models untuk semua response API
- pisahkan service API per domain
- gunakan loading state dan empty state
- hindari logika perhitungan berat di frontend
- frontend hanya konsumsi hasil agregasi backend

---

## 14. Urutan Implementasi yang Direkomendasikan

## Tahap 1 - Fondasi Project

1. finalisasi scope fase 1
2. sinkronisasi dengan struktur backend existing Spring Boot 4.0.3
3. setup SQL Server
4. setup schema management berbasis JPA/Hibernate sesuai project existing
5. setup environment config dev
6. setup base exception handling
7. setup logging dasar

## Tahap 2 - Model Data dan Schema Management

1. buat schema tabel utama
2. buat entity JPA
3. buat repository layer
4. siapkan data type dan enum utama
5. uji koneksi database dan sinkronisasi schema Hibernate

## Tahap 3 - Upload dan Document Management

1. endpoint upload file
2. simpan metadata upload
3. validasi ukuran dan format file
4. simpan file ke storage lokal sementara
5. status processing dokumen
6. deteksi tipe dokumen `PDF_DIGITAL` atau `IMAGE_SCAN`

## Tahap 4 - Parsing PDF Digital

1. deteksi PDF yang memiliki text layer
2. ekstraksi teks via PDFBox
3. pecah teks menjadi kandidat transaksi
4. mapping hasil ke model raw transaction
5. simpan log parsing
6. uji pada beberapa sampel mutasi PDF

## Tahap 5 - OCR / Ollama untuk Image Scan

1. siapkan adapter service ke Ollama
2. siapkan preprocessing image dasar
3. buat prompt extraction yang ketat
4. validasi output JSON / teks hasil OCR
5. siapkan fallback teknis ke Tesseract bila diperlukan
6. uji pada beberapa sampel mutasi image / scan

## Tahap 6 - Normalisasi Transaksi

1. normalisasi tanggal
2. normalisasi nominal
3. deteksi `CR` / `DB`
4. normalisasi keterangan
5. gabungkan output parser PDF dan OCR ke model transaksi yang sama
6. simpan transaksi ternormalisasi
7. cegah duplikasi transaksi

## Tahap 7 - Klasifikasi dan Exclusion

1. implement rule-based category detection
2. implement anomaly threshold
3. implement exclusion rules
4. tandai transaksi excluded
5. recalculation dashboard setelah exclusion berubah

## Tahap 8 - Dashboard Aggregation API

1. summary cards
2. monthly summary
3. top 10 amount
4. top 10 frequency
5. anomaly lists
6. income, pajak, bunga
7. detail transaksi paginated
8. export Excel

## Tahap 9 - Frontend Foundation

1. setup Angular 21 project structure
2. setup layout dashboard
3. setup global theme
4. setup API client layer
5. setup shared models dan interceptors

## Tahap 10 - Frontend Dashboard

1. render summary cards
2. render chart tren
3. render donut chart
4. render tabel summary bulanan
5. render top 10 tables
6. render anomali / income / pajak / bunga
7. render detail transaksi
8. export action

## Tahap 11 - Frontend Exclusion Management

1. form tambah rule
2. list rule aktif
3. update / delete rule
4. tombol recalculate
5. feedback sukses / gagal

## Tahap 12 - Testing dan Hardening

1. unit test service utama
2. integration test API penting
3. test upload file invalid
4. test parsing sampel nyata
5. test exclusion behavior
6. test dashboard totals against raw transactions
7. performance test ringan
8. bug fixing dan UI polish

---

## 15. Rencana Khusus OCR dan Ollama

## 15.1 Posisi Ollama dalam Sistem

Ollama ditempatkan sebagai **komponen inti untuk jalur image / scan**, bukan sekadar tambahan opsional.

Strategi implementasi:

- PDF digital diproses melalui parser PDF
- image / scan diproses melalui OCR / vision pipeline
- Ollama menjadi engine utama untuk ekstraksi image pada fase 1
- hasil Ollama tetap harus melewati validasi backend sebelum masuk ke transaksi normalisasi

## 15.2 Alur Ollama

1. backend menerima image / scan
2. backend melakukan preprocessing dasar
3. backend mengirim image ke model vision
4. backend meminta output JSON terstruktur
5. backend memvalidasi field wajib
6. backend melakukan normalisasi transaksi
7. jika gagal, dokumen diberi status `FAILED` atau `NEEDS_REVIEW`

## 15.3 Best Practice Prompting

Prompt harus:

- spesifik
- meminta JSON saja
- membatasi field
- melarang model berasumsi jika data tidak ada

Contoh prinsip prompt:

- ekstrak hanya data yang terlihat
- jangan mengarang angka
- gunakan format tanggal ISO jika memungkinkan
- jika flag tidak terlihat, isi `UNKNOWN`

## 15.4 Risiko Ollama

- hasil tidak selalu konsisten
- dokumen bank kompleks bisa membingungkan model
- butuh hardware memadai
- parsing tabel panjang bisa terpotong

Kesimpulan:

- gunakan Ollama sebagai engine utama untuk image / scan, tetapi **wajib dipagari validasi backend**
- siapkan fallback teknis OCR non-LLM agar jalur image tetap tersedia saat model vision bermasalah
- seluruh sistem tidak boleh bergantung pada satu metode ekstraksi saja; PDF dan image harus punya pipeline masing-masing

---

## 16. Testing Strategy

## 16.1 Backend

Wajib dites:

- parser PDF
- normalisasi nominal
- normalisasi tanggal
- exclusion matcher
- summary calculator
- export Excel

## 16.2 Frontend

Wajib dites:

- render dashboard saat data ada
- render empty state
- filter dan pagination tabel
- action export
- CRUD exclusion rule

## 16.3 Data Testing

Siapkan minimal:

- 2-3 file mutasi PDF digital
- 1 file image / scan
- 1 file invalid
- variasi transaksi kecil dan besar
- variasi keyword pajak, bunga, admin, transfer

---

## 17. Error Handling yang Harus Ada

- file format tidak didukung
- file kosong / rusak
- parsing gagal
- OCR timeout
- hasil OCR tidak valid
- migration database gagal
- duplicate upload
- export gagal

Setiap error harus menghasilkan:

- status yang jelas
- message yang aman dibaca user
- log teknis untuk developer

---

## 18. Target Delivery 25 Hari

## Minggu 1

- setup backend Spring Boot 4.0.3, frontend, SQL Server
- desain database
- setup schema management Hibernate
- upload document
- metadata rekening dan status processing

## Minggu 2

- parsing PDF digital
- OCR / Ollama pipeline untuk image / scan
- normalisasi transaksi
- simpan transaksi

## Minggu 3

- exclusion rules
- top 10 / anomali / income / pajak / bunga
- dashboard API lengkap
- frontend dashboard utama

## Minggu 4

- export Excel
- hardening pipeline PDF dan image
- testing menyeluruh
- bug fixing
- final polish

## Kesimpulan timeline

Untuk **1 developer**, target 25 hari **masih realistis tetapi ketat** jika:

- fokus pada 1 rekening
- fokus pada 1 format bank dulu
- implementasi PDF digital dan image / scan berjalan dengan satu model normalisasi yang sama
- klasifikasi transaksi tetap rule-based dan tidak melebar scope-nya

---

## 19. Prioritas Implementasi Agar Minim Error

### Prioritas Wajib

1. database schema stabil
2. upload dan parsing PDF digital
3. upload dan parsing image / scan
4. normalisasi transaksi
5. exclusion rule
6. dashboard API
7. dashboard frontend
8. export Excel

### Prioritas Menengah

9. penyempurnaan kategori transaksi
10. tuning OCR prompt dan preprocessing image
11. empty states dan polishing UI

### Prioritas Tunda

12. multi rekening
13. internal transfer detection
14. AI insight generator
15. approval workflow

---

## 20. Keputusan Teknis yang Disarankan

### Final recommendation untuk fase 1

- **Backend:** Spring Boot 4.0.3 + Java 21
- **Database:** SQL Server
- **Schema Management:** JPA + Hibernate mengikuti baseline project saat ini
- **PDF Parser:** Apache PDFBox
- **OCR AI:** Ollama untuk image / scan
- **OCR Fallback:** Tesseract bila diperlukan untuk stabilitas jalur image
- **Frontend:** Angular 21 + PrimeNG + ApexCharts
- **Export:** Apache POI
- **Deployment awal:** local / internal server Windows

### Pendekatan terbaik

- dukung **PDF digital** dan **image / scan** sebagai requirement inti fase 1
- buat **dua pipeline ekstraksi** yang berujung ke model transaksi normalisasi yang sama
- buat **dashboard driven by backend aggregation**, bukan hitung di frontend
- simpan **raw data dan normalized data** agar mudah audit dan debug
- pastikan **exclusion dapat diubah tanpa upload ulang**
- gunakan Hibernate sesuai project existing, dengan kontrol environment yang disiplin agar schema tidak berubah liar

---

## 21. Definition of Done Fase 1

Fase 1 dianggap selesai jika:

1. user dapat upload mutasi 1 rekening
2. sistem dapat memproses minimal 3 bulan data
3. dashboard tampil sesuai kebutuhan utama
4. summary dan detail konsisten
5. exclusion rule bekerja
6. data dapat diexport ke Excel
7. error utama tertangani
8. minimal 1 format mutasi bank berjalan stabil

---

## 22. Catatan Penutup

Dokumen ini menjadi patokan development fase 1. Jika ada perubahan scope, perubahan harus diklasifikasikan menjadi:

- **mandatory for MVP**
- **nice to have**
- **post-MVP**

Agar target 25 hari tetap realistis, setiap penambahan fitur baru harus dibandingkan terhadap dampaknya ke parsing, database, API, frontend, dan testing.
