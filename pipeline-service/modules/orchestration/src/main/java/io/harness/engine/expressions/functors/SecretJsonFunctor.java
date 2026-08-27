/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.functors.ExpressionFunctor;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * This helps in processing a json that is marked as a secret.
 * We want the json secret to be resolved on the delegate, hence we convert it to sweepingOutputSecrets expression
 * instead of using jsonFunctor to resolve here.
 */

@Value
@Slf4j
@OwnedBy(CDC)
public class SecretJsonFunctor implements ExpressionFunctor {
  public String object(String json) {
    return "${sweepingOutputSecrets.object(" + json + ")}";
  }

  public String select(String path, String json) {
    return "${sweepingOutputSecrets.select(\"" + path + "\"," + json + ")}";
  }

  public String list(String path, String json) {
    return "${sweepingOutputSecrets.list(\"" + path + "\"," + json + ")}";
  }

  public String exists(String path, String json) {
    return "${sweepingOutputSecrets.exists(\"" + path + "\"," + json + ")}";
  }

  public String isValid(String json) {
    return "${sweepingOutputSecrets.isValid(" + json + ")}";
  }
}
