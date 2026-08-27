/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.inputset.inputmetadata;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.ModuleType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.UnexpectedException;
import io.harness.pms.contracts.inputmetadata.InputsMetadataRequestBatch;
import io.harness.pms.contracts.inputmetadata.InputsMetadataRequestMetadata;
import io.harness.pms.contracts.inputmetadata.InputsMetadataRequestProto;
import io.harness.pms.contracts.inputmetadata.InputsMetadataResponse;
import io.harness.pms.contracts.inputmetadata.InputsMetadataResponseBatch;
import io.harness.pms.contracts.inputmetadata.InputsMetadataServiceGrpc.InputsMetadataServiceBlockingStub;
import io.harness.pms.utils.CompletableFutures;
import io.harness.pms.utils.PmsGrpcClientUtils;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(PIPELINE)
@Slf4j
public class InputsMetadataGenerator {
  @Inject Map<ModuleType, InputsMetadataServiceBlockingStub> inputsMetadataServiceBlockingStubMap;
  @Inject @Named("InputsMetadataRequestBatchSize") Integer inputsMetadataRequestBatchSize;
  @Inject @Named("InputsMetadataExecutorService") Executor executor;

  @Inject
  InputsMetadataGenerator(Map<ModuleType, InputsMetadataServiceBlockingStub> inputsMetadataServiceBlockingStubMap,
      @Named("InputsMetadataRequestBatchSize") Integer inputsMetadataRequestBatchSize,
      @Named("InputsMetadataExecutorService") Executor executor) {
    this.inputsMetadataServiceBlockingStubMap = inputsMetadataServiceBlockingStubMap;
    this.inputsMetadataRequestBatchSize = inputsMetadataRequestBatchSize;
    this.executor = executor;
  }

  public Set<InputsMetadataResponse> fetchInputsMetadata(
      Set<InputsMetadataRequest> inputsMetadataRequests, InputsMetadataRequestMetadata inputsMetadataRequestMetadata) {
    Set<InputsMetadataResponseBatch> responseBatches =
        fetchInputsMetadataResponseBatches(inputsMetadataRequests, inputsMetadataRequestMetadata);
    Set<InputsMetadataResponse> response = new HashSet<>();
    for (InputsMetadataResponseBatch responseBatch : responseBatches) {
      response.addAll(responseBatch.getInputsMetadataResponseList());
    }
    return response;
  }

  private Set<InputsMetadataResponseBatch> fetchInputsMetadataResponseBatches(
      Set<InputsMetadataRequest> inputsMetadataRequests, InputsMetadataRequestMetadata inputsMetadataRequestMetadata) {
    Multimap<ModuleType, InputsMetadataRequestBatch> expansionRequestBatches =
        batchExpansionRequests(inputsMetadataRequests, inputsMetadataRequestMetadata);
    CompletableFutures<InputsMetadataResponseBatch> completableFutures = new CompletableFutures<>(executor);

    for (ModuleType module : expansionRequestBatches.keySet()) {
      for (InputsMetadataRequestBatch inputsMetadataRequestBatch : expansionRequestBatches.get(module)) {
        completableFutures.supplyAsync(() -> {
          InputsMetadataServiceBlockingStub blockingStub = inputsMetadataServiceBlockingStubMap.get(module);
          return PmsGrpcClientUtils.retryAndProcessException(
              blockingStub::generateInputsMetadata, inputsMetadataRequestBatch);
        });
      }
    }

    try {
      return new HashSet<>(completableFutures.allOf().get(5, TimeUnit.MINUTES));
    } catch (Exception ex) {
      log.error("Error fetching Inputs Metadata responses from services: " + ExceptionUtils.getMessage(ex), ex);
      throw new UnexpectedException("Error fetching Inputs Metadata responses from services", ex);
    }
  }

  private Multimap<ModuleType, InputsMetadataRequestBatch> batchExpansionRequests(
      Set<InputsMetadataRequest> inputsMetadataRequests, InputsMetadataRequestMetadata inputsMetadataRequestMetadata) {
    Set<ModuleType> requiredModules =
        inputsMetadataRequests.stream().map(InputsMetadataRequest::getModule).collect(Collectors.toSet());
    Multimap<ModuleType, InputsMetadataRequestBatch> inputsMetadataRequestBatches = HashMultimap.create();
    for (ModuleType module : requiredModules) {
      List<InputsMetadataRequestProto> protoRequests =
          inputsMetadataRequests.stream()
              .filter(expansionRequest -> ModuleType.CD.equals(module))
              .map(request
                  -> InputsMetadataRequestProto.newBuilder()
                         .setFqn(request.getFqn())
                         .setEntityType(request.getEntityType())
                         .setEntityId(request.getEntityId())
                         .setInputFormYaml(convertToByteString(request.getInputFormYaml()))
                         .setEntityIdentifierKey(request.getEntityIdentifierKey())
                         .build())
              .sorted(Comparator.comparing(InputsMetadataRequestProto::getFqn))
              .collect(Collectors.toList());
      List<List<InputsMetadataRequestProto>> protoRequestsBatched =
          Lists.partition(protoRequests, inputsMetadataRequestBatchSize);
      for (List<InputsMetadataRequestProto> protoRequest : protoRequestsBatched) {
        inputsMetadataRequestBatches.put(module,
            InputsMetadataRequestBatch.newBuilder()
                .addAllInputsMetadataRequestProto(protoRequest)
                .setInputsMetadataRequestMetadata(inputsMetadataRequestMetadata)
                .build());
      }
    }
    return inputsMetadataRequestBatches;
  }

  private ByteString convertToByteString(String s) {
    return ByteString.copyFromUtf8(s);
  }
}