# CDC Kafka Implementation for GitOps - PR Summary

## 1. Kafka Setup

### Kafka Connect CDC Connectors

Created two Debezium MongoDB connectors to capture changes from the `harness-gitops` database:

#### 1.1 Utilization Snapshot Connector (Avro)
```bash
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "gitops-utilization-snapshot-connector",
    "config": {
      "connector.class": "io.debezium.connector.mongodb.MongoDbConnector",
      "mongodb.connection.string": "mongodb://mongo1:27017/?replicaSet=rs0",
      "topic.prefix": "gitops",
      "database.include.list": "harness-gitops",
      "collection.include.list": "harness-gitops.utilization_snapshot",
      "key.converter": "io.confluent.connect.avro.AvroConverter",
      "key.converter.schema.registry.url": "http://schema-registry:8081",
      "value.converter": "io.confluent.connect.avro.AvroConverter",
      "value.converter.schema.registry.url": "http://schema-registry:8081",
      "transforms": "unwrap",
      "transforms.unwrap.type": "io.debezium.connector.mongodb.transforms.ExtractNewDocumentState",
      "transforms.unwrap.drop.tombstones": "false",
      "transforms.unwrap.delete.handling.mode": "rewrite",
      "transforms.unwrap.add.headers": "op"
    }
  }'
```
- **Serialization**: Avro with Schema Registry
- **Target topic**: `gitops.harness-gitops.utilization_snapshot`
- **Purpose**: Captures instance count snapshots

#### 1.2 Applications Connector (JSON)
```bash
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "gitops-applications-connector",
    "config": {
      "connector.class": "io.debezium.connector.mongodb.MongoDbConnector",
      "mongodb.connection.string": "mongodb://mongo1:27017/?replicaSet=rs0",
      "topic.prefix": "gitops",
      "database.include.list": "harness-gitops",
      "collection.include.list": "harness-gitops.applications",
      "key.converter": "org.apache.kafka.connect.json.JsonConverter",
      "key.converter.schemas.enable": "false",
      "value.converter": "org.apache.kafka.connect.json.JsonConverter",
      "value.converter.schemas.enable": "false",
      "transforms": "unwrap",
      "transforms.unwrap.type": "io.debezium.connector.mongodb.transforms.ExtractNewDocumentState",
      "transforms.unwrap.drop.tombstones": "false",
      "transforms.unwrap.delete.handling.mode": "rewrite",
      "transforms.unwrap.add.headers": "op",
      "transforms.unwrap.array.encoding": "document"
    }
  }'
```
- **Serialization**: JSON (no schema registry)
- **Target topic**: `gitops.harness-gitops.applications`
- **Purpose**: Captures GitOps application metadata with service labels
- **Why JSON**: Avro cannot handle MongoDB's dot replacement character (`~`) in label keys like `harness~io/serviceRef`
- **`array.encoding: document`** (REQUIRED): Argo `Application` docs contain arrays whose elements
  vary in shape/type across the population (`status.resources[]`, `history[]`, `conditions[]`, ...).
  The default `array.encoding=array` requires every array to be **homogeneous** and throws
  `DebeziumException: Field <x> ... is not a homogenous array` in the TRANSFORMATION stage, killing
  the task. `document` encodes each array as a struct with numbered fields (`_0`, `_1`, `_2`, ...)
  so heterogeneous elements no longer need a shared schema. Safe here because the value converter is
  schemaless JSON. See §3.5.


### Key Transform Configuration

All connectors use the **ExtractNewDocumentState** transform:
- Flattens Debezium envelope (removes `before`, `after`, `source` wrapper)
- Places document fields at top level (e.g., `_id` directly accessible)
- Adds operation type (`c`, `u`, `d`, `r`) as message header `__op`
- Essential for compatibility with existing Redis event handlers

### 1.3 QA & Production rollout
Kafka resources - https://harness0.harness.io/ng/account/l7B_kbSEQD2wjrM7PShm5w/module/code/orgs/Cloud_Infra/projects/harnessinfra/repos/kafka-resources/summary/refs/heads/main
Kafka strimzi connector - https://harness0.harness.io/ng/account/l7B_kbSEQD2wjrM7PShm5w/module/code/orgs/Cloud_Infra/projects/harnessinfra/repos/kafka-connect-strimzi/summary/refs/heads/main
Harness PL infra - https://harness0.harness.io/ng/account/l7B_kbSEQD2wjrM7PShm5w/module/code/repos/harness-pl-infra/pulls/124693/changes

### 1.4 Dashboards
Strimzi Kafka Connect Cluster - https://monitoring.harness.io/d/ef781fa9-08cb-4fad-bc01-6ec4b342c5d4
Kafka Consumer Dashboard - https://monitoring.harness.io/d/5cfa52d5-d338-42b2-b63f-078d35269fd9
Redis Streams Monitor - https://monitoring.harness.io/d/4e0a3feb-44a7-403b-85ff-e03880908dbe

**Applications connector — sync-only filtering via Mongo pre-image + `cursor.pipeline`:**

Non-sync updates are dropped at the **Mongo change stream** before Kafka/Redis when the collection is `harness-gitops.applications`. Requires `changeStreamPreAndPostImages.enabled: true` on the collection and:

```properties
capture.mode=change_streams_update_full_with_pre_image
```

Pipeline stages (legacy debezium-service builds this in `DebeziumConfiguration`; Kafka Connect uses env vars below):

1. **`$match`** — pass `insert`/`replace`, or `update` when:
   - pre-image is missing (fail-open), or
   - `startedat` / `finishedat` changed comparing the **full pre-image** `fullDocumentBeforeChange.app.status.operationstate` vs the **full post-image** `fullDocument.app.status.operationstate`. The comparison never uses `updateDescription.updatedFields`, which only carries whichever top-level keys a write touched (e.g. `app`, `lastModifiedAt`) — comparing a full document against it would spuriously pass metadata-only writes that never touch `app`.
2. **`$project`** — exclude configured fields (e.g. `ownerRef`)

**Kafka Connect (Strimzi) deployment:**

1. **harness-pl-infra** (`connectorConfigEnv` in `kafka-connect-strimzi-values.yaml`):
   ```yaml
   DEBEZIUM_MONGO_GITOPS_APPLICATIONS_CAPTURE_MODE: change_streams_update_full_with_pre_image
   DEBEZIUM_MONGO_GITOPS_APPLICATIONS_CURSOR_PIPELINE: '<pipeline json>'
   ```
2. **kafka-connect-strimzi** repo (`debezium-mongo-gitops-applications` connector template):
   ```properties
   capture.mode=${DEBEZIUM_MONGO_GITOPS_APPLICATIONS_CAPTURE_MODE}
   cursor.pipeline=${DEBEZIUM_MONGO_GITOPS_APPLICATIONS_CURSOR_PIPELINE}
   ```

**Legacy debezium-service:** `DebeziumConfiguration` sets capture mode + pipeline only when `monitoredCollection` is `harness-gitops.applications`.

### Sync-only flow (pre-image cursor.pipeline)

```
MongoDB applications UPDATE
  └─ change stream (pre-image enabled)
       └─ cursor.pipeline $match (pre-image vs post-image)
            ├─ label-only update (sync timestamps unchanged) → dropped at Mongo
            └─ sync timestamp change → passes
                 └─ Debezium MongoDbConnector + ExtractNewDocumentState (fullDocument)
                      └─ Kafka topic / Redis stream
                           └─ GitopsApplicationsRedisEventHandler
                                └─ GitopsAppInfoUpsert (partial index + IS DISTINCT FROM) → gitops_app_info
```

---

## 2. Code Changes

### 2.1 Consumer Infrastructure

#### Created: `GitOpsUtilizationSnapshotKafkaConsumer.java`
- Extends `HKafkaConsumer<GenericRecord, Void>`
- Uses Avro deserializer for utilization snapshot events
- Consumer group: `ng-manager-gitops-utilization-snapshot`

#### Created: `GitopsApplicationsKafkaConsumer.java`
- Extends `HKafkaConsumer<String, Void>` (JSON, not Avro)
- Uses `StringDeserializer` for applications events
- Consumer group: `ng-manager-gitops-applications`
- **Special handling**: JSON serialization to handle MongoDB label keys


### 2.2 Message Handlers

#### Created: `AbstractGitopsCdcMessageHandler.java`
Base class for Avro-based CDC handlers:
- **Input**: `GenericRecord` (Avro)
- **Extracts**: `_id` from top-level Avro field
- **Reads operation type**: From `__op` header
- **Converts**: Avro GenericRecord → JSON string
- **Delegates**: To existing Redis event handlers
- **Features**:
    - Feature flag gating (`CDS_GITOPS_ENABLE_KAFKA_CONNECT`)
    - Bounded retry (3 attempts with exponential backoff)
    - Idempotent handling (handlers use UPSERT)

#### Created: `AbstractGitopsJsonCdcMessageHandler.java`
Base class for JSON-based CDC handlers (applications):
- **Input**: `String` (JSON)
- **Extracts**: `_id` from JSON
- **Reads operation type**: From `__op` header
- **Key feature**: **MongoDB label key normalization**
    - Replaces `~` → `.` in `app.objectmeta.labels` keys
    - Makes raw JSON compatible with Spring MongoDB's `MappingMongoConverter`
    - Enables `harness~io/serviceRef` → `harness.io/serviceRef` conversion
    - Required because MongoDB stores `harness.io/serviceRef` as `harness~io/serviceRef`
- **Delegates**: To existing Redis event handlers
- **Features**: Same as Avro handler (FF gating, retry, idempotency)

#### Created: `GitOpsUtilizationSnapshotCdcMessageHandler.java`
- Extends `AbstractGitopsCdcMessageHandler`
- Delegates to `GitOpsUtilizationSnapshotRedisEventHandler`
- Writes to TimescaleDB `gitops_instance_stats` table

#### Created: `GitopsApplicationsCdcMessageHandler.java`
- Extends `AbstractGitopsJsonCdcMessageHandler` (JSON-based)
- Delegates to `GitopsApplicationsRedisEventHandler`
- Writes to TimescaleDB `gitops_app_info` table
- **Extracts serviceid** from normalized `harness.io/serviceRef` label


### 2.3 Configuration

#### Modified: `120-ng-manager/config.yml`
Added CDC Kafka configuration:
```yaml
cdcKafka:
  enabled: ${CDC_KAFKA_ENABLED:-false}
  consumers:
    - name: utilizationSnapshot
      topic: ${CDC_KAFKA_UTILIZATION_SNAPSHOT_TOPIC:-gitops.harness-gitops.utilization_snapshot}
      maxPollRecords: ${CDC_KAFKA_UTILIZATION_SNAPSHOT_MAX_POLL_RECORDS:-500}
    - name: applications
      topic: ${CDC_KAFKA_APPLICATIONS_TOPIC:-gitops.harness-gitops.applications}
      maxPollRecords: ${CDC_KAFKA_APPLICATIONS_MAX_POLL_RECORDS:-500}
```

Also enabled TimescaleDB:
```yaml
enableDashboardTimescale: true

timescaledb:
  timescaledbUrl: "jdbc:postgresql://localhost:5432/harness"
  timescaledbUsername: "postgres"
  timescaledbPassword: ""
```

#### Created: `CdcKafkaConfig.java`
Configuration model for CDC Kafka consumers:
- Maps YAML config to Java objects
- Provides consumer lookup by name
- Constants for consumer names

#### Created: `CdcKafkaConsumerConfig.java`
Individual consumer configuration:
- Topic name
- Max poll records
- Consumer name

#### Created: `CdcKafkaConstants.java`
Shared constants:
- Consumer group IDs
- Executor service name
- CDC Kafka executor service name

### 2.4 Application Bootstrap

#### Modified: `NextGenApplication.java`
Added CDC Kafka consumer registration:
```java
private void registerCdcKafkaConsumers(Injector injector, Environment environment, CdcKafkaConfig cdcKafkaConfig) {
  if (!cdcKafkaConfig.isEnabled()) {
    log.info("CDC Kafka is disabled, skipping consumer registration");
    return;
  }

  log.info("Initializing CDC Kafka consumers for GitOps");
  
  // Register maintenance listener (CRITICAL: prevents consumers from blocking)
  ConsumerMaintenanceListener listener = injector.getInstance(ConsumerMaintenanceListener.class);
  injector.getInstance(MaintenanceController.class).register(listener);
  listener.syncMaintenanceState();

  // Register consumers
  environment.lifecycle().manage(injector.getInstance(GitOpsUtilizationSnapshotKafkaConsumer.class));
  log.info("Registered CDC Kafka consumer: GitOpsUtilizationSnapshotKafkaConsumer");

  environment.lifecycle().manage(injector.getInstance(GitopsApplicationsKafkaConsumer.class));
  log.info("Registered CDC Kafka consumer: GitopsApplicationsKafkaConsumer");
}
```

**Critical fix**: Added `ConsumerMaintenanceListener` registration and sync to prevent consumers from blocking on startup waiting for maintenance mode to clear.

---

## 3. Testing Prerequisites

### 3.1 Infrastructure Requirements

#### MongoDB Replica Set
- **Why**: Debezium requires a replica set (not standalone MongoDB) to read change streams
- **Setup**: 3-node replica set (mongo1, mongo2, mongo3) on rs0
- **Database**: `harness-gitops`
- **Collections**: `utilization_snapshot`, `applications`

#### Kafka Stack
```bash
cd ~/Documents/workspace/harness-core/kafka-client
docker-compose up -d
```
Components:
- **Kafka broker**: `localhost:39092` (external), `localhost:9092` (internal)
- **Schema Registry**: `localhost:7081` (for Avro serialization)
- **Kafka Connect**: `localhost:8083` (Debezium connectors)
- **Kafdrop**: `localhost:19000` (Kafka UI for debugging)

#### TimescaleDB
- **Container**: `110-change-data-capture-timescale-1`
- **Port**: `localhost:5432`
- **Database**: `harness`
- **Tables**: `gitops_instance_stats`, `gitops_app_info`
- **Migrations**: All 122 TimescaleDB migrations must complete successfully

### 3.2 Environment Variables

Required for ng-manager startup:
```bash
export CDC_KAFKA_ENABLED=true
export KAFKA_GENERAL_BOOTSTRAP_SERVER_URLS=localhost:39092
export KAFKA_GENERAL_SCHEMA_REGISTRY_URL=http://localhost:7081
export KAFKA_GENERAL_SECURITY_PROTOCOL=PLAINTEXT
```

### 3.3 Feature Flag

- **Flag**: `CDS_GITOPS_ENABLE_KAFKA_CONNECT`
- **Scope**: Global
- **Behavior**: When OFF, consumers acknowledge messages without processing (prevents replay on re-enable)

### 3.4 Verification Steps

#### 1. Verify Kafka Connect connectors are running:
```bash
for c in gitops-utilization-snapshot-connector gitops-applications-connector; do
  curl -s http://localhost:8083/connectors/$c/status | jq '{connector: .connector.state, task: .tasks[0].state}'
done
```
Expected: Both `connector` and `task` should be `RUNNING`

#### 2. Verify ng-manager startup logs:
```
✅ "Registered CDC Kafka consumer: GitOpsUtilizationSnapshotKafkaConsumer"
✅ "Registered CDC Kafka consumer: GitopsApplicationsKafkaConsumer"
```

#### 3. Insert test data (see Section 1 in testing notes)

#### 4. Verify data flow:
- MongoDB insert → Kafka topic (check in Kafdrop: http://localhost:19000)
- Kafka topic → ng-manager logs (search for `[CDC-Kafka]`)
- ng-manager → TimescaleDB (run SELECT queries)

### 3.5 Common Issues & Fixes

#### Issue: Consumers stuck with "waiting for maintenance to complete"
**Cause**: `ConsumerMaintenanceListener` not registered in CDC Kafka consumer registration  
**Fix**: Added in `NextGenApplication.registerGitOpsKafkaConsumers()`

#### Issue: Applications connector task FAILED with "Illegal character in: harness~io/serviceRef"
**Cause**: Trying to use Avro serialization on labels with MongoDB tilde character  
**Fix**: Switched applications connector to JSON serialization

#### Issue: serviceid is null for applications
**Cause**: Label key normalization missing (handler receives `harness~io/serviceRef` instead of `harness.io/serviceRef`)  
**Fix**: Added `normalizeMongoLabelKeys()` in `AbstractGitopsJsonCdcMessageHandler`

#### Issue: Applications connector task FAILED with "is not a homogenous array"
**Symptom** (Kafka Connect log):
```
ERROR Error encountered in task gitops-applications-connector-0. Executing stage 'TRANSFORMATION'
with class 'io.debezium.connector.mongodb.transforms.ExtractNewDocumentState' ...
Caused by: io.debezium.DebeziumException: Field <field> of schema
gitops.harness-gitops.applications is not a homogenous array.
Check option 'struct' of parameter 'array.encoding'
    at io.debezium.connector.mongodb.transforms.MongoDataConverter.testType(MongoDataConverter.java:493)
    at io.debezium.connector.mongodb.transforms.ExtractNewDocumentState.newRecord(...)
```
**Cause**: `ExtractNewDocumentState` runs with the default `array.encoding=array`, which requires
every array in the document to be **homogeneous** (all elements infer to the same Connect schema).
Argo `Application` docs contain arrays whose element shapes/types vary across the population — e.g.
`status.resources[]`, `history[]`, `conditions[]`, and fields that flip between `null` and an object
(`health`, `syncstrategy`, ...). The first heterogeneous array throws, retry tolerance is exceeded,
and the **task → FAILED**, halting the entire applications flow.  
**Fix**: Add `"transforms.unwrap.array.encoding": "document"` to the applications connector config
(§1.2). This encodes each array as a struct with numbered fields (`_0`, `_1`, `_2`, ...) so
heterogeneous elements no longer need a shared schema. Safe because the value converter is
schemaless JSON. (Note: Debezium's message says `'struct'`, but the valid MongoDB-SMT values are
`array` (default) and `document`.)  
**Downstream note**: With `document` encoding, arrays arrive as `{"_0":..,"_1":..}` objects rather
than JSON arrays. The current applications handler is **verified safe** with this change (see below):
it deserializes the payload into the minimal `Application` POJO (`io.harness.events.base.Application`
/ `Appsync`) and only reads scalar fields — `app.objectmeta.labels` (a `Map`, not an array) and
`app.status.operationstate.startedat/finishedat`. Spring's `MappingMongoConverter` tolerates the
`{"_0":..}` object encoding for the `ArrayList` fields on `OperationState` (`operation.sync.resources`,
`syncoptions`, `syncresult.resources`, `info`), so `array.encoding=document` does **not** move the
failure downstream. Caveat only applies to **future** handlers: if one starts reading those array
fields as ordered lists, it must account for the numbered-field object shape.  
**Reproduce**: Insert a doc with a heterogeneous array into `harness-gitops.applications`, e.g.
`db.applications.insertOne({_id:"probe", mixed:[1,"two",{three:3}]})` — the task fails with the
signature above. After applying the fix and restarting the task, the same insert flows through as
`"mixed": {"_0":1.0,"_1":"two","_2":{"three":3.0}}`.  
**Verified end-to-end** (2026-07-14): with the connector on `array.encoding=document`, an update to
`myriam-gitops-app` carrying a populated `operationstate` (its `resources`/`syncoptions` arrays
encoded as `{"_0":..}` objects on the topic) deserialized cleanly in ng-manager and upserted the
correct `last_sync_startedat_ts`/`serviceid` into TimescaleDB `gitops_app_info`.

### 3.6 Key Technical Decisions

#### Why JSON for Applications?
MongoDB replaces dots in map keys with `~` (tilde) because dots are reserved for nested field access. When Kafka Connect reads raw BSON and serializes to Avro, the Avro schema validator rejects `~` as an illegal field name character. JSON has no such restriction, so we use JSON serialization for applications and normalize the label keys (`~` → `.`) in the handler.

#### Why Avro for Utilization Snapshot?
This collection doesn't have problematic field names, so we use Avro for:
- Schema validation at serialization time
- Compact binary format (smaller message size)
- Schema evolution support via Schema Registry

#### Why ExtractNewDocumentState Transform?
This Debezium transform flattens the change event envelope, making `_id` directly accessible at the top level. This matches the structure expected by the existing Redis event handlers, allowing code reuse.

---

## Summary

This PR implements **CDC Kafka support for GitOps data** with two parallel flows:
1. **utilization_snapshot** → `gitops_instance_stats` (Avro)
2. **applications** → `gitops_app_info` with serviceid (JSON + label normalization)
