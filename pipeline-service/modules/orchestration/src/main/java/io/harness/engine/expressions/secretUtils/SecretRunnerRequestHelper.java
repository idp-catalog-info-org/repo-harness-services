/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.secretUtils;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.expression.common.ExpressionMode;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.utils.CDStepsExpressionResolver;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Singleton
@Slf4j
public class SecretRunnerRequestHelper {
  @Inject private CDStepsExpressionResolver cdStepsExpressionResolver;

  @Inject private SecretNGManagerClient secretNGManagerClient;

  public void addEnvVarsBasedOnSecret(Ambiance ambiance, String secretRef, @NonNull Map<String, String> envVars) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    IdentifierRef secretIdRef =
        IdentifierRefHelper.getIdentifierRef(secretRef, accountIdentifier, orgIdentifier, projectIdentifier);

    // Fetch secret once to determine type
    SecretResponseWrapper secretResponseWrapper = NGRestUtils.getResponse(
        secretNGManagerClient.getSecret(secretIdRef.getIdentifier(), secretIdRef.getAccountIdentifier(),
            secretIdRef.getOrgIdentifier(), secretIdRef.getProjectIdentifier()),
        "No secret configured with identifier: " + secretRef);

    if (secretResponseWrapper == null || secretResponseWrapper.getSecret() == null) {
      return;
    }

    SecretSpecDTO secretSpecDTO = secretResponseWrapper.getSecret().getSpec();

    Map<String, Object> secretFields = null;
    if (secretSpecDTO instanceof SSHKeySpecDTO) {
      SSHSecretAccessor sshAccessor = new SSHSecretAccessor(ambiance, secretNGManagerClient);
      Object result = sshAccessor.get(secretIdRef.getIdentifier());
      if (result instanceof Map) {
        secretFields = (Map<String, Object>) result;
      }
    } else if (secretSpecDTO instanceof WinRmCredentialsSpecDTO) {
      WinrmSecretAccessor winrmAccessor = new WinrmSecretAccessor(ambiance, secretNGManagerClient);
      Object result = winrmAccessor.get(secretIdRef.getIdentifier());
      if (result instanceof Map) {
        secretFields = (Map<String, Object>) result;
      }
    } else {
      // Unsupported/irrelevant secret type for this helper
      log.debug("Unsupported secret type for secret helper: {}", secretSpecDTO.getClass().getSimpleName());
      return;
    }

    if (isNotEmpty(secretFields)) {
      Map<String, String> secretEnvVariables = SecretVariableMapper.toSecretVariables(secretSpecDTO, secretFields);

      cdStepsExpressionResolver.updateExpressions(
          ambiance, secretEnvVariables, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
      envVars.putAll(secretEnvVariables);
    }
  }
}
