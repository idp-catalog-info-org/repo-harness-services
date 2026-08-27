/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.spring.converters.failureinfo;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.serializer.spring.ProtoWriteConverter;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.convert.WritingConverter;

@OwnedBy(CDC)
@Singleton
@WritingConverter
public class FailureInfoWriteConverter extends ProtoWriteConverter<FailureInfo> {
  @Override
  public Map<String, Object> getFieldMetadata(FailureInfo entity) {
    Map<String, Object> m = new HashMap<>();
    m.put("errorMessage", entity.getErrorMessage());
    if (entity.getFailureDataCount() > 0) {
      List<Map<String, String>> failureDataMapList = new ArrayList<>();
      for (FailureData failureData : entity.getFailureDataList()) {
        failureDataMapList.add(Map.of("message", failureData.getMessage()));
      }
      m.put("failureData", failureDataMapList);
    }
    return m;
  }
}
