/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.queue;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class CIInitPollerUtils extends ExecutionPollerUtils {
  @Inject @Named("ciInitTaskMessageProcessor") private CITaskMessageProcessor ciInitTaskMessageProcessor;

  @Override
  protected CITaskMessageProcessor getCITaskMessageProcessor() {
    return ciInitTaskMessageProcessor;
  }
}
