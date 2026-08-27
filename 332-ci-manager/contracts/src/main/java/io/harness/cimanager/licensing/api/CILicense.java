/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.licensing.api;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@OwnedBy(CI)
@Data
@SuperBuilder
public class CILicense {
  private String account_name;
  private String account_id;
  private CILicenseType license_type;
  private long users_provisioned;
  private long developers_purchased;
  private long active_developers;
  private long credit_purchased;
  private List<String> developers;
  private CreditConsumed credit_consumed;
}
