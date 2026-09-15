package com.baekyle.excel.export;

import com.baekyle.excel.domain.OrderRow;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingExcelWriterTest {

    private final StreamingExcelWriter writer = new StreamingExcelWriter();

    @Test
    void writesRowsAfterHeader() {
        SXSSFWorkbook workbook = writer.createWorkbook();
        List<OrderRow> rows = List.of(
                new OrderRow(1L, "CUST-00001", "Sample Product", "COMPLETED",
                        BigDecimal.valueOf(1000), LocalDate.of(2026, 1, 1))
        );

        int nextRowIndex = writer.writeRows(workbook, 1, rows);

        Sheet sheet = workbook.getSheetAt(0);
        assertThat(nextRowIndex).isEqualTo(2);
        assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("ID");
        assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("CUST-00001");

        workbook.dispose();
    }
}

