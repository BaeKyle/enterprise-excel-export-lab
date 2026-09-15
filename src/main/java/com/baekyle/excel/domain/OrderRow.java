package com.baekyle.excel.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OrderRow(
        long id,
        String customerCode,
        String productName,
        String status,
        BigDecimal amount,
        LocalDate requestedDate
) {
}

