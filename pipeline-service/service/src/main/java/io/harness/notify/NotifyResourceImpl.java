/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notify;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.dto.ResponseDTO.newResponse;

import io.harness.ng.core.dto.ResponseDTO;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;

public class NotifyResourceImpl implements NotifyResource {
  @Inject private WaitNotifyEngine waitEngine;

  @Override
  public ResponseDTO<Boolean> doneWith(String correlationId) {
    return newResponse(isNotEmpty(waitEngine.doneWith(correlationId, EmptyResponseData.getInstance())));
  }
}
