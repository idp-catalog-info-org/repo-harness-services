/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.expressions;

import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.Reference;
import io.harness.product.ci.scm.proto.Repository;
import io.harness.product.ci.scm.proto.User;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class TriggerExpressionEvaluatorTest extends CategoryTest {
  ParseWebhookResponse prEvent = ParseWebhookResponse.newBuilder()
                                     .setPr(PullRequestHook.newBuilder()
                                                .setPr(PullRequest.newBuilder()
                                                           .setNumber(1)
                                                           .setTitle("This is Title")
                                                           .setTarget("target")
                                                           .setSource("source")
                                                           .setSha("123")
                                                           .setBase(Reference.newBuilder().setSha("234").build())
                                                           .build())
                                                .setRepo(Repository.newBuilder().setLink("https://github.com").build())
                                                .setSender(User.newBuilder().setLogin("user").build())
                                                .build())
                                     .build();

  ParseWebhookResponse prEvent1 =
      ParseWebhookResponse.newBuilder()
          .setPr(PullRequestHook.newBuilder()
                     .setPr(PullRequest.newBuilder()
                                .setNumber(11)
                                .setTitle("This is Title1")
                                .setTarget("target1")
                                .setSource("source1")
                                .setSha("1231")
                                .setBase(Reference.newBuilder().setSha("2341").build())
                                .build())
                     .setRepo(Repository.newBuilder().setLink("https://github.com1").build())
                     .setSender(User.newBuilder().setLogin("user1").build())
                     .build())
          .build();

  ParseWebhookResponse pushEvent =
      ParseWebhookResponse.newBuilder()
          .setPush(PushHook.newBuilder()
                       .setAfter("456")
                       .setRepo(Repository.newBuilder().setLink("https://github.com").build())
                       .setSender(User.newBuilder().setLogin("user").build())
                       .setRef("ref")
                       .build())
          .build();

  ParseWebhookResponse pushEvent1 =
      ParseWebhookResponse.newBuilder()
          .setPush(PushHook.newBuilder()
                       .setAfter("4561")
                       .setRepo(Repository.newBuilder().setLink("https://github.com1").build())
                       .setSender(User.newBuilder().setLogin("user1").build())
                       .setRef("ref1")
                       .build())
          .build();
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }
  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testInstanceCreationWithoutTriggerPayload() {
    // PR payload
    ParseWebhookResponse parseWebhookResponse = ParseWebhookResponse.newBuilder().setPr(prEvent.getPr()).build();
    TriggerExpressionEvaluator expressionEvaluator =
        new TriggerExpressionEvaluator(parseWebhookResponse, null, null, "{}", null);
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.branch>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("source");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.sourceBranch>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("source");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.targetBranch>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("target");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.event>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("PR");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.type>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("Webhook");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.commitSha>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("123");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.baseCommitSha>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("234");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.prNumber>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("1");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.repoUrl>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("https://github.com");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.gitUser>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("user");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.prTitle>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("This is Title");

    // Push payload
    ParseWebhookResponse parseWebhookResponse1 = ParseWebhookResponse.newBuilder().setPush(pushEvent.getPush()).build();
    TriggerExpressionEvaluator expressionEvaluator1 =
        new TriggerExpressionEvaluator(parseWebhookResponse1, null, null, "{}", null);
    assertThat(expressionEvaluator1.evaluateExpressionWithExpressionMode(
                   "<+trigger.branch>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("ref");
    assertThat(expressionEvaluator1.evaluateExpressionWithExpressionMode(
                   "<+trigger.repoUrl>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("https://github.com");
    assertThat(expressionEvaluator1.evaluateExpressionWithExpressionMode(
                   "<+trigger.gitUser>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("user");
    assertThat(expressionEvaluator1.evaluateExpressionWithExpressionMode(
                   "<+trigger.event>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("PUSH");
    assertThat(expressionEvaluator1.evaluateExpressionWithExpressionMode(
                   "<+trigger.type>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("Webhook");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testInstanceCreationWithTriggerPayload() {
    // PR payload
    TriggerPayload triggerPayload = TriggerPayload.newBuilder()
                                        .setParsedPayload(ParsedPayload.newBuilder().setPr(prEvent1.getPr()).build())
                                        .build();
    TriggerExpressionEvaluator expressionEvaluator = new TriggerExpressionEvaluator(triggerPayload, null, "{}", null);
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.branch>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("source1");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.sourceBranch>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("source1");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.targetBranch>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("target1");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.event>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("PR");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.type>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("Webhook");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.commitSha>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("1231");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.baseCommitSha>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("2341");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.prNumber>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("11");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.repoUrl>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("https://github.com1");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.gitUser>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("user1");
    assertThat(expressionEvaluator.evaluateExpressionWithExpressionMode(
                   "<+trigger.prTitle>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("This is Title1");

    // Push payload
    TriggerPayload triggerPayload1 =
        TriggerPayload.newBuilder()
            .setParsedPayload(ParsedPayload.newBuilder().setPush(pushEvent1.getPush()).build())
            .build();
    TriggerExpressionEvaluator expressionEvaluator1 = new TriggerExpressionEvaluator(triggerPayload1, null, "{}", null);
    assertThat(expressionEvaluator1.evaluateExpressionWithExpressionMode(
                   "<+trigger.branch>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("ref1");
    assertThat(expressionEvaluator1.evaluateExpressionWithExpressionMode(
                   "<+trigger.repoUrl>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("https://github.com1");
    assertThat(expressionEvaluator1.evaluateExpressionWithExpressionMode(
                   "<+trigger.gitUser>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("user1");
    assertThat(expressionEvaluator1.evaluateExpressionWithExpressionMode(
                   "<+trigger.event>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("PUSH");
    assertThat(expressionEvaluator1.evaluateExpressionWithExpressionMode(
                   "<+trigger.type>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("Webhook");
  }
}
