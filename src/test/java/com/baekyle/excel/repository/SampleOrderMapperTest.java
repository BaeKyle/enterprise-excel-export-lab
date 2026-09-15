package com.baekyle.excel.repository;

import com.baekyle.excel.domain.OrderRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SampleOrderMapperTest {

    @Autowired
    private SampleOrderMapper sampleOrderMapper;

    @Test
    void findChunkReadsRowsWithOffsetPagination() {
        List<OrderRow> rows = sampleOrderMapper.findChunk(1000, 10, 600000);

        assertThat(rows).hasSize(10);
        assertThat(rows.get(0).id()).isEqualTo(1001L);
        assertThat(rows.get(9).id()).isEqualTo(1010L);
    }
}
