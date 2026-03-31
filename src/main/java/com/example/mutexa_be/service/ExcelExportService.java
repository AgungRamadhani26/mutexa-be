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

   public ByteArrayInputStream exportDetailTransaksiToExcel(List<DetailTransaksiResponse> data) throws IOException {
      String[] columns = { "Tanggal", "Keterangan", "Flag", "Jumlah" };

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
         for (DetailTransaksiResponse tx : data) {
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

            Cell amountCell = row.createCell(3);
            if (tx.getJumlah() != null) {
               amountCell.setCellValue(tx.getJumlah().doubleValue());
            } else {
               amountCell.setCellValue(0.0);
            }
            amountCell.setCellStyle(numberCellStyle);
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
