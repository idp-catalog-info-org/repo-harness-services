/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.expression.LateBindingMap;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import lombok.Builder;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
public class SettingsFunctor extends LateBindingMap implements RuntimeAbstractFunctor {
  @Inject NGSettingsClient settingsClient;
  Ambiance ambiance;
  private static final String settingsKey = "settings";

  @Builder
  public SettingsFunctor(Ambiance ambiance) {
    this.ambiance = ambiance;
  }

  @Override
  public boolean supportsKey(String key) {
    return key.equals(settingsKey);
  }

  @Override
  public synchronized Object get(Object key) {
    if (!(key instanceof String)) {
      return null;
    }
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    if (((String) key).startsWith("account.")) {
      orgId = projectId = null;
      key = ((String) key).substring(8);
    } else if (((String) key).startsWith("org.")) {
      projectId = null;
      key = ((String) key).substring(4);
    }
    String settingValue =
        NGRestUtils.getResponse(settingsClient.getSetting((String) key, accountId, orgId, projectId)).getValue();
    if (settingValue.equals("true")) {
      return true;
    } else if (settingValue.equals("false")) {
      return false;
    }
    return settingValue;
  }

  // This is required for CEL because CEL first calls the containsKey method and only if is true does it call get method
  // where we have our logic. That's why we are returning true here so that it can go to the get method.
  @Override
  public boolean containsKey(Object key) {
    if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
      return true;
    } else {
      return super.containsKey(key);
    }
  }
}
