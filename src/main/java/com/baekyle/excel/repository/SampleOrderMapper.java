package com.baekyle.excel.repository;

import com.baekyle.excel.domain.OrderRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SampleOrderMapper {

    List<OrderRow> findChunk(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("totalRows") int totalRows
    );

    List<OrderRow> findAllForNormalDownload(@Param("totalRows") int totalRows);
}
