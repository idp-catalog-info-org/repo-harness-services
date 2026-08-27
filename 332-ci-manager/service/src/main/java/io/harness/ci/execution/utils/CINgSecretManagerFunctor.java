/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.encryption.SecretRefData;
import io.harness.exception.FunctorException;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.ng.core.NGAccess;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.utils.FunctorUtils;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CINgSecretManagerFunctor implements ExpressionFunctor {
  private long expressionFunctorToken;
  private SecretUtils secretUtils;
  private NGAccess ngAccess;
  private boolean withSingleQuotes;

  @Builder.Default private List<SecretVariableDetails> secretVariableDetails = new ArrayList<>();
  @Builder.Default private List<SecretNGVariable> secretNGVariableDetails = new ArrayList<>();

  public List<SecretVariableDetails> getSecretVariableDetails() {
    return secretVariableDetails;
  }

  public List<SecretNGVariable> getSecretNGVariableDetails() {
    return secretNGVariableDetails;
  }

  public Object obtain(String secretIdentifier, int token) {
    try {
      IdentifierRef secretIdentifierRef = IdentifierRefHelper.getSecretIdentifierRef(secretIdentifier,
          ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
      SecretRefData secretRefData = SecretRefData.builder()
                                        .identifier(secretIdentifierRef.getIdentifier())
                                        .scope(secretIdentifierRef.getScope())
                                        .build();
      SecretNGVariable secretNGVariable = SecretNGVariable.builder()
                                              .name(secretIdentifierRef.getIdentifier())
                                              .value(ParameterField.createValueField(secretRefData))
                                              .build();
      secretNGVariableDetails.add(secretNGVariable);
      return FunctorUtils.getSecretExpression(expressionFunctorToken, secretIdentifier, withSingleQuotes);
    } catch (Exception ex) {
      throw new FunctorException("Error occurred while evaluating the secret [" + secretIdentifier + "]", ex);
    }
  }
}
