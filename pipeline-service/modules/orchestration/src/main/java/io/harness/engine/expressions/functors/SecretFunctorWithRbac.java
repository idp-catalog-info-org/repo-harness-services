/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.HarnessTeam;
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

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class SecretFunctorWithRbac implements ExpressionFunctor, WithGetValue {
  PipelineRbacHelper pipelineRbacHelper;
  Ambiance ambiance;
  Subject<SecretResolutionObserver> secretsRuntimeUsagesSubject;
  DynamicSecretReferenceHelper dynamicSecretReferenceHelper;

  public SecretFunctorWithRbac(Ambiance ambiance, PipelineRbacHelper pipelineRbacHelper,
      Subject<SecretResolutionObserver> secretsRuntimeUsagesSubject,
      DynamicSecretReferenceHelper dynamicSecretReferenceHelper) {
    this.ambiance = ambiance;
    this.pipelineRbacHelper = pipelineRbacHelper;
    this.secretsRuntimeUsagesSubject = secretsRuntimeUsagesSubject;
    this.dynamicSecretReferenceHelper = dynamicSecretReferenceHelper;
  }

  @SneakyThrows
  @Override
  public Object getValue(String secretIdentifier) {
    validateSecret(secretIdentifier);
    try (PmsSecurityContextNoSideEffectsGuard securityContextEventGuard =
             new PmsSecurityContextNoSideEffectsGuard(ambiance)) {
      SecretFunctorHelper.checkForAccess(secretIdentifier, ambiance, pipelineRbacHelper, dynamicSecretReferenceHelper);
      if (EmptyPredicate.isNotEmpty(secretIdentifier) && ambiance != null
          && AmbianceUtils.shouldEnableSecretsObserver(ambiance)) {
        secretsRuntimeUsagesSubject.fireInform(SecretResolutionObserver::onSecretsRuntimeUsage,
            SecretObserverInfo.builder().secretIdentifier(secretIdentifier).ambiance(ambiance).build());
      }

      return FunctorUtils.getSecretExpression(ambiance.getExpressionFunctorToken(), secretIdentifier,
          AmbianceUtils.checkIfFeatureFlagEnabled(
              ambiance, FeatureName.CDS_USE_SINGLE_QUOTES_IN_SECRET_FUNCTOR.name()));
    } catch (NGAccessDeniedException exception) {
      log.error("Encountered error while resolving secret ", exception);
      throw new EngineFunctorException(exception);
    } catch (Exception ex) {
      log.error("Encountered unknown error while resolving secret ", ex);
      throw new EngineFunctorException("Encountered unknown error while resolving secret", ex);
    }
  }

  public void validateSecret(String secretIdentifier) {
    if (isEmpty(secretIdentifier)) {
      throw new EngineFunctorException("Empty secret identifier values are not supported");
    }
  }
}
