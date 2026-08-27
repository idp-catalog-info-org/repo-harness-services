/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.SHREYAS_NAGARAJ;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.InputSetValidatorType;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.validation.InputSetValidator;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
public class ApproversDTOTest extends CategoryTest {
  public static final String NON_LIST = "notAList";
  private static final String TEST_EMAIL = "testEmail@harness.io";
  private static final String TEST_EXPRESSION = "<+pipleine.var.var1>";

  InputSetValidator validator = new InputSetValidator(InputSetValidatorType.REGEX, "");

  private Approvers createValidApproversBean() {
    return Approvers.builder()
        .userGroups(ParameterField.<List<String>>builder().value(Collections.singletonList("userGroup")).build())
        .minimumCount(ParameterField.<Integer>builder().value(1).build())
        .disallowPipelineExecutor(ParameterField.<Boolean>builder().value(false).build())
        .disallowedUserEmails(
            ParameterField.<List<String>>builder().value(Collections.singletonList(TEST_EMAIL)).build())
        .build();
  }

  private Approvers createApproversBeanWithUnResolvedBooleanParameter() {
    return Approvers.builder()
        .userGroups(ParameterField.<List<String>>builder().value(Collections.singletonList("userGroup")).build())
        .minimumCount(ParameterField.<Integer>builder().value(1).build())
        .disallowPipelineExecutor(ParameterField.createExpressionField(false, null, validator, false))
        .build();
  }

  private Approvers createApproversBeanWithUnResolvedUserGroups() {
    return Approvers.builder()
        .userGroups(ParameterField.createExpressionField(true, TEST_EXPRESSION, validator, false))
        .minimumCount(ParameterField.<Integer>builder().value(1).build())
        .disallowPipelineExecutor(ParameterField.<Boolean>builder().value(false).build())
        .build();
  }

  private Approvers createInValidApproversBean() {
    return Approvers.builder()
        .userGroups(ParameterField.<List<String>>builder().value(Collections.singletonList("userGroup")).build())
        .minimumCount(ParameterField.<Integer>builder().value(0).build())
        .disallowPipelineExecutor(ParameterField.<Boolean>builder().value(false).build())
        .build();
  }

  private Approvers createValidApproversBeanWithServiceAccounts() {
    return Approvers.builder()
        .serviceAccounts(
            ParameterField.<List<String>>builder().value(Collections.singletonList("serviceAccounts")).build())
        .minimumCount(ParameterField.<Integer>builder().value(1).build())
        .disallowPipelineExecutor(ParameterField.<Boolean>builder().value(false).build())
        .build();
  }

  private Approvers createApproversBeanWithUnResolvedBooleanParameterWithServiceAccounts() {
    return Approvers.builder()
        .serviceAccounts(
            ParameterField.<List<String>>builder().value(Collections.singletonList("serviceAccounts")).build())
        .minimumCount(ParameterField.<Integer>builder().value(1).build())
        .disallowPipelineExecutor(ParameterField.createExpressionField(false, null, validator, false))
        .build();
  }

  private Approvers createApproversBeanWithUnResolvedServiceAccounts() {
    return Approvers.builder()
        .serviceAccounts(ParameterField.createExpressionField(true, TEST_EXPRESSION, validator, false))
        .minimumCount(ParameterField.<Integer>builder().value(1).build())
        .disallowPipelineExecutor(ParameterField.<Boolean>builder().value(false).build())
        .build();
  }

  private Approvers createInValidApproversBeanWithServiceAccounts() {
    return Approvers.builder()
        .serviceAccounts(
            ParameterField.<List<String>>builder().value(Collections.singletonList("serviceAccounts")).build())
        .minimumCount(ParameterField.<Integer>builder().value(0).build())
        .disallowPipelineExecutor(ParameterField.<Boolean>builder().value(false).build())
        .build();
  }

  private Approvers createApproversBeanWithEmptyDisallowedUserEmails() {
    return Approvers.builder()
        .userGroups(ParameterField.<List<String>>builder().value(Collections.singletonList("userGroup")).build())
        .minimumCount(ParameterField.<Integer>builder().value(1).build())
        .disallowPipelineExecutor(ParameterField.<Boolean>builder().value(false).build())
        .disallowedUserEmails(ParameterField.<List<String>>builder().value(Collections.emptyList()).build())
        .build();
  }

  private Approvers createApproversBeanWithNullDisallowedUserEmails() {
    return Approvers.builder()
        .userGroups(ParameterField.<List<String>>builder().value(Collections.singletonList("userGroup")).build())
        .minimumCount(ParameterField.<Integer>builder().value(1).build())
        .disallowPipelineExecutor(ParameterField.<Boolean>builder().value(false).build())
        .disallowedUserEmails(null)
        .build();
  }

  private Approvers createApproversBeanWithStringDisallowedUserEmails() {
    ParameterField stringTypeField = ParameterField.createValueField(NON_LIST);
    return Approvers.builder()
        .userGroups(ParameterField.<List<String>>builder().value(Collections.singletonList("userGroup")).build())
        .minimumCount(ParameterField.<Integer>builder().value(1).build())
        .disallowPipelineExecutor(ParameterField.<Boolean>builder().value(false).build())
        .disallowedUserEmails(stringTypeField)
        .build();
  }

  private Approvers createApproversBeanWithUnresolvedDisallowedUserEmails() {
    return Approvers.builder()
        .disallowedUserEmails(ParameterField.createExpressionField(true, TEST_EXPRESSION, validator, false))
        .minimumCount(ParameterField.<Integer>builder().value(1).build())
        .disallowPipelineExecutor(ParameterField.<Boolean>builder().value(false).build())
        .build();
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void fromValidApproversBeans() {
    assertThat(ApproversDTO.fromApprovers(createValidApproversBean())).isNotNull();
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void fromInValidApproversBeansWithUnresolvedUserGroups() {
    assertThatThrownBy(() -> ApproversDTO.fromApprovers(createApproversBeanWithUnResolvedUserGroups()))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void fromInValidApproversBeansWithInvalidMinCount() {
    assertThatThrownBy(() -> ApproversDTO.fromApprovers(createInValidApproversBean()))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testFromApprovers_NullInput() {
    ApproversDTO dto = ApproversDTO.fromApprovers(null);
    assertThat(dto).isNull();
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testFromApproversWithUnResolvedBooleanParameter() {
    ApproversDTO dto = ApproversDTO.fromApprovers(createApproversBeanWithUnResolvedBooleanParameter());
    assertThat(dto).isNotNull();
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void fromValidApproversBeansWithServiceAccounts() {
    assertThat(ApproversDTO.fromApprovers(createValidApproversBeanWithServiceAccounts())).isNotNull();
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void fromInValidApproversBeansWithUnresolvedServiceAccounts() {
    assertThatThrownBy(() -> ApproversDTO.fromApprovers(createApproversBeanWithUnResolvedServiceAccounts()))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void fromInValidApproversBeansWithInvalidMinCountWithServiceAccounts() {
    assertThatThrownBy(() -> ApproversDTO.fromApprovers(createInValidApproversBeanWithServiceAccounts()))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testFromApproversWithUnResolvedBooleanParameterWithServiceAccounts() {
    ApproversDTO dto =
        ApproversDTO.fromApprovers(createApproversBeanWithUnResolvedBooleanParameterWithServiceAccounts());
    assertThat(dto).isNotNull();
  }

  @Test
  @Owner(developers = SHREYAS_NAGARAJ)
  @Category(UnitTests.class)
  public void testFromApproversWithUnresolvedDisallowedUserEmails() {
    assertThatThrownBy(() -> ApproversDTO.fromApprovers(createApproversBeanWithUnresolvedDisallowedUserEmails()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            String.format("Disallowed User Emails should be a list of emails, got value %s of type %s", TEST_EXPRESSION,
                String.class.getSimpleName()));
  }

  @Test
  @Owner(developers = SHREYAS_NAGARAJ)
  @Category(UnitTests.class)
  public void testFromApproversWithValidDisallowedUserEmails() {
    ApproversDTO dto = ApproversDTO.fromApprovers(createValidApproversBean());
    assertThat(dto).isNotNull();
    assertThat(dto.getDisallowedUserEmails()).containsExactly(TEST_EMAIL);
  }

  @Test
  @Owner(developers = SHREYAS_NAGARAJ)
  @Category(UnitTests.class)
  public void testFromApproversWithEmptyDisallowedUserEmails() {
    ApproversDTO dto = ApproversDTO.fromApprovers(createApproversBeanWithEmptyDisallowedUserEmails());
    assertThat(dto).isNotNull();
    assertThat(dto.getDisallowedUserEmails()).isEmpty();
  }

  @Test
  @Owner(developers = SHREYAS_NAGARAJ)
  @Category(UnitTests.class)
  public void testFromApproversWithNullDisallowedUserEmails() {
    // Empty list must be created even if input DisallowedUserEmails is null
    ApproversDTO dto = ApproversDTO.fromApprovers(createApproversBeanWithNullDisallowedUserEmails());
    assertThat(dto).isNotNull();
    assertThat(dto.getDisallowedUserEmails()).isEmpty(); // asserting empty list
  }

  @Test
  @Owner(developers = SHREYAS_NAGARAJ)
  @Category(UnitTests.class)
  public void testFromApproversWithStringDisallowedUserEmails() {
    assertThatThrownBy(() -> ApproversDTO.fromApprovers(createApproversBeanWithStringDisallowedUserEmails()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            String.format("Disallowed User Emails should be a list of emails, got value %s of type %s", NON_LIST,
                String.class.getSimpleName()));
  }
}
