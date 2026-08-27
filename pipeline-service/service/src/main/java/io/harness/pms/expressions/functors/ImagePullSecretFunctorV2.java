/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import io.harness.cd.beans.outcomes.ArtifactsOutcome;
import io.harness.cd.beans.outcomes.ArtifactsOutcomeSweepingOutput;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.pms.data.RawOptionalSweepingOutput;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.expression.LateBindingMap;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
public class ImagePullSecretFunctorV2 extends LateBindingMap implements ExpressionFunctor {
  private transient Ambiance ambiance;
  private transient PmsSweepingOutputService pmsSweepingOutputService;

  private static final String PRIMARY_ARTIFACT = "primary";
  private static final String SIDECAR_ARTIFACTS = "sidecars";

  @Override
  public synchronized Object get(Object key) {
    return get((String) key);
  }

  public Object get(String key) {
    try {
      if (!(PRIMARY_ARTIFACT.equals(key) || SIDECAR_ARTIFACTS.equals(key))) {
        return null;
      }

      ArtifactsOutcome artifactsOutcome = fetchArtifactOutcome(ambiance);

      if (EmptyPredicate.isEmpty(artifactsOutcome)) {
        return null;
      }

      if (key.equals(PRIMARY_ARTIFACT)) {
        return getImagePullSecretFromOutcome((Map<String, Object>) artifactsOutcome.get(PRIMARY_ARTIFACT));
      } else {
        Map<String, Object> sidecarsImagePullSecrets = new HashMap<>();
        Object sidecarData = artifactsOutcome.get(SIDECAR_ARTIFACTS);
        if (sidecarData instanceof Map) {
          @SuppressWarnings("unchecked") Map<String, Object> sideCarOutcomeData = (Map<String, Object>) sidecarData;
          sideCarOutcomeData.forEach((k, v) -> {
            if (v instanceof Map) {
              sidecarsImagePullSecrets.put(k, getImagePullSecretFromOutcome((Map<String, Object>) v));
            }
          });
        }
        return sidecarsImagePullSecrets;
      }

    } catch (Exception ex) {
      log.error(String.format("Exception while invoking image pull secret functor with key %s", key), ex);
    }
    return null;
  }

  private String getImagePullSecretFromOutcome(Map<String, Object> artifactOutcome) {
    if (EmptyPredicate.isNotEmpty(artifactOutcome) && artifactOutcome.containsKey("imagePullSecretExp")) {
      return (String) artifactOutcome.get("imagePullSecretExp");
    }
    return "";
  }

  public ArtifactsOutcome fetchArtifactOutcome(Ambiance ambiance) {
    RawOptionalSweepingOutput optionalSweepingOutput = pmsSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject("artifactsOutcomeSweepingOutput"));

    RecastOrchestrationUtils.fromJson(optionalSweepingOutput.getOutput(), ArtifactsOutcomeSweepingOutput.class);
    if (optionalSweepingOutput.isFound()) {
      ArtifactsOutcomeSweepingOutput artifactsOutcomeSweepingOutput =
          RecastOrchestrationUtils.fromJson(optionalSweepingOutput.getOutput(), ArtifactsOutcomeSweepingOutput.class);
      return artifactsOutcomeSweepingOutput.getArtifactsOutcome();
    }
    return null;
  }
}
