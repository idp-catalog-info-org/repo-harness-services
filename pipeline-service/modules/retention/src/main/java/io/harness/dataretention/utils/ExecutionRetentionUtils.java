/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.dataretention.utils.ExecutionRetentionConstants.ZST_DECOMPRESSED_SIZE_METADATA_KEY;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection;
import io.harness.dataretention.entity.beans.RetentionFileData;
import io.harness.dataretention.entity.beans.RetentionFileFormat;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.objectstore.StorageObject;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.utils.Utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.Zstd;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class ExecutionRetentionUtils {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final ZoneId UTC_ZONE_ID = ZoneId.of("UTC");

  public byte[] convertDBRecordToBytes(Object dbObject, ExecutionRetentionObjectStoreCollection collection) {
    if (RetentionFileFormat.JSON.equals(collection.getOriginalFileFormat())) {
      try {
        String jsonData;
        if (collection.isUseRecasterForConversion()) {
          jsonData = RecastOrchestrationUtils.toJson(dbObject);
        } else {
          jsonData = objectMapper.writeValueAsString(dbObject);
        }
        return Utils.convertStringToBytes(jsonData);
      } catch (JsonProcessingException | InvalidArgumentsException e) {
        log.error(String.format("[DATA_RETENTION]: Failed to convert object to json for collection: %s",
                      collection.getCollectionName()),
            e);
        throw new InternalServerErrorException(
            String.format("[DATA_RETENTION]: Failed to convert object to json for collection: %s",
                collection.getCollectionName()),
            e);
      }
    }
    throw new InvalidRequestException(String.format("[DATA_RETENTION]: File Format: %s, is not currently supported for "
            + "converting object to string for collection: %s",
        collection.getOriginalFileFormat(), collection.getCollectionName()));
  }

  /*
   * The below function is No-OP if the file format required is JSON
   * It will return compressed file if required format is JSON_ZSTD
   */
  public byte[] compressBytesIfRequired(byte[] value, ExecutionRetentionObjectStoreCollection collection) {
    if (RetentionFileFormat.JSON_ZSTD.equals(collection.getFileFormat())) {
      /*
       * We are only compressing the graph collection to ZST, because of 2 reasons:
       * 1. Other collections are actively used in major flows(List view etc.)
       * 2. Un-compressed execution graph will occupy a lot of space
       * For e.g. The record for an execution containing ~570 steps had inflated cacheEntities size as 6.7MB
       * On compressing it with ZST it goes to around 980 KB
       */
      return Zstd.compress(value);
    }
    return value;
  }

  /*
   * The below function is No-OP if the file format required is JSON
   * If the format is ZSTD, it will be decompressed by fetching the original size from the metadata
   */
  private byte[] deCompressBytesIfRequired(
      ExecutionRetentionObjectStoreCollection collection, byte[] value, Map<String, Object> fileMetadata) {
    if (RetentionFileFormat.JSON_ZSTD.equals(collection.getFileFormat())) {
      if (fileMetadata != null && fileMetadata.containsKey(ZST_DECOMPRESSED_SIZE_METADATA_KEY)) {
        return Zstd.decompress(value, (int) fileMetadata.get(ZST_DECOMPRESSED_SIZE_METADATA_KEY));
      } else {
        throw new InvalidRequestException(
            String.format("[DATA_RETENTION]: File Metadata doesn't contain the ZSTD decompressed size to decompress "
                    + "the object store file for collection : %s",
                collection.getCollectionName()));
      }
    }
    return value;
  }

  /*
   * The below function converts the provided bytes to java object by casting to the provided classType
   */
  public Object convertBytesToObject(ExecutionRetentionObjectStoreCollection collection, byte[] value,
      Map<String, Object> fileMetadata, Class<?> classType) {
    byte[] deCompressedBytes = deCompressBytesIfRequired(collection, value, fileMetadata);
    if (RetentionFileFormat.JSON.equals(collection.getOriginalFileFormat())) {
      try {
        if (collection.isUseRecasterForConversion()) {
          return RecastOrchestrationUtils.fromBytes(deCompressedBytes, classType);
        } else {
          return objectMapper.readValue(deCompressedBytes, classType);
        }
      } catch (IOException e) {
        log.error(String.format("[DATA_RETENTION]: Failed to convert json to object for collection: %s",
                      collection.getCollectionName()),
            e);
        throw new InternalServerErrorException(
            String.format("[DATA_RETENTION]: Failed to convert json to object for collection: %s",
                collection.getCollectionName()),
            e);
      }
    }
    throw new InternalServerErrorException(String.format("[DATA_RETENTION]: File Format: %s, is not currently "
            + "supported for converting json to object for collection: %s",
        collection.getOriginalFileFormat(), collection.getCollectionName()));
  }

  public String buildFilePathForObjectStore(
      String accountId, Long endTs, String uuid, ExecutionRetentionObjectStoreCollection collection) {
    String date = dateTimeFormatter.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(endTs), UTC_ZONE_ID));
    return String.format("%s/%s/%s/%s/%s.%s", accountId, date, collection.getDbName(), collection.getFolderName(), uuid,
        collection.getFileExtension()); // accountID/yyyyMMdd/pms/collectionName/uuid.fileExtension
  }

  public RetentionFileData getRetentionFileData(String uuid, StorageObject object,
      ExecutionRetentionObjectStoreCollection collection, Map<String, Object> metadata) {
    if (object == null) {
      log.error(String.format("[DATA_RETENTION]: Uploaded object cannot be null for uuid: %s, collection: %s", uuid,
          collection.getCollectionName()));
      throw new InternalServerErrorException(
          String.format("[DATA_RETENTION]: Uploaded object cannot be null for uuid: %s, collection: %s", uuid,
              collection.getCollectionName()));
    }
    return RetentionFileData.builder()
        .uuid(uuid)
        .filePath(object.getName())
        .fileSize(object.getSize())
        .collection(collection)
        .metadata(metadata)
        .fileFormat(collection.getFileFormat())
        .build();
  }

  public String buildFileUUIDForCollection(
      String planExecutionId, String uuid, ExecutionRetentionObjectStoreCollection collection) {
    switch (collection) {
      case EXECUTION_SUMMARY, EXECUTION_GRAPH, EXECUTION_METADATA -> {
        return planExecutionId;
      }
      case APPROVAL_INSTANCES -> {
        return uuid;
      }
      case EXECUTION_SUB_GRAPH -> {
        return String.format("%s_%s", planExecutionId, uuid);
      }
      default -> throw new InternalServerErrorException(String.format(
              "[DATA_RETENTION]: Collection: %s, is not currently supported for retaining in object store", collection.getName()));
    }
  }

  public List<String> extractNodeExecutionIDsForSubGraph(List<String> canonicalKeys) {
    List<String> nodeExecutionIDs = new ArrayList<>();
    if (isEmpty(canonicalKeys)) {
      return nodeExecutionIDs;
    }
    for (String canonicalKey : canonicalKeys) {
          String[] parts = canonicalKey.split("/");
          if (parts.length != 5) {
            throw new InternalServerErrorException(
                String.format("Provided canonicalKey: %s doesn't correspond to a subgraph", canonicalKey));
          }
          nodeExecutionIDs.add(parts[1]);
        }
        return nodeExecutionIDs;
    }
  }
