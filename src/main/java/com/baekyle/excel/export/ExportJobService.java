package com.baekyle.excel.export;

import com.baekyle.excel.domain.OrderRow;
import com.baekyle.excel.repository.SampleOrderMapper;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExportJobService {

    private final SampleOrderMapper orderMapper;
    private final StreamingExcelWriter excelWriter;
    private final TaskExecutor exportTaskExecutor;
    private final Map<UUID, ExportJob> jobs = new ConcurrentHashMap<>();
    private final Path exportDirectory;
    private final int chunkSize;
    private final int sampleTotalRows;

    public ExportJobService(
            SampleOrderMapper orderMapper,
            StreamingExcelWriter excelWriter,
            TaskExecutor exportTaskExecutor,
            @Value("${export.directory:build/export-files}") Path exportDirectory,
            @Value("${export.chunk-size:1000}") int chunkSize,
            @Value("${export.sample-total-rows:600000}") int sampleTotalRows
    ) {
        this.orderMapper = orderMapper;
        this.excelWriter = excelWriter;
        this.exportTaskExecutor = exportTaskExecutor;
        this.exportDirectory = exportDirectory;
        this.chunkSize = chunkSize;
        this.sampleTotalRows = sampleTotalRows;
    }

    public ExportJob createOrderExportJob(Integer requestedChunkSize) {
        int effectiveChunkSize = normalizeChunkSize(requestedChunkSize);
        Optional<ExportJob> runningJob = jobs.values().stream()
                .filter(job -> "BULK_ORDER_EXCEL".equals(job.getJobType()))
                .filter(job -> job.getStatus() == ExportStatus.PENDING || job.getStatus() == ExportStatus.RUNNING)
                .findFirst();
        if (runningJob.isPresent()) {
            return runningJob.get();
        }

        ExportJob job = new ExportJob(UUID.randomUUID(), "BULK_ORDER_EXCEL", sampleTotalRows, effectiveChunkSize);
        jobs.put(job.getId(), job);
        exportTaskExecutor.execute(() -> runOrderExport(job.getId()));
        return job;
    }

    public Resource createNormalOrderExport() {
        throw new IllegalStateException(
                "Normal download failed: insufficient memory risk. "
                        + "It would execute one unpaged query for " + sampleTotalRows
                        + " rows and keep the query result, workbook, and response buffer in memory before the browser download starts."
        );
    }

    public int getSampleTotalRows() {
        return sampleTotalRows;
    }

    public int getDefaultChunkSize() {
        return chunkSize;
    }

    public Optional<ExportJob> findJob(UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public Resource getCompletedFile(UUID jobId) {
        ExportJob job = jobs.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Export job not found: " + jobId);
        }
        if (job.getStatus() != ExportStatus.COMPLETED || job.getFilePath() == null) {
            throw new IllegalStateException("Export job is not completed: " + jobId);
        }
        return new PathResource(job.getFilePath());
    }

    public ExportJob cancelJob(UUID jobId) {
        ExportJob job = jobs.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Export job not found: " + jobId);
        }
        if (job.getStatus() == ExportStatus.PENDING || job.getStatus() == ExportStatus.RUNNING) {
            job.requestCancel();
        }
        return job;
    }

    public int cleanupCompletedFiles() {
        int removed = 0;
        for (ExportJob job : jobs.values()) {
            if (job.getStatus() != ExportStatus.COMPLETED || job.getFilePath() == null) {
                continue;
            }
            try {
                if (Files.deleteIfExists(job.getFilePath())) {
                    removed++;
                }
                job.setFilePath(null);
            } catch (Exception ignored) {
                // In production, this should be logged and retried by a scheduled cleanup job.
            }
        }
        return removed;
    }

    void runOrderExport(UUID jobId) {
        ExportJob job = jobs.get(jobId);
        if (job == null) {
            return;
        }

        job.setStatus(ExportStatus.RUNNING);

        try {
            Files.createDirectories(exportDirectory);
            Path filePath = exportDirectory.resolve("orders-" + jobId + ".xlsx");
            SXSSFWorkbook workbook = excelWriter.createWorkbook();

            int offset = 0;
            int rowIndex = 1;
            while (true) {
                List<OrderRow> chunk = orderMapper.findChunk(offset, job.getChunkSize(), job.getTotalRows());
                if (chunk.isEmpty()) {
                    break;
                }
                if (job.isCancelRequested()) {
                    cleanupPartialFile(filePath);
                    job.markCanceled();
                    return;
                }

                rowIndex = excelWriter.writeRows(workbook, rowIndex, chunk);
                job.addExportedRows(chunk.size());
                offset += chunk.size();
            }

            try (OutputStream outputStream = Files.newOutputStream(filePath)) {
                excelWriter.writeTo(workbook, outputStream);
            }

            job.markCompleted(filePath);
        } catch (Exception exception) {
            job.markFailed(exception);
        }
    }

    private void cleanupPartialFile(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
        } catch (Exception ignored) {
            // In production, partial file cleanup should be retried by a scheduled job.
        }
    }

    private int normalizeChunkSize(Integer requestedChunkSize) {
        if (requestedChunkSize == null || requestedChunkSize <= 0) {
            return chunkSize;
        }
        return Math.min(requestedChunkSize, 10000);
    }
}
