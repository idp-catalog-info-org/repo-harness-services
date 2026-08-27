/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notify;

import io.harness.tasks.ResponseData;

import lombok.Data;

@Data
public class EmptyResponseData implements ResponseData {
  private static EmptyResponseData instanceObject;

  private EmptyResponseData() {}

  public static EmptyResponseData getInstance() {
    if (instanceObject == null) {
      instanceObject = new EmptyResponseData();
    }
    return instanceObject;
  }
}
