package com.example.mutexa_be.service;

import com.example.mutexa_be.dto.response.DetailTransaksiResponse;
import com.example.mutexa_be.dto.response.PengendapanBulanResponse;
import com.example.mutexa_be.dto.response.PengendapanResponse;
import com.example.mutexa_be.dto.response.PengendapanRowResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ExcelExportService {

   public ByteArrayInputStream exportDetailTransaksiToExcel(List<DetailTransaksiResponse> data, boolean showSaldo,
         String filterFlag) throws IOException {
      String[] columns;
      boolean isKreditOnly = "CR".equalsIgnoreCase(filterFlag);
      boolean isDebitOnly = "DB".equalsIgnoreCase(filterFlag);

      if (isKreditOnly) {
         columns = new String[] { "Tanggal", "Keterangan", "Kredit" }; // hide flag, debit, saldo
      } else if (isDebitOnly) {
         columns = new String[] { "Tanggal", "Keterangan", "Debit" }; // hide flag, kredit, saldo
      } else {
         columns = showSaldo
               ? new String[] { "Tanggal", "Keterangan", "Flag", "Debit", "Kredit", "Saldo" }
               : new String[] { "Tanggal", "Keterangan", "Flag", "Debit", "Kredit" };
      }

      try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
         Sheet sheet = workbook.createSheet("Detail Transaksi");

         // Define Font for Header
         Font headerFont = workbook.createFont();
         headerFont.setBold(true);
         headerFont.setColor(IndexedColors.BLACK.getIndex());

         // Define CellStyle for Header
         CellStyle headerCellStyle = workbook.createCellStyle();
         headerCellStyle.setFont(headerFont);
         headerCellStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
         headerCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
         headerCellStyle.setBorderBottom(BorderStyle.THIN);
         headerCellStyle.setBorderTop(BorderStyle.THIN);
         headerCellStyle.setBorderLeft(BorderStyle.THIN);
         headerCellStyle.setBorderRight(BorderStyle.THIN);
         headerCellStyle.setAlignment(HorizontalAlignment.CENTER);

         // Row for Header
         Row headerRow = sheet.createRow(0);

         // Header
         for (int col = 0; col < columns.length; col++) {
            Cell cell = headerRow.createCell(col);
            cell.setCellValue(columns[col]);
            cell.setCellStyle(headerCellStyle);
         }

         // CellStyle for Data
         CellStyle dataCellStyle = workbook.createCellStyle();
         dataCellStyle.setBorderBottom(BorderStyle.THIN);
         dataCellStyle.setBorderTop(BorderStyle.THIN);
         dataCellStyle.setBorderLeft(BorderStyle.THIN);
         dataCellStyle.setBorderRight(BorderStyle.THIN);

         // Date formatting
         CreationHelper createHelper = workbook.getCreationHelper();
         CellStyle dateCellStyle = workbook.createCellStyle();
         dateCellStyle.cloneStyleFrom(dataCellStyle);
         dateCellStyle.setDataFormat(createHelper.createDataFormat().getFormat("dd/mm/yyyy"));

         // Number formatting
         CellStyle numberCellStyle = workbook.createCellStyle();
         numberCellStyle.cloneStyleFrom(dataCellStyle);
         numberCellStyle.setDataFormat(createHelper.createDataFormat().getFormat("#,##0.00"));

         int rowIdx = 1;
         for (int i = 0; i < data.size(); i++) {
            DetailTransaksiResponse tx = data.get(i);
            Row row = sheet.createRow(rowIdx++);

            Cell dateCell = row.createCell(0);
            if (tx.getTanggal() != null && !tx.getTanggal().isEmpty()) {
               try {
                  java.time.LocalDate localDate = java.time.LocalDate.parse(tx.getTanggal());
                  dateCell.setCellValue(
                        java.util.Date.from(localDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
               } catch (Exception e) {
                  dateCell.setCellValue(tx.getTanggal());
               }
            } else {
               dateCell.setCellValue("-");
            }
            dateCell.setCellStyle(dateCellStyle);

            Cell descCell = row.createCell(1);
            descCell.setCellValue(tx.getKeterangan() != null ? tx.getKeterangan() : "-");
            descCell.setCellStyle(dataCellStyle);

            // Dynamic cell placement based on active columns
            int cellIdx = 2; // Date is 0, Desc is 1

            if (!isKreditOnly && !isDebitOnly) {
               Cell flagCell = row.createCell(cellIdx++);
               flagCell.setCellValue(tx.getFlag() != null ? tx.getFlag() : "-");
               flagCell.setCellStyle(dataCellStyle);
            }

            if (!isKreditOnly) {
               Cell debitCell = row.createCell(cellIdx++);
               debitCell.setCellStyle(numberCellStyle);
               if (tx.getJumlah() != null && "DB".equalsIgnoreCase(tx.getFlag())) {
                  debitCell.setCellValue(tx.getJumlah().doubleValue());
               } else {
                  debitCell.setCellValue("");
               }
            }

            if (!isDebitOnly) {
               Cell creditCell = row.createCell(cellIdx++);
               creditCell.setCellStyle(numberCellStyle);
               if (tx.getJumlah() != null && "CR".equalsIgnoreCase(tx.getFlag())) {
                  creditCell.setCellValue(tx.getJumlah().doubleValue());
               } else {
                  creditCell.setCellValue("");
               }
            }

            if (showSaldo && !isKreditOnly && !isDebitOnly) {
               Cell saldoCell = row.createCell(cellIdx);
               saldoCell.setCellStyle(numberCellStyle);

               boolean isLastOfDate = true;
               if (i < data.size() - 1) {
                  DetailTransaksiResponse nextTx = data.get(i + 1);
                  if (tx.getTanggal() != null && nextTx.getTanggal() != null
                        && tx.getTanggal().equals(nextTx.getTanggal())) {
                     isLastOfDate = false;
                  }
               }

               if (isLastOfDate && tx.getSaldo() != null) {
                  saldoCell.setCellValue(tx.getSaldo().doubleValue());
               } else {
                  saldoCell.setCellValue("");
               }
            }
         }

         // Resize all columns to fit the content size
         for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
         }

         workbook.write(out);
         return new ByteArrayInputStream(out.toByteArray());
      }
   }

   public ByteArrayInputStream exportPengendapanPemakaianToExcel(PengendapanResponse data, String accountName) throws IOException {
      try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
         Sheet sheet = workbook.createSheet("Pengendapan & Pemakaian");

         // --- STYLES ---
         CellStyle headerStyle = createHeaderStyle(workbook);
         CellStyle dataStyle = createBorderStyle(workbook);
         CellStyle numberStyle = createNumberStyle(workbook, dataStyle);
         CellStyle boldStyle = createBoldStyle(workbook, dataStyle);
         CellStyle boldNumberStyle = createBoldStyle(workbook, numberStyle);

         // --- TITLE ---
         Row titleRow = sheet.createRow(0);
         Cell titleCell = titleRow.createCell(1);
         titleCell.setCellValue("PENGENDAPAN DAN PEMAKAIAN");
         CellStyle titleStyle = workbook.createCellStyle();
         Font titleFont = workbook.createFont();
         titleFont.setBold(true);
         titleFont.setFontHeightInPoints((short) 14);
         titleStyle.setFont(titleFont);
         titleCell.setCellStyle(titleStyle);

         if (accountName != null) {
            Row accRow = sheet.createRow(1);
            Cell accCell = accRow.createCell(1);
            accCell.setCellValue("Nama Pemilik: " + accountName);
         }

         // --- DATA GRID ---
         int monthsPerRow = 3;
         int startRow = 3;
         int colOffsetPerMonth = 6; 

         List<PengendapanBulanResponse> bulanList = data.getBulanList();
         
         List<String> avgPengendapanRefs = new java.util.ArrayList<>();
         List<String> avgPemakaianRefs = new java.util.ArrayList<>();

         for (int m = 0; m < bulanList.size(); m++) {
            PengendapanBulanResponse bulan = bulanList.get(m);
            int blockColStart = 1 + (m % monthsPerRow) * colOffsetPerMonth;
            int blockRowStart = startRow + (m / monthsPerRow) * 40; 

            // Header row for month
            Row monthHeadRow = getOrCreateRow(sheet, blockRowStart);
            createStyledCell(monthHeadRow, blockColStart, bulan.getPeriode(), headerStyle);
            createStyledCell(monthHeadRow, blockColStart + 1, "Saldo", headerStyle);
            createStyledCell(monthHeadRow, blockColStart + 2, "Hari", headerStyle);
            createStyledCell(monthHeadRow, blockColStart + 3, "Pengendapan", headerStyle);
            createStyledCell(monthHeadRow, blockColStart + 4, "Pemakaian", headerStyle);

            int currentRow = blockRowStart + 1;
            List<PengendapanRowResponse> rows = bulan.getRows();
            for (int r = 0; r < rows.size(); r++) {
               PengendapanRowResponse dr = rows.get(r);
               Row row = getOrCreateRow(sheet, currentRow + r);
               
               createStyledCell(row, blockColStart, dr.getTanggal(), dataStyle);
               createStyledCell(row, blockColStart + 1, dr.getSaldo().doubleValue(), numberStyle);
               createStyledCell(row, blockColStart + 2, dr.getHari(), dataStyle);

               String saldoRef = new CellReference(currentRow + r, blockColStart + 1).formatAsString();
               String hariRef = new CellReference(currentRow + r, blockColStart + 2).formatAsString();
               String formula = saldoRef + "*" + hariRef;

               if (dr.getSaldo().doubleValue() >= 0) {
                  Cell cell = row.createCell(blockColStart + 3);
                  cell.setCellFormula(formula);
                  cell.setCellStyle(numberStyle);
                  createStyledCell(row, blockColStart + 4, "", dataStyle);
               } else {
                  createStyledCell(row, blockColStart + 3, "", dataStyle);
                  Cell cell = row.createCell(blockColStart + 4);
                  cell.setCellFormula(formula);
                  cell.setCellStyle(numberStyle);
               }
            }

            // Total Row
            int totalRowIdx = currentRow + rows.size();
            Row totalRow = getOrCreateRow(sheet, totalRowIdx);
            createStyledCell(totalRow, blockColStart, "Total", boldStyle);
            createStyledCell(totalRow, blockColStart + 1, "", dataStyle);
            
            String hariRange = getRange(blockRowStart + 1, blockColStart + 2, totalRowIdx - 1, blockColStart + 2);
            String pengRange = getRange(blockRowStart + 1, blockColStart + 3, totalRowIdx - 1, blockColStart + 3);
            String pemRange = getRange(blockRowStart + 1, blockColStart + 4, totalRowIdx - 1, blockColStart + 4);

            Cell sumHariCell = totalRow.createCell(blockColStart + 2);
            sumHariCell.setCellFormula("SUM(" + hariRange + ")");
            sumHariCell.setCellStyle(boldStyle);

            Cell sumPengCell = totalRow.createCell(blockColStart + 3);
            sumPengCell.setCellFormula("SUM(" + pengRange + ")");
            sumPengCell.setCellStyle(boldNumberStyle);

            Cell sumPemCell = totalRow.createCell(blockColStart + 4);
            sumPemCell.setCellFormula("SUM(" + pemRange + ")");
            sumPemCell.setCellStyle(boldNumberStyle);

            // Avg Rows
            String sumHariRef = new CellReference(totalRowIdx, blockColStart + 2).formatAsString();
            String sumPengRef = new CellReference(totalRowIdx, blockColStart + 3).formatAsString();
            String sumPemRef = new CellReference(totalRowIdx, blockColStart + 4).formatAsString();

            Row avgPengRow = getOrCreateRow(sheet, totalRowIdx + 1);
            createStyledCell(avgPengRow, blockColStart, "Pengendapan/Bulan :", dataStyle);
            Cell avgPengCell = avgPengRow.createCell(blockColStart + 1);
            avgPengCell.setCellFormula(sumPengRef + "/" + sumHariRef);
            avgPengCell.setCellStyle(numberStyle);
            avgPengendapanRefs.add(new CellReference(totalRowIdx + 1, blockColStart + 1).formatAsString());

            Row avgPemRow = getOrCreateRow(sheet, totalRowIdx + 2);
            createStyledCell(avgPemRow, blockColStart, "Pemakaian/Bulan :", dataStyle);
            Cell avgPemCell = avgPemRow.createCell(blockColStart + 1);
            avgPemCell.setCellFormula(sumPemRef + "/" + sumHariRef);
            avgPemCell.setCellStyle(numberStyle);
            avgPemakaianRefs.add(new CellReference(totalRowIdx + 2, blockColStart + 1).formatAsString());
         }

         // Overall Avg
         if (!avgPengendapanRefs.isEmpty()) {
            int summaryCol = 1 + monthsPerRow * colOffsetPerMonth;
            Row r1 = getOrCreateRow(sheet, 3);
            createStyledCell(r1, summaryCol, "Rata-Rata Pengendapan Keseluruhan :", boldStyle);
            Cell c1 = r1.createCell(summaryCol + 1);
            c1.setCellFormula("AVERAGE(" + String.join(",", avgPengendapanRefs) + ")");
            c1.setCellStyle(boldNumberStyle);

            Row r2 = getOrCreateRow(sheet, 4);
            createStyledCell(r2, summaryCol, "Rata-Rata Pemakaian Keseluruhan :", boldStyle);
            Cell c2 = r2.createCell(summaryCol + 1);
            c2.setCellFormula("AVERAGE(" + String.join(",", avgPemakaianRefs) + ")");
            c2.setCellStyle(boldNumberStyle);
            
            sheet.autoSizeColumn(summaryCol);
            sheet.autoSizeColumn(summaryCol + 1);
         }

         for (int i = 0; i < (monthsPerRow * colOffsetPerMonth) + 1; i++) {
            sheet.autoSizeColumn(i);
         }

         workbook.write(out);
         return new ByteArrayInputStream(out.toByteArray());
      }
   }

   private Row getOrCreateRow(Sheet sheet, int rowIdx) {
      Row row = sheet.getRow(rowIdx);
      return (row == null) ? sheet.createRow(rowIdx) : row;
   }

   private void createStyledCell(Row row, int colIdx, Object value, CellStyle style) {
      Cell cell = row.createCell(colIdx);
      if (value instanceof String) cell.setCellValue((String) value);
      else if (value instanceof Number) cell.setCellValue(((Number) value).doubleValue());
      else if (value == null) cell.setCellValue("");
      cell.setCellStyle(style);
   }

   private String getRange(int r1, int c1, int r2, int c2) {
      return new CellReference(r1, c1).formatAsString() + ":" + new CellReference(r2, c2).formatAsString();
   }

   private CellStyle createHeaderStyle(Workbook wb) {
      CellStyle style = wb.createCellStyle();
      Font font = wb.createFont();
      font.setBold(true);
      style.setFont(font);
      style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
      style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      applyBorders(style);
      style.setAlignment(HorizontalAlignment.CENTER);
      return style;
   }

   private CellStyle createBorderStyle(Workbook wb) {
      CellStyle style = wb.createCellStyle();
      applyBorders(style);
      return style;
   }

   private void applyBorders(CellStyle style) {
      style.setBorderBottom(BorderStyle.THIN);
      style.setBorderTop(BorderStyle.THIN);
      style.setBorderLeft(BorderStyle.THIN);
      style.setBorderRight(BorderStyle.THIN);
   }

   private CellStyle createNumberStyle(Workbook wb, CellStyle base) {
      CellStyle style = wb.createCellStyle();
      style.cloneStyleFrom(base);
      style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("#,##0.00"));
      return style;
   }

   private CellStyle createBoldStyle(Workbook wb, CellStyle base) {
      CellStyle style = wb.createCellStyle();
      style.cloneStyleFrom(base);
      Font font = wb.createFont();
      font.setBold(true);
      style.setFont(font);
      return style;
   }
}
