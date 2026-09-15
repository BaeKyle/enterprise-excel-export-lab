package com.baekyle.excel.export;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class ExportJob {

    private final UUID id;
    private final String jobType;
    private final int totalRows;
    private final int chunkSize;
    private final LocalDateTime requestedAt;
    private final AtomicInteger exportedRows;
    private volatile ExportStatus status;
    private volatile Path filePath;
    private volatile String errorMessage;
    private volatile LocalDateTime completedAt;
    private volatile LocalDateTime expiresAt;
    private volatile boolean cancelRequested;

    public ExportJob(UUID id, String jobType, int totalRows, int chunkSize) {
        this.id = id;
        this.jobType = jobType;
        this.totalRows = totalRows;
        this.chunkSize = chunkSize;
        this.requestedAt = LocalDateTime.now();
        this.exportedRows = new AtomicInteger();
        this.status = ExportStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public String getJobType() {
        return jobType;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getExportedRows() {
        return exportedRows.get();
    }

    public void addExportedRows(int count) {
        exportedRows.addAndGet(count);
    }

    public ExportStatus getStatus() {
        return status;
    }

    public void setStatus(ExportStatus status) {
        this.status = status;
    }

    public Path getFilePath() {
        return filePath;
    }

    public void setFilePath(Path filePath) {
        this.filePath = filePath;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public long getElapsedMillis() {
        LocalDateTime end = completedAt == null ? LocalDateTime.now() : completedAt;
        return java.time.Duration.between(requestedAt, end).toMillis();
    }

    public long getFileSizeBytes() {
        if (filePath == null) {
            return 0L;
        }
        try {
            return java.nio.file.Files.size(filePath);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public void requestCancel() {
        this.cancelRequested = true;
    }

    public void markCompleted(Path filePath) {
        this.filePath = filePath;
        this.status = ExportStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.expiresAt = completedAt.plusHours(1);
    }

    public void markFailed(Exception exception) {
        this.status = ExportStatus.FAILED;
        this.errorMessage = exception.getMessage();
        this.completedAt = LocalDateTime.now();
    }

    public void markCanceled() {
        this.status = ExportStatus.CANCELED;
        this.completedAt = LocalDateTime.now();
        this.errorMessage = "Export job was canceled by user request.";
    }
}
