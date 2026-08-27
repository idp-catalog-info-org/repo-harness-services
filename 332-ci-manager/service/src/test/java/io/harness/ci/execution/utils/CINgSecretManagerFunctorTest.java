/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.NISHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.encryption.Scope;
import io.harness.exception.FunctorException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.rule.Owner;
import io.harness.yaml.core.variables.SecretNGVariable;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CI)
public class CINgSecretManagerFunctorTest extends CIExecutionTestBase {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final int TOKEN = 12345;
  private static final long EXPRESSION_FUNCTOR_TOKEN = 98765L;

  private NGAccess ngAccess;
  private CINgSecretManagerFunctor functor;

  @Before
  public void setUp() {
    ngAccess = BaseNGAccess.builder()
                   .accountIdentifier(ACCOUNT_ID)
                   .orgIdentifier(ORG_ID)
                   .projectIdentifier(PROJECT_ID)
                   .build();
    functor = CINgSecretManagerFunctor.builder()
                  .expressionFunctorToken(EXPRESSION_FUNCTOR_TOKEN)
                  .ngAccess(ngAccess)
                  .withSingleQuotes(false)
                  .build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testObtain_shouldReturnSecretExpressionWithDoubleQuotes() {
    String secretIdentifier = "mySecret";

    Object result = functor.obtain(secretIdentifier, TOKEN);

    assertThat(result)
        .as("Should return secret expression with double quotes when withSingleQuotes is false")
        .isEqualTo("${ngSecretManager.obtain(\"" + secretIdentifier + "\", " + EXPRESSION_FUNCTOR_TOKEN + ")}");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testObtain_withSingleQuotes_shouldReturnSecretExpressionWithSingleQuotes() {
    CINgSecretManagerFunctor singleQuoteFunctor = CINgSecretManagerFunctor.builder()
                                                      .expressionFunctorToken(EXPRESSION_FUNCTOR_TOKEN)
                                                      .ngAccess(ngAccess)
                                                      .withSingleQuotes(true)
                                                      .build();
    String secretIdentifier = "mySecret";

    Object result = singleQuoteFunctor.obtain(secretIdentifier, TOKEN);

    assertThat(result)
        .as("Should return secret expression with single quotes when withSingleQuotes is true")
        .isEqualTo("${ngSecretManager.obtain('" + secretIdentifier + "', " + EXPRESSION_FUNCTOR_TOKEN + ")}");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testObtain_shouldAddSecretNGVariableToList() {
    String secretIdentifier = "org.myOrgSecret";

    functor.obtain(secretIdentifier, TOKEN);

    List<SecretNGVariable> secretNGVariables = functor.getSecretNGVariableDetails();
    assertThat(secretNGVariables).as("Should have one secret NG variable after calling obtain once").hasSize(1);
    SecretNGVariable variable = secretNGVariables.get(0);
    assertThat(variable.getName())
        .as("Secret variable name should match the identifier portion of the secret ref")
        .isNotNull();
    assertThat(variable.getValue()).as("Secret variable value should not be null").isNotNull();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testObtain_calledMultipleTimes_shouldAccumulateSecretNGVariables() {
    functor.obtain("secret1", TOKEN);
    functor.obtain("secret2", TOKEN);
    functor.obtain("secret3", TOKEN);

    List<SecretNGVariable> secretNGVariables = functor.getSecretNGVariableDetails();
    assertThat(secretNGVariables).as("Should accumulate three secret NG variables after three obtain calls").hasSize(3);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSecretVariableDetails_shouldReturnEmptyListByDefault() {
    List<SecretVariableDetails> details = functor.getSecretVariableDetails();

    assertThat(details).as("Secret variable details should be empty by default").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetSecretNGVariableDetails_shouldReturnEmptyListByDefault() {
    List<SecretNGVariable> details = functor.getSecretNGVariableDetails();

    assertThat(details).as("Secret NG variable details should be empty by default").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testObtain_whenExceptionOccurs_shouldThrowFunctorException() {
    // Empty identifier causes InvalidIdentifierRefException inside getSecretIdentifierRef,
    // which is caught and re-thrown as a FunctorException by obtain().
    String invalidSecretIdentifier = "";

    assertThatThrownBy(() -> functor.obtain(invalidSecretIdentifier, TOKEN))
        .as("Should throw FunctorException when the secret identifier is empty")
        .isInstanceOf(FunctorException.class);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testObtain_withScopedSecret_shouldReturnExpressionAndAddVariable() {
    String secretIdentifier = "account.myAccountSecret";

    Object result = functor.obtain(secretIdentifier, TOKEN);

    assertThat(result).as("Should return a non-null secret expression for scoped secret").isNotNull();
    assertThat(result.toString())
        .as("Should contain the secret identifier in the expression")
        .contains(secretIdentifier);

    List<SecretNGVariable> variables = functor.getSecretNGVariableDetails();
    assertThat(variables).as("Should have added one variable for the scoped secret").hasSize(1);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testObtain_vaultPathWithDotsInVersion_shouldSucceed() {
    // Regression test for CI-23109: vault paths containing version strings like "7.2.0" were
    // incorrectly parsed as scope-prefixed identifiers, causing "Invalid Secret Reference" errors.
    String vaultPath =
        "hashicorpvault://RMSOYCLDNONPROD/kv/global/perf/rms-perf-batch-7.2.0-02/ssh-tunnel#ssh_private_key_pem";

    Object result = functor.obtain(vaultPath, TOKEN);

    assertThat(result).as("Should successfully return expression for vault path with dots").isNotNull();
    assertThat(result.toString()).as("Expression should reference the full vault path").contains(vaultPath);

    List<SecretNGVariable> variables = functor.getSecretNGVariableDetails();
    assertThat(variables).hasSize(1);
    SecretNGVariable variable = variables.get(0);
    assertThat(variable.getName())
        .as("Secret variable name should be the full vault path (project scope)")
        .isEqualTo(vaultPath);
    assertThat(variable.getValue().getValue().getScope())
        .as("Secret should be resolved as project scope")
        .isEqualTo(Scope.PROJECT);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testObtain_accountScopedVaultPathWithDots_shouldResolveAccountScope() {
    String vaultPath = "hashicorpvault://connectorId/path/to/secret.with.dot#key";
    String accountScopedVaultPath = "account." + vaultPath;

    functor.obtain(accountScopedVaultPath, TOKEN);

    List<SecretNGVariable> variables = functor.getSecretNGVariableDetails();
    assertThat(variables).hasSize(1);
    SecretNGVariable variable = variables.get(0);
    assertThat(variable.getName())
        .as("Secret variable name should be the vault path without the scope prefix")
        .isEqualTo(vaultPath);
    assertThat(variable.getValue().getValue().getScope())
        .as("Secret should be resolved as account scope")
        .isEqualTo(Scope.ACCOUNT);
  }
}
