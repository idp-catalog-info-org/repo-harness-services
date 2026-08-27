/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook;

import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.HARSHIT;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.DecryptedSecretValue;
import io.harness.beans.HeaderConfig;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.gitxwebhooks.entity.GenericWebhookSpec;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.entity.HmacSpec;
import io.harness.gitsync.gitxwebhooks.entity.SlackWebhookSpec;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.rule.Owner;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class WebhookHmacHelperTest extends CategoryTest {
  @InjectMocks WebhookHmacHelper webhookHmacHelper;
  @Mock NGEncryptedDataService encryptedDataService;
  @Mock ScopeResolutionHelper scopeResolutionHelper;
  private static final String ACCOUNT_ID = "DUMMY_ACCOUNT_ID";
  private static final String ORG_ID = "DUMMY_ORG_ID";
  private static final String PROJECT_ID = "DUMMY_PROJECT_ID";
  private static final String PARENT_UNIQUE_ID = "PARENT_UNIQUE_ID";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testVerifyHMACSignature() {
    GitXWebhook webhook = GitXWebhook.builder()
                              .identifier("hamcwebhook")
                              .name("hmacwebhook")
                              .accountIdentifier(ACCOUNT_ID)
                              .parentUniqueId(ACCOUNT_ID)
                              .webhookType("GENERIC")
                              .spec(GenericWebhookSpec.builder()
                                        .authType("Hmac")
                                        .authSpec(HmacSpec.builder()
                                                      .header("header")
                                                      .secretKey("account.secretKey")
                                                      .hashAlgorithm("HmacSHA256")
                                                      .build())
                                        .build())
                              .build();

    String eventPayload = "{HelloWorld}";

    List<HeaderConfig> httpHeaders = new ArrayList<>();
    HeaderConfig headerConfig =
        HeaderConfig.builder()
            .key("header")
            .values(Arrays.asList("aab26602f46c049dc444ef96828a0fa5b465f16dc58b94b1b64b93ae38d65260"))
            .build();
    httpHeaders.add(headerConfig);
    when(scopeResolutionHelper.getScopeInfo(anyString(), anyString()))
        .thenReturn(ScopeInfo.builder()
                        .scopeType(ScopeLevel.ACCOUNT)
                        .accountIdentifier(ACCOUNT_ID)
                        .uniqueId(ACCOUNT_ID)
                        .build());
    when(scopeResolutionHelper.getScopeInfo(anyString(), any(), any()))
        .thenReturn(ScopeInfo.builder()
                        .scopeType(ScopeLevel.ACCOUNT)
                        .accountIdentifier(ACCOUNT_ID)
                        .uniqueId(ACCOUNT_ID)
                        .build());
    when(encryptedDataService.decryptSecret(any(ScopeInfo.class), any()))
        .thenReturn(DecryptedSecretValue.builder().accountIdentifier(ACCOUNT_ID).decryptedValue("secret").build());

    webhookHmacHelper.verifyHMACSignature(webhook, eventPayload, httpHeaders);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testVerifyHMACSignatureFailure() {
    GitXWebhook webhook = GitXWebhook.builder()
                              .identifier("hamcwebhook")
                              .name("hmacwebhook")
                              .accountIdentifier(ACCOUNT_ID)
                              .parentUniqueId(ACCOUNT_ID)
                              .webhookType("GENERIC")
                              .spec(GenericWebhookSpec.builder()
                                        .authType("Hmac")
                                        .authSpec(HmacSpec.builder()
                                                      .header("header")
                                                      .secretKey("account.secret")
                                                      .hashAlgorithm("HmacSHA256")
                                                      .build())
                                        .build())
                              .build();

    String eventPayload = "{HelloWorld}";

    List<HeaderConfig> httpHeaders = new ArrayList<>();
    HeaderConfig headerConfig =
        HeaderConfig.builder().key("header").values(Arrays.asList("Incorrect-signature")).build();
    httpHeaders.add(headerConfig);
    when(scopeResolutionHelper.getScopeInfo(anyString(), anyString()))
        .thenReturn(ScopeInfo.builder()
                        .scopeType(ScopeLevel.ACCOUNT)
                        .accountIdentifier(ACCOUNT_ID)
                        .uniqueId(ACCOUNT_ID)
                        .build());
    when(scopeResolutionHelper.getScopeInfo(anyString(), any(), any()))
        .thenReturn(ScopeInfo.builder()
                        .scopeType(ScopeLevel.ACCOUNT)
                        .accountIdentifier(ACCOUNT_ID)
                        .uniqueId(ACCOUNT_ID)
                        .build());
    when(encryptedDataService.decryptSecret(any(), any()))
        .thenReturn(DecryptedSecretValue.builder().accountIdentifier(ACCOUNT_ID).decryptedValue("secret").build());

    assertThatThrownBy(() -> webhookHmacHelper.verifyHMACSignature(webhook, eventPayload, httpHeaders))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("HMAC Signature from the Http headers does not match with the expected signature calculated.");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testVerifySlackHMACSignature() {
    // Example & values are based on Slack Docs - https://api.slack.com/authentication/verifying-requests-from-slack
    GitXWebhook webhook = GitXWebhook.builder()
                              .identifier("hamcwebhook")
                              .name("hmacwebhook")
                              .accountIdentifier(ACCOUNT_ID)
                              .parentUniqueId(ACCOUNT_ID)
                              .webhookType("SLACK")
                              .spec(SlackWebhookSpec.builder()
                                        .authType("Hmac")
                                        .authSpec(HmacSpec.builder().secretKey("account.secretKey").build())
                                        .build())
                              .build();

    String eventPayload =
        "token=xyzz0WbapA4vBCDEFasx0q6G&team_id=T1DC2JH3J&team_domain=testteamnow&channel_id=G8PSS9T3V&channel_name="
        + "foobar&user_id=U2CERLKJA&user_name=roadrunner&command=%2Fwebhook-collect&text=&response_url=https%3A%2F%"
        + "2Fhooks.slack.com%2Fcommands%2FT1DC2JH3J%2F397700885554%2F96rGlfmibIGlgcZRskXaIFfN&trigger_id=398738663015."
        + "47445629121.803a0bc887a14d10d2c447fce8b6703c";

    List<HeaderConfig> httpHeaders = Lists.newArrayList(
        HeaderConfig.builder().key("X-Slack-Request-Timestamp").values(Arrays.asList("1531420618")).build(),
        HeaderConfig.builder()
            .key("X-Slack-Signature")
            .values(Arrays.asList("v0=a2114d57b48eac39b9ad189dd8316235a7b4a8d21a10bd27519666489c69b503"))
            .build());
    when(scopeResolutionHelper.getScopeInfo(anyString(), anyString()))
        .thenReturn(ScopeInfo.builder()
                        .scopeType(ScopeLevel.ACCOUNT)
                        .accountIdentifier(ACCOUNT_ID)
                        .uniqueId(ACCOUNT_ID)
                        .build());
    when(scopeResolutionHelper.getScopeInfo(anyString(), any(), any()))
        .thenReturn(ScopeInfo.builder()
                        .scopeType(ScopeLevel.ACCOUNT)
                        .accountIdentifier(ACCOUNT_ID)
                        .uniqueId(ACCOUNT_ID)
                        .build());
    when(encryptedDataService.decryptSecret(any(ScopeInfo.class), any()))
        .thenReturn(DecryptedSecretValue.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .decryptedValue("8f742231b10e8888abcd99yyyzzz85a5")
                        .build());

    webhookHmacHelper.verifyHMACSignatureForSlack(webhook, eventPayload, httpHeaders);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testVerifySlackHMACSignatureFailure() {
    GitXWebhook webhook = GitXWebhook.builder()
                              .identifier("hamcwebhook")
                              .name("hmacwebhook")
                              .accountIdentifier(ACCOUNT_ID)
                              .parentUniqueId(ACCOUNT_ID)
                              .webhookType("SLACK")
                              .spec(SlackWebhookSpec.builder()
                                        .authType("Hmac")
                                        .authSpec(HmacSpec.builder().secretKey("account.secretKey").build())
                                        .build())
                              .build();

    String eventPayload = "{HelloWorld}";

    List<HeaderConfig> httpHeaders = Lists.newArrayList(
        HeaderConfig.builder().key("X-Slack-Request-Timestamp").values(Arrays.asList("1531420618")).build(),
        HeaderConfig.builder()
            .key("X-Slack-Signature")
            .values(Arrays.asList("v0=a2114d57b48eac39b9ad189dd8316235a7b4a8d21a10bd27519666489c69b503"))
            .build());
    when(scopeResolutionHelper.getScopeInfo(anyString(), anyString()))
        .thenReturn(ScopeInfo.builder()
                        .scopeType(ScopeLevel.ACCOUNT)
                        .accountIdentifier(ACCOUNT_ID)
                        .uniqueId(ACCOUNT_ID)
                        .build());
    when(scopeResolutionHelper.getScopeInfo(anyString(), any(), any()))
        .thenReturn(ScopeInfo.builder()
                        .scopeType(ScopeLevel.ACCOUNT)
                        .accountIdentifier(ACCOUNT_ID)
                        .uniqueId(ACCOUNT_ID)
                        .build());
    when(encryptedDataService.decryptSecret(any(), any()))
        .thenReturn(DecryptedSecretValue.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .decryptedValue("8f742231b10e8888abcd99yyyzzz85a5")
                        .build());

    assertThatThrownBy(() -> webhookHmacHelper.verifyHMACSignatureForSlack(webhook, eventPayload, httpHeaders))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("HMAC Signature from the Http headers does not match with the expected signature calculated.");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testVerifyHMACSignatureForAccountLevelSecret() {
    GitXWebhook webhook = GitXWebhook.builder()
                              .identifier("hamcwebhook")
                              .name("hmacwebhook")
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .parentUniqueId(PARENT_UNIQUE_ID)
                              .webhookType("GENERIC")
                              .spec(GenericWebhookSpec.builder()
                                        .authType("Hmac")
                                        .authSpec(HmacSpec.builder()
                                                      .header("header")
                                                      .secretKey("account.secretKey")
                                                      .hashAlgorithm("HmacSHA256")
                                                      .build())
                                        .build())
                              .build();

    String eventPayload = "{HelloWorld}";

    List<HeaderConfig> httpHeaders = new ArrayList<>();
    HeaderConfig headerConfig =
        HeaderConfig.builder()
            .key("header")
            .values(Arrays.asList("aab26602f46c049dc444ef96828a0fa5b465f16dc58b94b1b64b93ae38d65260"))
            .build();
    httpHeaders.add(headerConfig);
    when(scopeResolutionHelper.getScopeInfo(anyString(), anyString()))
        .thenReturn(ScopeInfo.builder()
                        .scopeType(ScopeLevel.PROJECT)
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_ID)
                        .projectIdentifier(PROJECT_ID)
                        .uniqueId(PARENT_UNIQUE_ID)
                        .build());
    when(scopeResolutionHelper.getScopeInfo(anyString(), any(), any()))
        .thenReturn(ScopeInfo.builder()
                        .scopeType(ScopeLevel.PROJECT)
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_ID)
                        .projectIdentifier(PROJECT_ID)
                        .uniqueId(PARENT_UNIQUE_ID)
                        .build());
    ArgumentCaptor<ScopeInfo> argumentCaptor = ArgumentCaptor.forClass(ScopeInfo.class);
    when(encryptedDataService.decryptSecret(argumentCaptor.capture(), any()))
        .thenReturn(DecryptedSecretValue.builder().accountIdentifier(ACCOUNT_ID).decryptedValue("secret").build());
    webhookHmacHelper.verifyHMACSignature(webhook, eventPayload, httpHeaders);
    assertThat(argumentCaptor.getValue().getScopeType().name()).isEqualTo(ScopeLevel.PROJECT.name());
  }
}
