/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static java.lang.String.format;

import io.harness.cdng.instance.outcome.InstanceOutcome;
import io.harness.cdng.instance.outcome.InstancesOutcome;
import io.harness.engine.pms.data.RawOptionalSweepingOutput;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.LateBindingMap;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.contracts.refobjects.RefType;
import io.harness.pms.data.OrchestrationRefType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
public class InstanceFunctorV2 extends LateBindingMap implements ExpressionFunctor {
  public static final String INSTANCE = "instance";
  private static final String INSTANCES = "instances";

  private static final String INSTANCE_NAME_PROPERTY = "name";
  private static final String INSTANCE_HOST_NAME_PROPERTY = "hostName";
  private static final String INSTANCE_HOST_PROPERTY = "host";
  public static final String INSTANCE_PROPERTIES_PROPERTY = "properties";
  private transient Ambiance ambiance;
  private transient PmsSweepingOutputService pmsSweepingOutputService;

  @Override
  public Object get(Object key) {
    return get((String) key);
  }

  public Object get(String key) {
    log.info("Resolving instance properties with key: {}", key);
    var refObject = RefObject.newBuilder()
                        .setName(INSTANCES)
                        .setKey(INSTANCES)
                        .setRefType(RefType.newBuilder().setType(OrchestrationRefType.SWEEPING_OUTPUT).build())
                        .build();
    RawOptionalSweepingOutput resolve = pmsSweepingOutputService.resolveOptional(ambiance, refObject);
    if (!resolve.isFound()) {
      throw new InvalidRequestException("Unable to read instances output");
    }
    var sweepingOutput = RecastOrchestrationUtils.fromJson(resolve.getOutput(), ExecutionSweepingOutput.class);
    var instances = ((InstancesOutcome) sweepingOutput).getInstances();
    var hostName = getRepeatStrategyItem(ambiance);
    var instance = findInstanceByHostNameOrThrow(instances, hostName);
    return switch (key) {
      case INSTANCE_NAME_PROPERTY:
        yield instance.getName();
      case INSTANCE_HOST_NAME_PROPERTY:
        yield instance.getHostName();
      case INSTANCE_HOST_PROPERTY:
        yield instance.getHost();
      case INSTANCE_PROPERTIES_PROPERTY:
        yield instance.getHost() != null ? instance.getHost().getProperties() : null;
      default:
        throw new InvalidArgumentsException(format("Unsupported instance property, property: %s", key));
    };
  }

  private InstanceOutcome findInstanceByHostNameOrThrow(
      List<InstanceOutcome> instances, @NotNull final String hostName) {
    return instances.stream()
        .filter(instanceOutcome -> hostName.equals(instanceOutcome.getHostName()))
        .findFirst()
        .orElseThrow(() -> new InvalidRequestException(format("Not found instance by host name, %s", hostName)));
  }

  private String getRepeatStrategyItem(Ambiance ambiance) {
    List<Level> stepLevelsWithStrategyMetadata =
        ambiance.getLevelsList()
            .stream()
            .filter(level -> AmbianceUtils.hasStrategyMetadata(level) && level.hasStepType())
            .collect(Collectors.toList());

    Map<String, Object> strategyObjectMap = StrategyUtils.fetchStrategyObjectMap(stepLevelsWithStrategyMetadata);
    if (strategyObjectMap == null) {
      throw new InvalidRequestException("Not found step level strategy");
    }

    Object repeatStrategy = strategyObjectMap.get("repeat");
    if (!(repeatStrategy instanceof HashMap)) {
      throw new InvalidRequestException("Not found step level repeat strategy");
    }

    Object repeatStrategyItem = ((HashMap<String, Object>) repeatStrategy).get("item");
    if (!(repeatStrategyItem instanceof String)) {
      throw new InvalidRequestException("Not found step level repeat strategy item");
    }

    return (String) repeatStrategyItem;
  }
}
