/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.spring.converters.executionmetadata;

import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.serializer.spring.ProtoReadConverter;

import com.google.protobuf.InvalidProtocolBufferException;
import org.bson.types.Binary;

public class TriggerPayloadReadConverter extends ProtoReadConverter<TriggerPayload> {
  public TriggerPayloadReadConverter() {
    super(TriggerPayload.class);
  }

  @Override
  public TriggerPayload getDeserializedValue(Binary bytes) throws InvalidProtocolBufferException {
    return TriggerPayload.parseFrom(bytes.getData());
  }
}
