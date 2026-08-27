/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.serializer.spring.converters.dependencyGraph;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.plan.DependencyGraphProto;
import io.harness.serializer.spring.ProtoReadConverter;

import com.google.protobuf.InvalidProtocolBufferException;
import org.bson.types.Binary;

@OwnedBy(HarnessTeam.PIPELINE)
public class DependencyGraphProtoReadConverter extends ProtoReadConverter<DependencyGraphProto> {
  public DependencyGraphProtoReadConverter() {
    super(DependencyGraphProto.class);
  }
  @Override
  public DependencyGraphProto getDeserializedValue(Binary bytes) throws InvalidProtocolBufferException {
    return DependencyGraphProto.parseFrom(bytes.getData());
  }
}
