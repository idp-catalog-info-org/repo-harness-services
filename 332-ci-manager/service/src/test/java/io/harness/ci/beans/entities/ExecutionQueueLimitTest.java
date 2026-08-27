/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.beans.entities;

import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.app.beans.entities.ExecutionQueueLimit;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ExecutionQueueLimitTest {
  private static final String ACCOUNT_ID = "testAccountId";
  private static final String UUID = "testUuid";
  private static final String MAC_LIMIT = "10";
  private static final String TOTAL_LIMIT = "100";

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testBuilder() {
    ExecutionQueueLimit executionQueueLimit = ExecutionQueueLimit.builder()
                                                  .uuid(UUID)
                                                  .accountIdentifier(ACCOUNT_ID)
                                                  .macExecLimit(MAC_LIMIT)
                                                  .totalExecLimit(TOTAL_LIMIT)
                                                  .build();

    assertThat(executionQueueLimit).isNotNull();
    assertThat(executionQueueLimit.getUuid()).isEqualTo(UUID);
    assertThat(executionQueueLimit.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(executionQueueLimit.getMacExecLimit()).isEqualTo(MAC_LIMIT);
    assertThat(executionQueueLimit.getTotalExecLimit()).isEqualTo(TOTAL_LIMIT);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testBuilderWithNullValues() {
    ExecutionQueueLimit executionQueueLimit = ExecutionQueueLimit.builder().accountIdentifier(ACCOUNT_ID).build();

    assertThat(executionQueueLimit).isNotNull();
    assertThat(executionQueueLimit.getUuid()).isNull();
    assertThat(executionQueueLimit.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(executionQueueLimit.getMacExecLimit()).isNull();
    assertThat(executionQueueLimit.getTotalExecLimit()).isNull();
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testSettersAndGetters() {
    ExecutionQueueLimit executionQueueLimit = ExecutionQueueLimit.builder().build();

    executionQueueLimit.setUuid(UUID);
    executionQueueLimit.setAccountIdentifier(ACCOUNT_ID);
    executionQueueLimit.setMacExecLimit(MAC_LIMIT);
    executionQueueLimit.setTotalExecLimit(TOTAL_LIMIT);

    assertThat(executionQueueLimit.getUuid()).isEqualTo(UUID);
    assertThat(executionQueueLimit.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(executionQueueLimit.getMacExecLimit()).isEqualTo(MAC_LIMIT);
    assertThat(executionQueueLimit.getTotalExecLimit()).isEqualTo(TOTAL_LIMIT);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testEqualsAndHashCode() {
    ExecutionQueueLimit limit1 = ExecutionQueueLimit.builder()
                                     .uuid(UUID)
                                     .accountIdentifier(ACCOUNT_ID)
                                     .macExecLimit(MAC_LIMIT)
                                     .totalExecLimit(TOTAL_LIMIT)
                                     .build();

    ExecutionQueueLimit limit2 = ExecutionQueueLimit.builder()
                                     .uuid(UUID)
                                     .accountIdentifier(ACCOUNT_ID)
                                     .macExecLimit(MAC_LIMIT)
                                     .totalExecLimit(TOTAL_LIMIT)
                                     .build();

    assertThat(limit1).isEqualTo(limit2);
    assertThat(limit1.hashCode()).isEqualTo(limit2.hashCode());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testNotEquals() {
    ExecutionQueueLimit limit1 = ExecutionQueueLimit.builder()
                                     .uuid(UUID)
                                     .accountIdentifier(ACCOUNT_ID)
                                     .macExecLimit(MAC_LIMIT)
                                     .totalExecLimit(TOTAL_LIMIT)
                                     .build();

    ExecutionQueueLimit limit2 = ExecutionQueueLimit.builder()
                                     .uuid("differentUuid")
                                     .accountIdentifier(ACCOUNT_ID)
                                     .macExecLimit(MAC_LIMIT)
                                     .totalExecLimit(TOTAL_LIMIT)
                                     .build();

    assertThat(limit1).isNotEqualTo(limit2);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testToString() {
    ExecutionQueueLimit executionQueueLimit = ExecutionQueueLimit.builder()
                                                  .uuid(UUID)
                                                  .accountIdentifier(ACCOUNT_ID)
                                                  .macExecLimit(MAC_LIMIT)
                                                  .totalExecLimit(TOTAL_LIMIT)
                                                  .build();

    String toString = executionQueueLimit.toString();

    assertThat(toString).contains(UUID);
    assertThat(toString).contains(ACCOUNT_ID);
    assertThat(toString).contains(MAC_LIMIT);
    assertThat(toString).contains(TOTAL_LIMIT);
  }
}
