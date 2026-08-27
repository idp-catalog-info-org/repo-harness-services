/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.spring.converters.executionContext;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.serializer.spring.ProtoWriteConverter;

import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.convert.WritingConverter;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@Singleton
@WritingConverter
public class ExecutionContextWriteConverter extends ProtoWriteConverter<ExecutionContext> {
  @Override
  public Map<String, Object> getFieldMetadata(ExecutionContext entity) {
    Map<String, Object> m = new HashMap<>();
    m.put("planExecutionId", entity.getPlanExecutionId());
    m.put("planId", entity.getPlanId());
    m.put("stageExecutionId", entity.getStageExecutionId());
    if (EmptyPredicate.isNotEmpty(entity.getSetupAbstractionsMap())) {
      m.put("setupAbstractions", Map.of("accountId", entity.getSetupAbstractionsMap().get("accountId")));
    }
    return m;
  }
}
