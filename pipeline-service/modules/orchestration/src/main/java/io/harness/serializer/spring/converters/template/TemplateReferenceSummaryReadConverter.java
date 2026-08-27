/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.spring.converters.template;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.template.TemplateReferenceSummary;
import io.harness.serializer.spring.ProtoReadConverter;

import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import org.bson.types.Binary;
import org.springframework.data.convert.ReadingConverter;

@OwnedBy(PIPELINE)
@Singleton
@ReadingConverter
public class TemplateReferenceSummaryReadConverter extends ProtoReadConverter<TemplateReferenceSummary> {
  public TemplateReferenceSummaryReadConverter() {
    super(TemplateReferenceSummary.class);
  }

  @Override
  public TemplateReferenceSummary getDeserializedValue(Binary bytes) throws InvalidProtocolBufferException {
    return TemplateReferenceSummary.parseFrom(bytes.getData());
  }
}
