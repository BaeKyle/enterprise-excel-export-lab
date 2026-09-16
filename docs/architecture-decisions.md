# Architecture Decisions

This document explains the main architectural decisions behind the Enterprise Excel Export Lab.

The project is a portfolio-safe, single-instance sample. It demonstrates the lifecycle of a large Excel export without claiming to solve every distributed batch-processing concern.

---

## 1. Why asynchronous export instead of a synchronous HTTP download?

### Context

A normal Excel download keeps all work inside a single HTTP request:

1. Query the export data.
2. Keep the query result in application memory.
3. Build the Excel workbook.
4. Write the response to the browser.
5. Keep the user waiting until the entire operation finishes.

This approach is simple for small exports, but its reliability decreases as the number of rows and the workbook size increase.

Potential failure modes include:

- HTTP, proxy, or load-balancer timeout
- excessive JVM heap usage
- repeated requests because the user cannot see progress
- partially generated responses
- difficulty supporting cancellation
- orphaned temporary files

### Decision

Large exports are represented as background jobs.

The request that creates the job returns immediately. A background worker reads the data and generates the Excel file, while the browser polls the job status. The completed file is downloaded through a separate request.

```text
Create export job
  -> return job ID
  -> execute in background
  -> read rows in chunks
  -> stream rows to an XLSX file
  -> update progress
  -> download completed file
```

### Consequences

Benefits:

- The request thread is not occupied for the entire export duration.
- The UI can display progress and elapsed time.
- Duplicate requests can be detected.
- Cancellation and cleanup can be modeled explicitly.
- File generation and file download have separate failure boundaries.

Costs:

- Job state must be tracked.
- Generated files require lifecycle management.
- Interrupted jobs need a recovery policy.
- Multi-instance deployment requires persistent state and execution coordination.

### Boundary

This pattern is intended for large or long-running exports.

Small exports may still use a synchronous download because creating and polling a background job for every file would add unnecessary operational and user-experience complexity.

---

## 2. Why application-managed asynchronous execution?

### Context

The following implementation options were considered:

- synchronous HTTP processing
- Spring `@Async` or an application-managed executor
- Spring Batch
- an external message queue and worker
- a distributed job-processing platform

### Decision

This sample uses application-managed asynchronous execution because its primary purpose is to demonstrate the export lifecycle with minimal infrastructure:

- create a job
- execute work outside the request thread
- expose progress
- prevent duplicate requests
- support cancellation
- download the completed file
- clean up generated files

The sample runs as a single application instance. Introducing a message broker or a full batch platform would significantly increase the setup and operational scope without improving the main demonstration.

### Why not Spring Batch?

Spring Batch would be appropriate when the system requires:

- persistent job and step metadata
- restart from a defined checkpoint
- formal reader, processor, and writer pipelines
- scheduled or recurring workloads
- configurable skip and retry policies
- partitioned or parallel processing
- operational control over multiple batch job types

This sample has one focused export workflow and does not require all of those capabilities. Application-managed execution keeps the example smaller and makes the export-specific lifecycle easier to inspect.

For a production system with strict restartability, audit requirements, or many job types, Spring Batch would be a stronger candidate.

### Why not a message queue?

A message queue would be appropriate when:

- API servers and workers must scale independently
- jobs must survive application restarts
- workload must be buffered during traffic spikes
- workers execute across multiple application instances
- durable delivery and retry semantics are required

The current sample is intentionally single-instance and locally executable. Adding Kafka, RabbitMQ, or another broker would introduce infrastructure and delivery semantics outside the main scope of the project.

For a distributed production environment, the architecture could evolve as follows:

```text
API
  -> persistent job table
  -> transactional outbox or job event
  -> message broker
  -> stateless export workers
  -> object storage
```

### Limitations of the current choice

The current application-local executor is appropriate for demonstrating the export lifecycle in a single application instance.

It does not independently provide:

- job recovery after an application restart
- coordination between multiple application instances
- persistent retry history
- durable job state
- distributed duplicate-execution prevention

A production deployment requiring those capabilities would need persistent job metadata and explicit worker coordination through a database, batch framework, or message queue.

Moving work to an asynchronous thread prevents a long-running HTTP request, but it does not automatically make the job durable or distributed.

---

## 3. Why Apache POI SXSSFWorkbook?

### Context

`XSSFWorkbook` keeps the workbook structure in memory. For a large export, application memory may be consumed by:

- the database result list
- workbook rows and cells
- cell styles and shared strings
- response buffering

Using a streaming workbook alone does not eliminate every source of memory usage, but it prevents the entire generated workbook from remaining in heap memory.

### Decision

The project uses Apache POI `SXSSFWorkbook`.

Only a limited window of rows remains accessible in memory. Older rows are flushed to temporary storage while generation continues.

### Consequences

Benefits:

- workbook memory usage is less dependent on the total row count
- larger workbooks can be generated with a bounded row window
- generated rows do not all remain in application memory

Costs:

- temporary files must be disposed reliably
- flushed rows cannot be freely revisited
- some workbook features are harder to use
- the writer must operate in a forward-only manner

The implementation therefore treats Excel generation as a streaming write operation rather than an in-memory document editing operation.

---

## 4. Why chunked database queries?

### Context

Streaming the workbook is not sufficient if the application retrieves every database row into a single `List`.

In that case, the query result itself remains a major source of heap usage even when `SXSSFWorkbook` is used.

### Decision

The background worker reads a bounded number of rows for each iteration. Each chunk is written to the workbook before the next chunk is retrieved.

```text
query chunk
  -> write chunk
  -> release chunk references
  -> query next chunk
```

This bounds the amount of business data held in application memory at one time.

### Chunk-size trade-off

Chunk size is a tuning parameter, not a universal constant.

A very small chunk size can cause:

- excessive database round trips
- increased query overhead
- longer total processing time

A very large chunk size can cause:

- increased heap usage
- longer garbage-collection pauses
- slower cancellation response

The appropriate chunk size should be selected using measurements from the target database, row width, workbook structure, and available JVM heap.

---

## 5. Why OFFSET pagination, and what are its limits?

### Decision

This sample uses `OFFSET` and `FETCH NEXT` because:

- the mechanism is easy to understand
- arbitrary pages can be requested
- it works well with generated sample data
- progress can be demonstrated using an offset and total row count
- it keeps the portfolio sample small and reproducible

Example:

```sql
SELECT ...
FROM orders
ORDER BY id
OFFSET :offset ROWS
FETCH NEXT :limit ROWS ONLY
```

### Limitations

OFFSET pagination is not the preferred strategy for every large production export.

#### Increasing query cost

As the offset grows, the database may need to scan or sort an increasing number of preceding rows before returning the requested page.

Later chunks may therefore become slower than earlier chunks.

#### Consistency during concurrent changes

If rows are inserted or deleted while the export is running, offset boundaries can shift. Depending on transaction isolation and ordering, rows can be duplicated or skipped.

#### Stable ordering requirement

The query must use deterministic and unique ordering.

Ordering only by a non-unique business column is insufficient because rows with the same value may change position between page requests.

### Production alternative: keyset pagination

For a large sequential export, keyset pagination is generally preferable when a stable indexed key is available.

```sql
SELECT ...
FROM orders
WHERE id > :lastId
ORDER BY id
FETCH NEXT :limit ROWS ONLY
```

Benefits:

- avoids scanning an increasingly large offset
- provides more stable page boundaries
- usually scales better for sequential processing

Trade-offs:

- requires a stable and indexed cursor column
- arbitrary page access is difficult
- compound ordering requires a compound cursor
- consistency still requires an explicit isolation or snapshot policy

### Selection criteria

Use OFFSET pagination when:

- the dataset is moderate
- implementation simplicity is important
- the database execution plan remains stable
- the export uses an immutable or snapshot-like dataset

Use keyset pagination when:

- the dataset is large
- the export scans rows sequentially
- an indexed and stable ordering key exists
- the cost of later OFFSET queries becomes significant

Use cursor streaming when:

- the database driver supports controlled streaming
- a long-running cursor and transaction are operationally acceptable
- connection-pool occupancy is understood and controlled

---

## 6. Why polling instead of SSE or WebSocket?

### Decision

The sample uses polling because export progress is low-frequency state and does not require a permanent bidirectional connection.

Polling also keeps the project locally executable without additional connection management or proxy configuration.

### Trade-offs

Polling causes repeated HTTP requests and introduces a progress-display delay equal to the polling interval.

Server-Sent Events would be a reasonable production alternative when immediate server-to-client progress updates are important.

WebSocket would generally be unnecessary unless the feature requires bidirectional real-time interaction.

---

## 7. Duplicate request handling

### Context

When a large export does not immediately download a file, users may click the export button repeatedly. Starting every request independently wastes database, CPU, memory, and disk resources.

### Decision

The sample checks whether a matching export is already running and reuses the existing job instead of starting another equivalent job.

### Production considerations

An in-memory check is sufficient only for a single application instance.

A multi-instance deployment would require an atomic database state transition, unique constraint, distributed lock, or queue-level coordination so that two workers cannot acquire the same logical job simultaneously.

---

## 8. Cancellation and cleanup

### Context

Cancellation is not complete when the UI merely closes a popup.

The background worker may continue reading data and writing a file unless cancellation is represented in the job lifecycle and checked during processing.

### Decision

The sample exposes cancellation as an explicit job operation. The worker checks the job state between chunks and stops processing when cancellation is requested.

Partial and expired files are removed through the cleanup path.

### Limitation

Cancellation is cooperative rather than immediate. A worker can stop only when it reaches a cancellation check, so the maximum cancellation delay is affected by chunk size and the duration of each database query or workbook write.

---

## 9. Production evolution

The current repository demonstrates the application pattern. A distributed production implementation should additionally consider:

1. Persistent job metadata
2. Atomic job acquisition
3. Recovery for abandoned `RUNNING` jobs
4. Retry and maximum-attempt policies
5. User and tenant authorization
6. Object storage for completed files
7. Scheduled expiry and cleanup
8. Metrics, tracing, and structured failure reasons
9. Rate limits and per-user concurrency limits
10. Keyset pagination or a database snapshot strategy

A possible production topology is:

```text
Browser
  -> Export API
  -> Job database
  -> Queue
  -> Export worker
  -> Business database
  -> Object storage
  -> Download API
```

The purpose of this project is not to claim that a single-instance asynchronous executor solves every batch-processing concern.

It demonstrates the large-export lifecycle, makes its trade-offs explicit, and defines the conditions under which the architecture should evolve.
