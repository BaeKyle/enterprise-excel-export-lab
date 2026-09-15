package com.baekyle.excel.export;

import java.time.LocalDateTime;
import java.util.UUID;

public record ExportJobResponse(
        UUID id,
        String jobType,
        ExportStatus status,
        int exportedRows,
        int totalRows,
        int chunkSize,
        int progressPercent,
        String fileName,
        String errorMessage,
        LocalDateTime requestedAt,
        LocalDateTime completedAt,
        LocalDateTime expiresAt,
        long elapsedMillis,
        long fileSizeBytes
) {
    public static ExportJobResponse from(ExportJob job) {
        String fileName = job.getFilePath() == null ? null : job.getFilePath().getFileName().toString();
        int progressPercent = job.getTotalRows() == 0
                ? 0
                : Math.min(100, (int) Math.round(job.getExportedRows() * 100.0 / job.getTotalRows()));
        return new ExportJobResponse(
                job.getId(),
                job.getJobType(),
                job.getStatus(),
                job.getExportedRows(),
                job.getTotalRows(),
                job.getChunkSize(),
                progressPercent,
                fileName,
                job.getErrorMessage(),
                job.getRequestedAt(),
                job.getCompletedAt(),
                job.getExpiresAt(),
                job.getElapsedMillis(),
                job.getFileSizeBytes()
        );
    }
}
