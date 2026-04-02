# Exclusion Feature Roadmap (Belum Diimplementasi)

> ⚠️ Dokumen ini adalah hasil brainstorming awal. Belum didiskusikan dengan user final.

## Fase Penuh (Nanti)

### Tabel `exclusion_rules`
| Field | Deskripsi |
|---|---|
| `rule_type` | `NAME`, `ACCOUNT_NUMBER`, `KEYWORD` |
| `rule_value` | Nilai yang di-exclude |
| `is_active` | Toggle aktif/nonaktif |
| `notes` | Catatan alasan exclusion |

### Cara Kerja
1. Setelah normalisasi, cek semua rule aktif
2. Jika match → `is_excluded = true`, isi `exclusion_reason`
3. Transaksi TETAP disimpan (audit trail), tapi TIDAK ikut dashboard
4. Exclusion bisa di-undo → dashboard kembali seperti semula

### Dampak ke Dashboard
- Top 10 Credit/Debit Amount → recalculate
- Top 10 Credit/Debit Freq → recalculate
- Summary Perbulan → total & freq berubah
- Income/Pajak → berubah
- Detail Transaksi → tetap terlihat tapi ditandai excluded

### API
- `GET/POST/PUT/DELETE /api/exclusions`
- `POST /api/exclusions/recalculate`

### Ide UX
- Quick Exclude langsung dari tabel (klik tombol ❌)
- Exclude Preview (before & after impact)
- Exclusion Drawer di dashboard (bukan halaman terpisah)
- Auto-recalculate setelah rule berubah
