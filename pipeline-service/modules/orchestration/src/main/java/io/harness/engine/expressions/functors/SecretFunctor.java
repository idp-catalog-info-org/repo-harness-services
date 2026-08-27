/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.DynamicSecretReferenceHelper;
import io.harness.engine.observers.SecretObserverInfo;
import io.harness.engine.observers.SecretResolutionObserver;
import io.harness.exception.EngineFunctorException;
import io.harness.expression.celcustomfunctor.WithGetValue;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.observer.Subject;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.security.PmsSecurityContextNoSideEffectsGuard;
import io.harness.yaml.utils.FunctorUtils;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Value
@Slf4j
@OwnedBy(CDC)
public class SecretFunctor implements ExpressionFunctor, WithGetValue {
  Ambiance ambiance;
  Subject<SecretResolutionObserver> secretsRuntimeUsagesSubject;
  PipelineRbacHelper pipelineRbacHelper;
  DynamicSecretReferenceHelper dynamicSecretReferenceHelper;

  public SecretFunctor(Ambiance ambiance, Subject<SecretResolutionObserver> secretsRuntimeUsagesSubject,
      PipelineRbacHelper pipelineRbacHelper, DynamicSecretReferenceHelper dynamicSecretReferenceHelper) {
    this.ambiance = ambiance;
    this.pipelineRbacHelper = pipelineRbacHelper;
    this.secretsRuntimeUsagesSubject = secretsRuntimeUsagesSubject;
    this.dynamicSecretReferenceHelper = dynamicSecretReferenceHelper;
  }

  @Override
  public Object getValue(String secretIdentifier) {
    if (EmptyPredicate.isNotEmpty(secretIdentifier) && ambiance != null
        && AmbianceUtils.shouldEnableSecretsObserver(ambiance)) {
      secretsRuntimeUsagesSubject.fireInform(SecretResolutionObserver::onSecretsRuntimeUsage,
          SecretObserverInfo.builder().secretIdentifier(secretIdentifier).ambiance(ambiance).build());
    }
    boolean shouldRethrowException = shouldRethrowException(secretIdentifier);

    try (PmsSecurityContextNoSideEffectsGuard securityContextEventGuard =
             new PmsSecurityContextNoSideEffectsGuard(ambiance)) {
      SecretFunctorHelper.checkForAccess(secretIdentifier, ambiance, pipelineRbacHelper, dynamicSecretReferenceHelper);
    } catch (NGAccessDeniedException exception) {
      log.warn("Encountered NGAccessDenied error while resolving secret using SecretFunctor ", exception);
      if (shouldRethrowException) {
        throw new EngineFunctorException(exception);
      }
    } catch (Exception ex) {
      log.error("Encountered unknown error while resolving secret using SecretFunctor ", ex);
      if (shouldRethrowException) {
        throw new EngineFunctorException("Encountered unknown error while resolving secret", ex);
      }
    }
    return FunctorUtils.getSecretExpression(ambiance.getExpressionFunctorToken(), secretIdentifier,
        AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.CDS_USE_SINGLE_QUOTES_IN_SECRET_FUNCTOR.name()));
  }

  private boolean shouldRethrowException(String secretIdentifier) {
    try {
      return AmbianceUtils.checkIfFeatureFlagEnabled(
                 ambiance, FeatureName.PIPE_DO_RBAC_CHECK_ON_SECRETS_FOR_PATH_REFERENCE.name())
          && dynamicSecretReferenceHelper.isSecretIdentifierAPathReference(secretIdentifier);
    } catch (Exception ex) {
      return false;
    }
  }
}
