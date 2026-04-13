package com.example.mutexa_be.service;

import com.example.mutexa_be.dto.response.DetailTransaksiResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ExcelExportService {

   public ByteArrayInputStream exportDetailTransaksiToExcel(List<DetailTransaksiResponse> data, boolean showSaldo) throws IOException {
      String[] columns = showSaldo 
          ? new String[]{ "Tanggal", "Keterangan", "Flag", "Debit", "Kredit", "Saldo" }
          : new String[]{ "Tanggal", "Keterangan", "Flag", "Debit", "Kredit" };

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

            Cell flagCell = row.createCell(2);
            flagCell.setCellValue(tx.getFlag() != null ? tx.getFlag() : "-");
            flagCell.setCellStyle(dataCellStyle);

            Cell debitCell = row.createCell(3);
            Cell creditCell = row.createCell(4);
            debitCell.setCellStyle(numberCellStyle);
            creditCell.setCellStyle(numberCellStyle);

            if (tx.getJumlah() != null) {
               if ("DB".equalsIgnoreCase(tx.getFlag())) {
                  debitCell.setCellValue(tx.getJumlah().doubleValue());
                  creditCell.setCellValue("");
               } else if ("CR".equalsIgnoreCase(tx.getFlag())) {
                  debitCell.setCellValue("");
                  creditCell.setCellValue(tx.getJumlah().doubleValue());
               } else {
                  debitCell.setCellValue("");
                  creditCell.setCellValue("");
               }
            } else {
               debitCell.setCellValue("");
               creditCell.setCellValue("");
            }

            if (showSaldo) {
               Cell saldoCell = row.createCell(5);
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
}
