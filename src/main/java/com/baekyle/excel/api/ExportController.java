package com.baekyle.excel.api;

import com.baekyle.excel.export.ExportJob;
import com.baekyle.excel.export.ExportJobResponse;
import com.baekyle.excel.export.ExportJobService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/exports")
public class ExportController {

    private final ExportJobService exportJobService;

    public ExportController(ExportJobService exportJobService) {
        this.exportJobService = exportJobService;
    }

    @PostMapping("/orders")
    public ExportJobResponse exportOrders(@RequestParam(required = false) Integer chunkSize) {
        ExportJob job = exportJobService.createOrderExportJob(chunkSize);
        return ExportJobResponse.from(job);
    }

    @GetMapping("/orders/normal")
    public ResponseEntity<Resource> normalExport() {
        Resource resource = exportJobService.createNormalOrderExport();
        return excelDownload(resource, "normal-large-orders.xlsx");
    }

    @GetMapping("/orders/normal/check")
    public ResponseEntity<Map<String, Object>> normalExportCheck() {
        return ResponseEntity.status(507).body(Map.of(
                "success", false,
                "reason", "INSUFFICIENT_MEMORY_FOR_NORMAL_DOWNLOAD",
                "rows", exportJobService.getSampleTotalRows(),
                "queryMode", "UNPAGED_QUERY",
                "memoryRisk", "query result list + workbook buffer + response buffer",
                "bulkQueryMode", "LIMIT/OFFSET chunk query",
                "chunkSize", exportJobService.getDefaultChunkSize(),
                "message", "Normal download cannot safely load the full query result into memory before the browser download starts. Use bulk download instead.",
                "recommendedFlow", "BULK_EXPORT_JOB"
        ));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ExportJobResponse> getJob(@PathVariable UUID jobId) {
        return exportJobService.findJob(jobId)
                .map(ExportJobResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{jobId}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID jobId) {
        Resource resource = exportJobService.getCompletedFile(jobId);
        String fileName = resource.getFilename() == null ? "export.xlsx" : resource.getFilename();
        return excelDownload(resource, fileName);
    }

    @PostMapping("/{jobId}/cancel")
    public ExportJobResponse cancel(@PathVariable UUID jobId) {
        return ExportJobResponse.from(exportJobService.cancelJob(jobId));
    }

    @PostMapping("/cleanup")
    public Map<String, Integer> cleanup() {
        return Map.of("removedFiles", exportJobService.cleanupCompletedFiles());
    }

    private ResponseEntity<Resource> excelDownload(Resource resource, String fileName) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }
}
