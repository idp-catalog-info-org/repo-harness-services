/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.metrics;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import java.util.HashMap;
import java.util.Map;

public class ExpressionResolutionMetricContext extends AutoMetricContext {
  public static final String LABEL_ACCOUNT_ID = "accountId";
  public static final String LABEL_STATUS = "status";

  public ExpressionResolutionMetricContext(Map<String, String> fields) {
    if (fields != null) {
      fields.forEach((fieldKey, fieldValue) -> {
        if (!isEmpty(fieldKey) && !isEmpty(fieldValue)) {
          put(fieldKey, fieldValue);
        }
      });
    }
  }

  public static ExpressionResolutionMetricContext build(String accountId, String status) {
    Map<String, String> fields = new HashMap<>();
    fields.put(LABEL_ACCOUNT_ID, normalize(accountId));
    fields.put(LABEL_STATUS, normalize(status));
    return new ExpressionResolutionMetricContext(fields);
  }

  private static String normalize(String value) {
    return isEmpty(value) ? "na" : value;
  }
}
