# Penjelasan Logika *Average Daily Balance* (Rata-Rata Saldo Harian) pada Mutexa

Dokumen ini disusun untuk menjelaskan landasan bisnis dan alur algoritma bagaimana modul **Average Daily Balance (ADB)** / Rata-rata Saldo Harian dihitung pada aplikasi Mutexa. Pendekatan ini diformulasikan agar selaras dengan ketelitian standar yang dibutuhkan oleh seorang Credit Analyst.

---

## 1. Landasan Permasalahan: Mengapa Hitung dari Tanggal 1?
Pertanyaan kritis sering muncul: *"Jika transaksi pertama di mutasi terjadi pada tanggal 4, mengapa kita tidak memulai rata-rata dari tanggal 4 saja?"*

**Jawaban Bisnis:**
Bila kita mengabaikan periode tanggal 1 sampai 3 (di mana tidak ada transaksi), kita secara artifisial "mengurangi" umur saldo, yang akhirnya menyebabkan angka Rata-rata Saldo Harian termanipulasi / membengkak secara tidak akurat. 

Oleh karena itu, ADB **harus dihitung menggunakan basis Full 1 Bulan Kalender (Tanggal 1 s/d Akhir)**.

**Bagaimana cara mengetahui Saldo pada tanggal 1 s/d 3 jika datanya tidak ada?**
Kita akan melakukan *Reverse Calculation* untuk mencari **Saldo Awal (Opening Balance)**.
* Misal: Transaksi pertama tgl 4 adalah masuk Rp 1.000.000, mengakibatkan saldo sisa Rp 5.000.000.
* Maka, sistem menggunakan logika matematika balik: Sebelum tgl 4 terisi Rp 1.000.000, pasti saldo awalnya adalah **Rp 4.000.000**.
* Sistem kita menempati Rp 4.000.000 ini secara paksa mundur ke Tanggal 1, 2, dan 3.

---

## 2. Gap-Filling: Bagaimana Jika Transaksi Bolong?
Pada kenyataannya, nasabah tidak melakukan transaksi mutasi setiap hari.

Sistem mengatasi hal ini (misal tanggal 6 s/d 10 tidak ada transaksi) dengan cara **Gap Filling (Mewariskan Saldo)**.
Jika kalender sedang memproses iterasi di tanggal 7 namun tidak ada transaksi, sistem akan **menyalin saldo yang bersarang di tanggal 6** sebagai saldo tetap pada tanggal 7, dan menjumlahkannya.

Hal ini secara otomatis memastikan bahwa uang nasabah yang "mengendap tanpa ditarik" tetap dihitung secara adil perharinya untuk menaikkan nilai Rata-rata.

---

## 3. Penanganan Dinamis Kalender (Tahun Kabisat & Hari 31 vs 30)
Bagaimana komputer menghindari kesalahan perhitungan karena sadar Februari bisa 28 atau 29 hari, dan perhitungan loncat antar bulan?

Aplikasi kita beroperasi dengan standardisasi **Java Time API (`java.time.LocalDate`)** yang sudah *built-in* dengan spesifikasi kalender Gregorian Dunia:
- Sistem tidak pernah salah mendeteksi akhiran bulan, karena kita mengeksekusi function `YearMonth.from(tanggal).atEndOfMonth()`. Fungsional ini akan mutlak menyajikan ujung dari bulan berjalan tanpa peduli itu tahun biasa ataupun kabisat.
- Ketika iterasi menjumlah (`plusDays(1)`), `30 April` akan mendesak jarum jam langsung pindah menuju `1 Mei`, dan `28 Februari` tahun 2024 akan mendarat di `29 Februari`. 

Otomasi kalender ini membebaskan sistem dari kelemahan hitung hari statis.

---

## 4. Penentuan Otomatis Titik "Start" dan "Stop"
Sistem Mutexa tidak meraba-raba periode dengan membaca seribu *row* transaksi satu persatu, melainkan menggunakan kekuatan Database:
1. Menarik Tanggal Transaksi Paling Tua dan Paling Baru via SQL. (Cth: Terdeteksi ada di `15 Jan 2026 - 15 Maret 2026`).
2. Tanggal Paling Tua (15 Jan) dipukul mundur menuju standar awal bulan: **Titik Mulai = 1 Januari 2026**.
3. Tanggal Paling Baru (15 Mar) didorong paksa menuju standar akhir bulan: **Titik Berhenti = 31 Maret 2026**.

**Final Result**: Sistem akan men-iterasi kalender persis dari `1 Januari s/d 31 Maret` (90 hari). Merangkum semua transaksi dan *gap-filling*, sebelum akhirnya membagi rapi total harian tersebut ke dalam 90 angka pembagi.
