package com.baekyle.excel.export;

import com.baekyle.excel.domain.OrderRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

@Component
public class StreamingExcelWriter {

    private static final String[] HEADERS = {
            "ID", "Customer Code", "Product", "Status", "Amount", "Requested Date"
    };

    public SXSSFWorkbook createWorkbook() {
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);

        Sheet sheet = workbook.createSheet("orders");
        Row header = sheet.createRow(0);
        for (int index = 0; index < HEADERS.length; index++) {
            header.createCell(index).setCellValue(HEADERS[index]);
        }

        return workbook;
    }

    public int writeRows(SXSSFWorkbook workbook, int startRowIndex, List<OrderRow> rows) {
        Sheet sheet = workbook.getSheetAt(0);
        CellStyle dateStyle = dateStyle(workbook);

        int rowIndex = startRowIndex;
        for (OrderRow item : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(item.id());
            row.createCell(1).setCellValue(item.customerCode());
            row.createCell(2).setCellValue(item.productName());
            row.createCell(3).setCellValue(item.status());
            row.createCell(4).setCellValue(item.amount().doubleValue());

            Cell dateCell = row.createCell(5);
            dateCell.setCellValue(item.requestedDate());
            dateCell.setCellStyle(dateStyle);
        }

        return rowIndex;
    }

    public void writeTo(SXSSFWorkbook workbook, OutputStream outputStream) throws IOException {
        workbook.write(outputStream);
        workbook.dispose();
    }

    private CellStyle dateStyle(SXSSFWorkbook workbook) {
        CreationHelper helper = workbook.getCreationHelper();
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));
        return style;
    }
}

