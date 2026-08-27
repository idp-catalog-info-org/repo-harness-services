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
import io.harness.expression.ConnectorInputsMapper;
import io.harness.notification.remote.SmtpConfigClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.runner.request.helpers.infra.InfraBasedHelper;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.usergroups.UserGroupClient;
import io.harness.utils.CDStepsExpressionResolver;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
public class RuntimeFunctorFactory {
  @Inject private Injector injector;
  @Inject InfraBasedHelper infraBasedHelper;
  @Inject private CDStepsExpressionResolver cdStepsExpressionResolver;
  @Inject private SecretNGManagerClient secretNGManagerClient;
  @Inject private SmtpConfigClient smtpConfigClient;
  @Inject private UserGroupClient userGroupClient;
  @Inject private ConnectorInputsMapper connectorInputsMapper;

  private final List<RuntimeAbstractFunctor> functors = new ArrayList<>();

  public RuntimeFunctor getRuntimeFunctor(Ambiance ambiance) {
    initFunctors(ambiance);
    return new RuntimeFunctor(ambiance, functors, infraBasedHelper, cdStepsExpressionResolver, connectorInputsMapper);
  }

  private void initFunctors(Ambiance ambiance) {
    functors.add(new FFFunctor(ambiance));
    functors.add(new SettingsFunctor(ambiance));
    functors.add(new RuntimeInputsFunctor(ambiance));
    functors.add(new HarnessCodeFunctor(ambiance));
    functors.add(new SSHSecretFunctor(secretNGManagerClient, ambiance));
    functors.add(new WinrmSecretFunctor(secretNGManagerClient, ambiance));
    functors.add(new SMTPFunctor(smtpConfigClient, ambiance));
    functors.add(new UserGroupsFunctor(userGroupClient, ambiance));
    functors.add(new HarnessRegistryFunctor(ambiance));
    functors.add(new QwietFunctor(ambiance));
    functors.add(new IdpFunctor(ambiance));
    injectMembersInFunctors();
  }

  private void injectMembersInFunctors() {
    functors.stream().peek(f -> injector.injectMembers(f)).collect(Collectors.toList());
  }
}
