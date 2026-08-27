**Currently**
1. Download [avro-tools-1.9.1.jar](https://repo1.maven.org/maven2/org/apache/avro/avro-tools/1.11.4/avro-tools-1.11.4.jar)
2. Delete the generated files
3. Run below command to auto-generate the avro java files after updating the schema.
```shell
java -jar ~/Downloads/avro-tools-1.11.4.jar compile schema pipeline-service/modules/pms-contracts/src/main/generated/avro/execution/{filename}.avsc pipeline-service/modules/pms-contracts/src/main/generated/java
```

---
**Why is it not yet migrated to bazel**
>This was the PR for avro plugin in bazel -> https://github.com/harness/harness-core/pull/21636/files
the plugin required this argument with bazel build --incompatible_restrict_string_escapes=false which was creating some issue with bazel_script, that's why it wasn't merged