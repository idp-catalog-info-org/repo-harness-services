/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;
import io.harness.security.ServiceTokenGenerator;
import io.harness.security.dto.Principal;

import com.google.inject.ConfigurationException;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class IdpFunctorTest extends CategoryTest {
  private static final String TEST_ACCOUNT_ID = "test-account-id";
  private static final String TEST_BASE_URL = "https://app.harness.io";
  private static final String TEST_SERVICE_SECRET = "test-secret";
  private static final String TEST_TOKEN = "generated-token";
  private static final String TEST_USER_EMAIL = "test@harness.io";
  private static final String TEST_USER_UUID = "user-uuid-123";
  private static final String TEST_USERNAME = "testuser";

  private Ambiance ambianceV1Manual;
  private Ambiance ambianceV1NonManual;
  private Ambiance ambianceV0;

  private IdpFunctor idpFunctor;
  private ServiceTokenGenerator tokenGenerator;
  private Injector mockInjector;

  @Before
  public void setUp() {
    TriggeredBy manualTrigger = TriggeredBy.newBuilder()
                                    .setUuid(TEST_USER_UUID)
                                    .setIdentifier(TEST_USERNAME)
                                    .putExtraInfo("email", TEST_USER_EMAIL)
                                    .build();

    ambianceV1Manual =
        Ambiance.newBuilder()
            .putSetupAbstractions("accountId", TEST_ACCOUNT_ID)
            .setMetadata(ExecutionMetadata.newBuilder()
                             .setHarnessVersion(HarnessYamlVersion.V1)
                             .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                 .setTriggerType(TriggerType.MANUAL)
                                                 .setTriggeredBy(manualTrigger)
                                                 .build())
                             .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder().setShouldValidateRbac(false).build())
                             .build())
            .build();

    ambianceV1NonManual =
        Ambiance.newBuilder()
            .putSetupAbstractions("accountId", TEST_ACCOUNT_ID)
            .setMetadata(ExecutionMetadata.newBuilder()
                             .setHarnessVersion(HarnessYamlVersion.V1)
                             .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                 .setTriggerType(TriggerType.WEBHOOK)
                                                 .setTriggeredBy(manualTrigger)
                                                 .build())
                             .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder().setShouldValidateRbac(false).build())
                             .build())
            .build();

    ambianceV0 =
        Ambiance.newBuilder()
            .putSetupAbstractions("accountId", TEST_ACCOUNT_ID)
            .setMetadata(ExecutionMetadata.newBuilder()
                             .setHarnessVersion(HarnessYamlVersion.V0)
                             .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                                 .setTriggerType(TriggerType.MANUAL)
                                                 .setTriggeredBy(manualTrigger)
                                                 .build())
                             .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder().setShouldValidateRbac(false).build())
                             .build())
            .build();

    tokenGenerator = mock(ServiceTokenGenerator.class);
    when(tokenGenerator.getServiceTokenWithDuration(anyString(), any(Duration.class), any(Principal.class)))
        .thenReturn(TEST_TOKEN);

    mockInjector = mock(Injector.class);
    when(mockInjector.getInstance(Key.get(String.class, Names.named("idpBaseUrl")))).thenReturn(TEST_BASE_URL);
    when(mockInjector.getInstance(Key.get(String.class, Names.named("idpServiceSecret"))))
        .thenReturn(TEST_SERVICE_SECRET);

    idpFunctor = IdpFunctor.builder().ambiance(ambianceV1Manual).build();
    on(idpFunctor).set("tokenGenerator", tokenGenerator);
    on(idpFunctor).set("injector", mockInjector);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSupportsKeyWithIdpKey() {
    assertThat(idpFunctor.supportsKey("idp")).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSupportsKeyWithOtherKeys() {
    assertThat(idpFunctor.supportsKey("qwiet")).isFalse();
    assertThat(idpFunctor.supportsKey("IDP")).isFalse();
    assertThat(idpFunctor.supportsKey("")).isFalse();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetBaseUrl() {
    Object result = idpFunctor.get("baseUrl");
    assertThat(result).isEqualTo(TEST_BASE_URL);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetIsManualTriggerTrue() {
    Object result = idpFunctor.get("isManualTrigger");
    assertThat(result).isEqualTo("true");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetIsManualTriggerFalse() {
    IdpFunctor nonManualFunctor = IdpFunctor.builder().ambiance(ambianceV1NonManual).build();
    on(nonManualFunctor).set("tokenGenerator", tokenGenerator);
    on(nonManualFunctor).set("injector", mockInjector);

    Object result = nonManualFunctor.get("isManualTrigger");
    assertThat(result).isEqualTo("false");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetAuthorizationManualTrigger() {
    Object result = idpFunctor.get("authorization");
    assertThat(result).isNotNull();
    assertThat((String) result).contains("IDPService");
    assertThat((String) result).contains(TEST_TOKEN);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetAuthorizationNonManualTrigger() {
    IdpFunctor nonManualFunctor = IdpFunctor.builder().ambiance(ambianceV1NonManual).build();
    on(nonManualFunctor).set("tokenGenerator", tokenGenerator);
    on(nonManualFunctor).set("injector", mockInjector);

    Object result = nonManualFunctor.get("authorization");
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetSourcePrincipalManualTrigger() {
    Object result = idpFunctor.get("sourcePrincipal");
    assertThat(result).isNotNull();
    assertThat((String) result).contains("IDPService");
    assertThat((String) result).contains(TEST_TOKEN);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetSourcePrincipalNonManualTrigger() {
    IdpFunctor nonManualFunctor = IdpFunctor.builder().ambiance(ambianceV1NonManual).build();
    on(nonManualFunctor).set("tokenGenerator", tokenGenerator);
    on(nonManualFunctor).set("injector", mockInjector);

    Object result = nonManualFunctor.get("sourcePrincipal");
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetErrorNullWhenServicesInjected() {
    Object result = idpFunctor.get("error");
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetErrorWhenTokenGeneratorNotInjected() {
    IdpFunctor functorWithoutGenerator = IdpFunctor.builder().ambiance(ambianceV1Manual).build();
    on(functorWithoutGenerator).set("injector", mockInjector);

    Object result = functorWithoutGenerator.get("error");
    assertThat(result).isInstanceOf(String.class);
    assertThat((String) result).contains("ServiceTokenGenerator not injected");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetErrorWhenSecretNotInjected() {
    Injector noSecretInjector = mock(Injector.class);
    when(noSecretInjector.getInstance(Key.get(String.class, Names.named("idpBaseUrl")))).thenReturn(TEST_BASE_URL);
    when(noSecretInjector.getInstance(Key.get(String.class, Names.named("idpServiceSecret"))))
        .thenThrow(new ConfigurationException(Collections.emptyList()));

    IdpFunctor functorWithoutSecret = IdpFunctor.builder().ambiance(ambianceV1Manual).build();
    on(functorWithoutSecret).set("tokenGenerator", tokenGenerator);
    on(functorWithoutSecret).set("injector", noSecretInjector);

    Object result = functorWithoutSecret.get("error");
    assertThat(result).isInstanceOf(String.class);
    assertThat((String) result).contains("idpServiceSecret not injected");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetAuthorizationGracefulWhenNotInjected() {
    IdpFunctor functorWithout = IdpFunctor.builder().ambiance(ambianceV1Manual).build();

    Object result = functorWithout.get("authorization");
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetUnknownKeyReturnsNull() {
    assertThat(idpFunctor.get("unknownKey")).isNull();
    assertThat(idpFunctor.get("")).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetNonStringKeyReturnsNull() {
    Object result = idpFunctor.get(123);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testContainsKeyV1AlwaysReturnsTrue() {
    IdpFunctor functorV1 = IdpFunctor.builder().ambiance(ambianceV1Manual).build();

    assertThat(functorV1.containsKey("baseUrl")).isTrue();
    assertThat(functorV1.containsKey("unknownKey")).isTrue();
    assertThat(functorV1.containsKey("")).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testContainsKeyV0UsesDefaultBehavior() {
    IdpFunctor functorV0 = IdpFunctor.builder().ambiance(ambianceV0).build();

    assertThat(functorV0.containsKey("baseUrl")).isFalse();
    assertThat(functorV0.containsKey("unknownKey")).isFalse();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBaseUrlNullWhenNotInjected() {
    IdpFunctor functorWithout = IdpFunctor.builder().ambiance(ambianceV1Manual).build();

    assertThat(functorWithout.get("baseUrl")).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBuilderCreatesInstance() {
    IdpFunctor functor = IdpFunctor.builder().ambiance(ambianceV1Manual).build();
    assertThat(functor).isNotNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIdpFunctorDispatchThroughRuntimeFunctor() {
    List<RuntimeAbstractFunctor> functors = new java.util.ArrayList<>();
    functors.add(idpFunctor);

    RuntimeFunctor runtimeFunctor =
        RuntimeFunctor.builder().ambiance(ambianceV1Manual).runtimeAbstractFunctors(functors).build();

    Object idpResult = runtimeFunctor.get("idp");
    assertThat(idpResult).isNotNull();
    assertThat(idpResult).isInstanceOf(IdpFunctor.class);

    IdpFunctor returnedFunctor = (IdpFunctor) idpResult;

    Object baseUrl = returnedFunctor.get("baseUrl");
    assertThat(baseUrl).isEqualTo(TEST_BASE_URL);

    Object error = returnedFunctor.get("error");
    assertThat(error).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIdpFunctorContainsKeyV1AllowsCelAccess() {
    assertThat(idpFunctor.containsKey("baseUrl")).isTrue();
    assertThat(idpFunctor.containsKey("authorization")).isTrue();
    assertThat(idpFunctor.containsKey("error")).isTrue();
    assertThat(idpFunctor.containsKey("nonexistent")).isTrue();
  }
}
