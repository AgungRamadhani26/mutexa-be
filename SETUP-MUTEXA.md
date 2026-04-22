# 🚀 MUTEXA — Panduan Setup Laptop Baru

Panduan ini digunakan untuk meng-clone dan menjalankan **MUTEXA System** (Backend & Frontend) di laptop baru dari GitHub, disesuaikan dengan standar keamanan kantor.

---

## 📋 Ringkasan Komponen

| Komponen | Teknologi | Port | URL Repo GitHub |
|----------|-----------|------|-----------------|
| **mutexa-be** | Java 21 + Spring Boot 4 + Maven | `9091` | [mutexa-be.git](https://github.com/AgungRamadhani26/mutexa-be.git) |
| **mutexa-fe** | Angular 21 + Node.js 20 | `4200` | [mutexa-fe.git](https://github.com/AgungRamadhani26/mutexa-fe.git) |

---

## ⬇️ BAGIAN 1 — Persiapan Software

### 1.1 Java JDK 21
> Mutexa butuh **JDK 21**.
1. Download: [Adoptium Temurin JDK 21](https://adoptium.net)
2. Install → centang **"Add to PATH"**.
3. Verifikasi: `java -version`

### 1.2 Apache Maven
1. Download: [Maven Download](https://maven.apache.org/download.cgi)
2. Extract ke `C:\apache-maven-x.x.x`
3. Tambahkan `%MAVEN_HOME%\bin` ke Environment PATH.
4. Verifikasi: `mvn -v`

### 1.3 Node.js 20 LTS & Angular CLI
1. Download: [Node.js 20 LTS](https://nodejs.org)
2. Install & Verifikasi: `node -v` dan `npm -v`
3. Install Angular CLI:
   ```bash
   npm install -g @angular/cli@21
   ```

### 1.4 Microsoft SQL Server
1. Install **SQL Server Express** atau Developer Edition.
2. Pastikan port default **1433** aktif.
3. Gunakan **SQL Server Management Studio (SSMS)** untuk membuat database baru bernama `mutexa`.

---

## 📁 BAGIAN 2 — Clone Project

Buka terminal di folder kerja Anda (misal: `C:\Projects\Mutexa\`) dan jalankan:

```bash
# Clone Backend
git clone https://github.com/AgungRamadhani26/mutexa-be.git

# Clone Frontend
git clone https://github.com/AgungRamadhani26/mutexa-fe.git
```

---

## ⚙️ BAGIAN 3 — Setup & Konfigurasi

### 3.1 Setup Backend (`mutexa-be`)
1. Masuk ke folder: `cd mutexa-be`
2. Download dependencies: `mvn dependency:resolve`
3. Cek file `src/main/resources/application.properties`. Pastikan username & password SQL Server sudah sesuai:
   ```properties
   spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=mutexa;...
   spring.datasource.username=sa
   spring.datasource.password=KATA_SANDI_ANDA
   ```
4. Jalankan: `mvn spring-boot:run`

### 3.2 Setup Frontend (`mutexa-fe`)
1. Masuk ke folder: `cd mutexa-fe`
2. Install dependencies: `npm install`
3. Jalankan: `npm start` atau `ng serve`

---

## 🔧 BAGIAN 4 — Tips Laptop Kantor (Hardening)

### 4.1 Melewati Kebijakan PowerShell
Jika Anda tidak bisa menjalankan script `.ps1`, jalankan perintah ini di PowerShell (Run as Administrator):
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### 4.2 Masalah Koneksi/Proxy
Jika koneksi internet dibatasi oleh proxy kantor, Antigravity dapat membantu melakukan konfigurasi otomatis dengan perintah berikut (sesuaikan URL proxy):
```bash
# Contoh untuk NPM
npm config set proxy http://username:password@proxy.kantor.com:8080
# Contoh untuk Maven (Edit settings.xml)
```

---

## ▶️ Urutan Start yang Benar
1️⃣ **SQL Server** (Pastikan service Running)
2️⃣ **mutexa-be** (Tunggu sampai muncul `Started MutexaBeApplication`)
3️⃣ **mutexa-fe** (Terakhir)

Akses aplikasi di: [http://localhost:4200](http://localhost:4200)

---
*MUTEXA — Mutation & Expense Transaction Analyzer*
*Update: April 2026*
