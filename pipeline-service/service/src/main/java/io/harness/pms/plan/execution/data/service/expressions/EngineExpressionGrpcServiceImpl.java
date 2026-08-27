/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.data.service.expressions;

import static io.harness.beans.FeatureName.CDS_DEPTH_LIMITED_EXPRESSION_RESOLUTION;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.blockExecutionMetadata.BlockExecutionMetadataService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnresolvedExpressionsException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.service.EngineExpressionProtoServiceGrpc.EngineExpressionProtoServiceImplBase;
import io.harness.pms.contracts.service.ExpressionEvaluateBlobRequest;
import io.harness.pms.contracts.service.ExpressionEvaluateBlobResponse;
import io.harness.pms.contracts.service.ExpressionRenderBlobRequest;
import io.harness.pms.contracts.service.ExpressionRenderBlobResponse;
import io.harness.pms.contracts.service.ExpressionRenderByNodeExecutionIdRequest;
import io.harness.pms.contracts.service.ExpressionResolveWithContextRequest;
import io.harness.pms.contracts.service.ExpressionResolveWithContextResponse;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.ExpressionModeMapper;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.serializer.JsonUtils;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class EngineExpressionGrpcServiceImpl extends EngineExpressionProtoServiceImplBase {
  private static final String CACHE_NAME = "pms-grpc-expression-rendering";
  private static final int DEPTH_THRESHOLD = 100;
  private static final int MAX_FUNCTOR_KEYWORD_LENGTH = 32;
  private final PmsEngineExpressionService pmsEngineExpressionService;
  private final NodeExecutionService nodeExecutionService;
  private final RedissonClient redissonClient;
  private static final int MAX_CONTEXT_MAP_SIZE =
      Optional.ofNullable(System.getenv("MAX_CONTEXT_MAP_SIZE")).map(Integer::parseInt).orElse(1024 * 1024);
  @Inject private PmsFeatureFlagService featureFlagService;
  @Inject private BlockExecutionMetadataService blockExecutionMetadataService;

  @Inject
  public EngineExpressionGrpcServiceImpl(PmsEngineExpressionService pmsEngineExpressionService,
      NodeExecutionService nodeExecutionService, @Named("lock") RedisConfig redisconfig) {
    this.pmsEngineExpressionService = pmsEngineExpressionService;
    this.nodeExecutionService = nodeExecutionService;
    this.redissonClient = RedissonClientFactory.getClient(redisconfig);
  }

  @Override
  public void renderExpression(
      ExpressionRenderBlobRequest request, StreamObserver<ExpressionRenderBlobResponse> responseObserver) {
    if (blockExecutionMetadataService.validate(request.getAmbiance())) {
      return;
    }
    var accountId = AmbianceUtils.getAccountId(request.getAmbiance());
    var nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(request.getAmbiance());

    if (featureFlagService.isEnabled(accountId, CDS_DEPTH_LIMITED_EXPRESSION_RESOLUTION)) {
      // getting cached count for this particular nodeExecution, if it is bigger than the threshold, we return error
      RMapCache<String, Integer> countCache = redissonClient.getMapCache(CACHE_NAME);
      var count = countCache.getOrDefault(nodeExecutionId, 0);
      if (count > DEPTH_THRESHOLD) {
        log.warn(
            String.format("Too deep recursion while rendering expression for accountId: %s and nodeExecutionId: %s",
                accountId, nodeExecutionId));
        responseObserver.onError(
            Status.RESOURCE_EXHAUSTED.withDescription("Recursion or too deep hierarchy in property interpretation")
                .asRuntimeException());
        return;
      }

      // we don't want to lock it so lets get latest count again to reduce race condition effects
      count = countCache.getOrDefault(nodeExecutionId, 0);
      count += 1;
      countCache.put(nodeExecutionId, count, 15, TimeUnit.MINUTES);
    }

    final String value;
    if (request.getExpressionMode() != ExpressionMode.UNKNOWN_MODE
        && request.getExpressionMode() != ExpressionMode.UNRECOGNIZED) {
      value = pmsEngineExpressionService.renderExpression(request.getAmbiance(), request.getExpression(),
          ExpressionModeMapper.fromExpressionModeProto(request.getExpressionMode()));
    } else {
      value = pmsEngineExpressionService.renderExpression(
          request.getAmbiance(), request.getExpression(), request.getSkipUnresolvedExpressionsCheck());
    }

    responseObserver.onNext(ExpressionRenderBlobResponse.newBuilder().setValue(value).build());
    responseObserver.onCompleted();
  }

  @Override
  public void evaluateExpression(
      ExpressionEvaluateBlobRequest request, StreamObserver<ExpressionEvaluateBlobResponse> responseObserver) {
    if (blockExecutionMetadataService.validate(request.getAmbiance())) {
      return;
    }
    var accountId = AmbianceUtils.getAccountId(request.getAmbiance());
    var nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(request.getAmbiance());

    if (featureFlagService.isEnabled(accountId, CDS_DEPTH_LIMITED_EXPRESSION_RESOLUTION)) {
      // getting cached count for this particular nodeExecution, if it is bigger than the threshold, we return error
      RMapCache<String, Integer> countCache = redissonClient.getMapCache(CACHE_NAME);
      var count = countCache.getOrDefault(nodeExecutionId, 0);
      if (count > DEPTH_THRESHOLD) {
        log.warn(
            String.format("Too deep recursion while evaluating expression for accountId: %s and nodeExecutionId: %s",
                accountId, nodeExecutionId));
        responseObserver.onError(
            Status.RESOURCE_EXHAUSTED.withDescription("Recursion or too deep hierarchy in property interpretation")
                .asRuntimeException());
        return;
      }

      // we don't want to lock it so lets get latest count again to reduce race condition effects
      count = countCache.getOrDefault(nodeExecutionId, 0);
      count += 1;
      countCache.put(nodeExecutionId, count, 15, TimeUnit.MINUTES);
    }

    final String value;
    Object evaluatedValue = pmsEngineExpressionService.evaluateExpression(request.getAmbiance(),
        request.getExpression(), ExpressionModeMapper.fromExpressionModeProto(request.getExpressionMode()), null);
    value = RecastOrchestrationUtils.toJson(evaluatedValue, request.getNewRecastFlow());

    responseObserver.onNext(ExpressionEvaluateBlobResponse.newBuilder().setValue(value).build());
    responseObserver.onCompleted();
  }

  /*
    This grpc method can be used to resolve expression by passing your context map and it does not have any dependency
    on pipeline service nuances.
   */
  @Override
  public void resolveExpressionWithContext(ExpressionResolveWithContextRequest request,
      StreamObserver<ExpressionResolveWithContextResponse> responseObserver) {
    // Validate and get context map
    Map<String, Object> contextMap = validateAndGetContextMap(request.getContextMap());
    String resolvedBody;
    try {
      if (request.hasAmbiance()) {
        resolvedBody = (String) pmsEngineExpressionService.resolve(request.getAmbiance(), request.getExpression(),
            ExpressionModeMapper.fromExpressionModeProto(request.getExpressionMode()), contextMap);
      } else {
        SimpleContextMapExpressionEvaluator simpleContextMapExpressionEvaluator =
            new SimpleContextMapExpressionEvaluator(contextMap);
        resolvedBody = (String) simpleContextMapExpressionEvaluator.resolve(
            request.getExpression(), ExpressionModeMapper.fromExpressionModeProto(request.getExpressionMode()));
      }
    } catch (UnresolvedExpressionsException e) {
      log.error("Some expressions couldn't be resolved", e);
      throw e;
    }
    responseObserver.onNext(ExpressionResolveWithContextResponse.newBuilder().setValue(resolvedBody).build());
    responseObserver.onCompleted();
  }

  @Override
  public void renderExpressionByNodeExecutionId(
      ExpressionRenderByNodeExecutionIdRequest request, StreamObserver<ExpressionRenderBlobResponse> responseObserver) {
    String nodeExecutionId = request.getNodeExecutionId();
    if (nodeExecutionId == null || nodeExecutionId.isEmpty()) {
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription("nodeExecutionId is required").asRuntimeException());
      return;
    }

    NodeExecution nodeExecution;
    try {
      nodeExecution =
          nodeExecutionService.getWithFieldsIncludedFromSecondary(nodeExecutionId, Set.of(NodeExecutionKeys.ambiance));
    } catch (Exception e) {
      log.error("Failed to fetch NodeExecution for nodeExecutionId: {}", nodeExecutionId, e);
      responseObserver.onError(
          Status.NOT_FOUND.withDescription("NodeExecution not found for id: " + nodeExecutionId).asRuntimeException());
      return;
    }

    if (nodeExecution == null || nodeExecution.getAmbiance() == null) {
      responseObserver.onError(
          Status.NOT_FOUND.withDescription("NodeExecution or Ambiance not found for id: " + nodeExecutionId)
              .asRuntimeException());
      return;
    }

    final String value;
    if (request.getExpressionMode() != ExpressionMode.UNKNOWN_MODE
        && request.getExpressionMode() != ExpressionMode.UNRECOGNIZED) {
      value = pmsEngineExpressionService.renderExpression(nodeExecution.getAmbiance(), request.getExpression(),
          ExpressionModeMapper.fromExpressionModeProto(request.getExpressionMode()));
    } else {
      value = pmsEngineExpressionService.renderExpression(nodeExecution.getAmbiance(), request.getExpression(), false);
    }

    responseObserver.onNext(ExpressionRenderBlobResponse.newBuilder().setValue(value).build());
    responseObserver.onCompleted();
  }

  @VisibleForTesting
  Map<String, Object> validateAndGetContextMap(Map<String, String> requestMap) {
    byte[] requestMapBytes = JsonUtils.asJson(requestMap).getBytes(StandardCharsets.UTF_8);
    if (requestMapBytes.length > MAX_CONTEXT_MAP_SIZE) {
      log.error("Context map byte size exceeds maximum allowed limit: {} bytes", requestMapBytes.length);
      throw new InvalidRequestException("Context map byte size exceeds maximum allowed limit of 1MB");
    }
    try {
      // For PIPELINE context source type (no functor keyword), support both JSON and simple strings
      final Map<String, Object> returnMap = new HashMap<>();
      requestMap.forEach((key, value) -> {
        try {
          // Try to parse as JSON first
          Map<String, Object> mapObject = JsonUtils.asMap(value);
          returnMap.put(key, mapObject);
        } catch (Exception jsonException) {
          // If JSON parsing fails, treat as a simple string value
          log.debug("Value for key '{}' is not JSON, treating as string: {}", key, value);
          returnMap.put(key, value);
        }
      });
      return returnMap;
    } catch (Exception e) {
      log.error("Exception occurred while processing context map", e);
      throw new InvalidRequestException("Exception occurred while processing context map: " + e.getMessage());
    }
  }
}
