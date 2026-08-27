/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.NGCommonEntityConstants.FUNCTOR_BASE64_METHOD_NAME;
import static io.harness.NGCommonEntityConstants.FUNCTOR_STRING_METHOD_NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.blockExecutionMetadata.BlockExecutionMetadataService;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.LateBindingMap;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.expression.ExpressionRequest;
import io.harness.pms.contracts.expression.ExpressionResponse;
import io.harness.pms.contracts.expression.RemoteFunctorServiceGrpc.RemoteFunctorServiceBlockingStub;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.expression.ExpressionResultUtils;
import io.harness.pms.utils.PmsGrpcClientUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class RemoteExpressionFunctor extends LateBindingMap implements ExpressionFunctor {
  private RemoteFunctorServiceBlockingStub remoteFunctorServiceBlockingStub;
  private BlockExecutionMetadataService blockExecutionMetadataService;
  private String functorKey;
  public String value;
  Ambiance ambiance;

  @Override
  public Object get(Object args) {
    if (blockExecutionMetadataService.validate(ambiance)) {
      return null;
    }
    try {
      List<String> allArgs;
      // checking if args is string or array of string
      if (args instanceof String) {
        allArgs = Collections.singletonList((String) args);
      } else {
        allArgs = Arrays.asList((String[]) args);
      }
      long startEvaluateExpressionTimeInMs = System.currentTimeMillis();
      ExpressionResponse expressionResponse = PmsGrpcClientUtils.retryAndProcessException(
          remoteFunctorServiceBlockingStub::evaluate,
          ExpressionRequest.newBuilder().setAmbiance(ambiance).setFunctorKey(functorKey).addAllArgs(allArgs).build());
      if (expressionResponse.getIsPrimitive()) {
        return ExpressionResultUtils.getPrimitiveResponse(
            expressionResponse.getValue(), expressionResponse.getPrimitiveType());
      }
      long evaluatedExpressionTimeInMs = System.currentTimeMillis() - startEvaluateExpressionTimeInMs;
      log.info("Expression evaluated successfully, functorKey: {}, time taken in ms: {}", functorKey,
          evaluatedExpressionTimeInMs);
      return RecastOrchestrationUtils.fromJson(expressionResponse.getValue());
    } catch (ClassNotFoundException e) {
      log.error(e.getMessage());
      throw new InvalidRequestException(e.getMessage(), e);
    } catch (GeneralException ex) {
      if (ex.getMessage().contains("Call giving rise to a loop")) {
        log.error("[BLOCKING_EXECUTION]: This execution is blocked, we need to unblock it post correction");
        blockExecutionMetadataService.block(AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance),
            ambiance.getMetadata().getPipelineIdentifier());
      }
      throw ex;
    } catch (Exception ex) {
      log.error(String.format("Could not get object from remote functor for key: %s", functorKey), ex);
      throw ex;
    }
  }

  public Object getValue(String... args) {
    return get(args);
  }

  public Object getAsString(String ref) {
    String[] args = {FUNCTOR_STRING_METHOD_NAME, ref};
    return get(args);
  }

  public Object getAsBase64(String ref) {
    String[] args = {FUNCTOR_BASE64_METHOD_NAME, ref};
    return get(args);
  }

  // This is required for CEL because CEL first calls the containsKey method and only if is true does it call get method
  // where we have our logic. That's why we are returning true here so that it can go to the get method.
  @Override
  public boolean containsKey(Object key) {
    if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
      return true;
    } else {
      return super.containsKey(key);
    }
  }
}
