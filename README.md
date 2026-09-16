# Enterprise Excel Export Lab

[![CI](https://github.com/BaeKyle/enterprise-excel-export-lab/actions/workflows/ci.yml/badge.svg?branch=main&event=push)](https://github.com/BaeKyle/enterprise-excel-export-lab/actions/workflows/ci.yml)

Production-oriented Spring Boot sample for exporting large enterprise datasets
to Excel without freezing the browser, blocking a request thread, or loading all
rows into memory.

This project is a safe portfolio example. It uses generated sample data only and
does not contain company source code, internal URLs, real table names, customer
data, credentials, or business-specific documents.

## Demo

### Bulk Export in Progress
<img width="877" height="888" alt="2  Bulk Export in Progress" src="https://github.com/user-attachments/assets/11185f53-a131-41fb-b24c-9f7c2cb7c03f" />

### Export Completed
<img width="876" height="905" alt="3  Export Completed" src="https://github.com/user-attachments/assets/ebbf53ea-9e0f-4a67-b73c-8e0ad9c078d9" />

## What This Demonstrates

This project focuses on a common enterprise reporting problem:

```text
Large search result
  -> normal Excel download tries to handle everything in one request
  -> JVM/browser memory pressure appears before the user can reliably receive the file
  -> bulk export moves the work to a background job and reads rows by page
```

It is designed as a portfolio-safe implementation of the pattern, not a copy of
any production source code.

## Problem

Enterprise systems often need to export large datasets for reporting, audit, and
business review. A normal browser download works for small files, but large Excel
files can easily become 20MB or larger. If the application tries to generate and
download that file in one request, it can cause:

- high JVM heap usage
- slow responses
- request timeouts
- unstable report downloads
- browser memory pressure
- users repeatedly clicking download because the screen looks stuck
- duplicate export requests
- orphaned temporary files
- poor user feedback while the file is being generated

## Architecture

This sample compares two approaches:

| Flow | Use case | Trade-off |
| --- | --- | --- |
| Normal download | One unpaged query and one response | Fails with an insufficient-memory style response before a stable browser download starts |
| Bulk download | Large files such as 20MB+ Excel reports | Modal popup and status polling keep the main browser screen responsive |

Bulk export flow:

```text
User clicks export
  -> main screen or popup creates or reuses a running export job
  -> background worker reads rows in chunks, e.g. 1,000 rows per OFFSET/FETCH query
  -> SXSSFWorkbook streams rows to a temporary XLSX file
  -> UI polls job status and progress
  -> modal popup automatically starts the download after completion
  -> cleanup removes generated files
```

For the rationale, limitations, and production trade-offs behind these choices, see
[Architecture Decisions](docs/architecture-decisions.md).

## Features

- Browser demo page at `http://localhost:8080`
- Normal download failure simulation for 600,000-row Excel exports
- Bulk Excel export job through REST API
- Accounting-system style modal popup bulk download flow
- Cancel a running bulk export from the main screen or popup
- Generate 600,000 sample rows in the bulk flow
- Generate sample business rows through an in-memory H2/MyBatis query
- Read data in fixed-size chunks using `OFFSET` and `FETCH NEXT`
- Let users choose rows per chunk, defaulting to 1,000 rows
- Write `.xlsx` files using streaming workbook
- Track export status, row count, progress percent, elapsed time, expiry time, and file size
- Prevent duplicate bulk jobs while an export is already running
- Download the generated file after completion
- Cleanup generated files through an operations-style endpoint
- Cleanly separate controller, service, repository, and writer logic

## Senior Engineering Points

- Uses `SXSSFWorkbook` instead of keeping the full workbook in memory
- Keeps long-running work outside the request thread
- Avoids making the browser wait on a single long-running export response
- Demonstrates why the normal 20MB+ download path is blocked
- Shows the concrete memory risk: query result list + workbook buffer + response buffer
- Models the database-side solution with chunked MyBatis queries instead of loading all rows at once
- Returns job metadata so the UI can show meaningful progress and chunk size
- Handles duplicate clicks by returning the already-running job
- Supports user cancellation, including popup close cancellation
- Separates normal download and bulk export because they have different risk profiles
- Adds a cleanup path for generated files, which is often missed in export features
- Uses generated sample data only, making the repository safe to publish

## Performance Benchmark

[![Performance Benchmark](https://github.com/BaeKyle/enterprise-excel-export-lab/actions/workflows/performance.yml/badge.svg)](https://github.com/BaeKyle/enterprise-excel-export-lab/actions/workflows/performance.yml)

End-to-end bulk export benchmark executed through the same REST API used by the demo UI.

### Benchmark Environment

* Runner: GitHub-hosted `ubuntu-24.04`
* CPU available to job: 4 vCPU
* Memory available to job: 15.6 GiB
* Java: OpenJDK 17
* JVM heap limit: `-Xmx1024m`
* Query chunk size: 1,000 rows
* Data source: generated sample data with H2/MyBatis
* Excel writer: Apache POI SXSSF

### Benchmark Results

|    Rows | Chunk Size | Generation Time |   Throughput | File Size | Peak Process RSS |
| ------: | ---------: | --------------: | -----------: | --------: | ---------------: |
| 100,000 |      1,000 |         12.46 s | 8,023 rows/s |   3.26 MB |         976.9 MB |
| 300,000 |      1,000 |         83.54 s | 3,591 rows/s |   9.48 MB |       1,122.9 MB |
| 600,000 |      1,000 |        318.32 s | 1,885 rows/s |  18.82 MB |       1,184.5 MB |

The benchmark verifies the export flow with datasets up to **600,000 rows** in a reproducible GitHub Actions environment.

The export processes database records in fixed-size chunks and writes workbook data using Apache POI SXSSF instead of loading the entire result set and workbook into memory at once.

[View GitHub Actions benchmark run](https://github.com/BaeKyle/enterprise-excel-export-lab/actions/runs/34924825389)

> These values are reference measurements from a GitHub-hosted runner, not an SLA. Runtime can vary between runner instances. Peak Process RSS measures the Java process resident memory observed during the export, not JVM heap usage alone.

## Demo Flow

Open the browser demo:

```text
http://localhost:8080
```

Normal risky download:

```text
Click "Try normal 20MB+ download"
  -> API returns 507 INSUFFICIENT_MEMORY_FOR_NORMAL_DOWNLOAD
  -> UI explains the unpaged query and memory pressure
```

Bulk download:

```text
Click "Start Bulk Export"
  -> status changes to PENDING/RUNNING
  -> generates 600,000 sample rows
  -> exported row count increases
  -> download button is enabled after COMPLETED
```

Popup mode:

```text
Click "Popup mode export"
  -> modal popup starts the background export automatically
  -> progress is shown without leaving the main page
  -> completed file downloads automatically
  -> Stop and close cancels the job and removes the partial file
```

## Query Strategy

Normal download intentionally represents the anti-pattern:

```sql
SELECT ...
FROM large_report_source
ORDER BY id
```

The application would need to keep the full query result, Excel workbook, and
response buffer in memory at the same time. For the 600,000-row sample, the demo
returns:

```text
507 INSUFFICIENT_MEMORY_FOR_NORMAL_DOWNLOAD
```

Bulk download uses chunked reads:

```sql
SELECT ...
FROM large_report_source
ORDER BY id
OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
```

This keeps each query page small, gives the UI measurable progress, and avoids
making the browser own the long-running export request.

## API

Start export:

```bash
curl -X POST http://localhost:8080/api/exports/orders
```

Start export with a custom chunk size:

```bash
curl -X POST "http://localhost:8080/api/exports/orders?chunkSize=1000"
```

Normal risky download:

```bash
curl http://localhost:8080/api/exports/orders/normal/check
```

Check status:

```bash
curl http://localhost:8080/api/exports/{jobId}
```

Download:

```bash
curl -O -J http://localhost:8080/api/exports/{jobId}/download
```

Cleanup generated files:

```bash
curl -X POST http://localhost:8080/api/exports/cleanup
```

## Tech Stack

- Java 17
- Spring Boot 2.7
- MyBatis
- H2 Database
- Apache POI SXSSF
- Maven

## Run

```bash
mvn spring-boot:run
```

Then open:

```text
http://localhost:8080
```

By default, generated Excel files are stored in:

```text
build/export-files
```

## Repository Hygiene

The repository intentionally excludes generated artifacts:

- Maven `target/`
- generated Excel files
- runtime export files under `build/export-files/`
- local IDE files

Only source code, tests, configuration, and documentation should be committed.

## Design Notes

The important part is not Excel generation itself. The important part is
controlling memory usage while keeping the user-facing request responsive.

General pattern:

```text
request export
  -> create export job
  -> run background worker
  -> read rows by chunk
  -> stream rows to xlsx
  -> update job status
  -> download completed file
```

In a real production system, this pattern can be extended with:

- database keyset pagination or cursor streaming
- persistent job table
- user-based authorization for download files
- scheduled cleanup
- retry policy
- rate limiting
- export history
- object storage instead of local disk
- SSE or WebSocket progress updates instead of polling


