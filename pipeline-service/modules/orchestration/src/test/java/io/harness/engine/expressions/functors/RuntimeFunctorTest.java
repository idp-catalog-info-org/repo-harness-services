/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.pms.contracts.steps.StepCategory.PIPELINE;
import static io.harness.pms.contracts.steps.StepCategory.STAGE;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.SATYAKOTA;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.expression.ConnectorInputsMapper;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.rule.Owner;
import io.harness.utils.CDStepsExpressionResolver;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.LoggerFactory;

public class RuntimeFunctorTest extends CategoryTest {
  private RuntimeFunctor runtimeFunctor;
  private ListAppender<ILoggingEvent> listAppender;
  private Logger logger;
  private Ambiance ambiance = Ambiance.newBuilder()
                                  .putSetupAbstractions("accountId", "test-account")
                                  .putSetupAbstractions("orgIdentifier", "test-org")
                                  .putSetupAbstractions("projectIdentifier", "test-project")
                                  .build();

  @Before
  public void setUp() {
    runtimeFunctor = RuntimeFunctor.builder().build();
    logger = (Logger) LoggerFactory.getLogger(RuntimeFunctor.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testToStringWithListOfStrings() {
    List<String> stringList = Arrays.asList("value1", "value2", "value3");
    String result = runtimeFunctor.toString(stringList);
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo("value1,value2,value3");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testToStringWithEmptyList() {
    List<String> emptyList = Collections.emptyList();
    String result = runtimeFunctor.toString(emptyList);
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testToStringWithListOfMixedTypes() {
    List<Object> mixedList = Arrays.asList("value1", 123, "value3");
    String result = runtimeFunctor.toString(mixedList);
    assertThat(result).isNull();
    assertThat(listAppender.list).isNotEmpty();
    assertThat(listAppender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    assertThat(listAppender.list.get(0).getMessage())
        .isEqualTo("Object provided should be map or list with data type string");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testToStringWithStringArray() {
    String[] stringArray = new String[] {"value1", "value2", "value3"};
    String result = runtimeFunctor.toString(stringArray);
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo("value1,value2,value3");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testToStringWithEmptyStringArray() {
    String[] emptyArray = new String[0];
    String result = runtimeFunctor.toString(emptyArray);
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testToStringWithMapOfStrings() {
    Map<String, String> stringMap = new HashMap<>();
    stringMap.put("key1", "value1");
    stringMap.put("key2", "value2");
    stringMap.put("key3", "value3");
    String result = runtimeFunctor.toString(stringMap);
    assertThat(result).isNotNull();
    assertThat(result).contains("key1=value1");
    assertThat(result).contains("key2=value2");
    assertThat(result).contains("key3=value3");
    assertThat(result).contains(",");
    assertThat(result).isEqualTo("key1=value1,key2=value2,key3=value3");
  }
  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testToStringWithEmptyMap() {
    Map<String, String> emptyMap = Collections.emptyMap();
    String result = runtimeFunctor.toString(emptyMap);
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testToStringWithMapOfMixedKeyTypes() {
    Map<Object, String> mixedKeyMap = new HashMap<>();
    mixedKeyMap.put("key1", "value1");
    mixedKeyMap.put(123, "value2");
    String result = runtimeFunctor.toString(mixedKeyMap);
    assertThat(result).isNull();
    assertThat(listAppender.list).isNotEmpty();
    assertThat(listAppender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    assertThat(listAppender.list.get(0).getMessage())
        .isEqualTo("Object provided should be map or list with data type string");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testToStringWithMapOfMixedValueTypes() {
    Map<String, Object> mixedValueMap = new HashMap<>();
    mixedValueMap.put("key1", "value1");
    mixedValueMap.put("key2", 123);
    String result = runtimeFunctor.toString(mixedValueMap);
    assertThat(result).isNull();
    assertThat(listAppender.list).isNotEmpty();
    assertThat(listAppender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    assertThat(listAppender.list.get(0).getMessage())
        .isEqualTo("Object provided should be map or list with data type string");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testToStringWithNonSupportedType() {
    Integer nonSupportedType = 123;
    String result = runtimeFunctor.toString(nonSupportedType);
    assertThat(result).isNull();
    assertThat(listAppender.list).isNotEmpty();
    assertThat(listAppender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    assertThat(listAppender.list.get(0).getMessage())
        .isEqualTo("Object provided should be map or list with data type string");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testToStringWithNullInput() {
    Object nullInput = null;
    String result = runtimeFunctor.toString(nullInput);
    assertThat(result).isNull();
    assertThat(listAppender.list).isNotEmpty();
    assertThat(listAppender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    assertThat(listAppender.list.get(0).getMessage())
        .isEqualTo("Object provided should be map or list with data type string");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testHandleParentFQN() {
    ambiance = Ambiance.newBuilder()
                   .addLevels(io.harness.pms.contracts.ambiance.Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(PIPELINE).build())
                                  .setIdentifier("pipeline")
                                  .build())
                   .addLevels(io.harness.pms.contracts.ambiance.Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                  .setIdentifier("stages")
                                  .build())
                   .addLevels(io.harness.pms.contracts.ambiance.Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(STAGE).build())
                                  .setIdentifier("stage_1__testenv_k8scluster")
                                  .build())
                   .addLevels(io.harness.pms.contracts.ambiance.Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                  .setIdentifier("steps")
                                  .build())
                   .addLevels(io.harness.pms.contracts.ambiance.Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP_GROUP).build())
                                  .setIdentifier("k8sRollingDeployStep_1")
                                  .build())
                   .addLevels(io.harness.pms.contracts.ambiance.Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                  .setIdentifier("steps")
                                  .build())
                   .addLevels(io.harness.pms.contracts.ambiance.Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                  .setIdentifier("k8sRollingApply")
                                  .build())
                   .build();
    assertThat(AmbianceUtils.getFQNUsingLevels(ambiance.getLevelsList()))
        .isEqualTo("pipeline.stages.stage_1__testenv_k8scluster.steps.k8sRollingDeployStep_1.steps.k8sRollingApply");

    runtimeFunctor.ambiance = ambiance;

    String result = runtimeFunctor.handleFQN();
    assertThat(result).isNotEmpty();
    assertThat(result).isEqualTo("pipeline.stages.stage_1__testenv_k8scluster.steps.k8sRollingDeployStep_1.steps");

    Object resultObj = runtimeFunctor.getParentFqn(0);
    assertThat(resultObj).isInstanceOf(String.class);
    String resultStr = (String) resultObj;
    assertThat(resultStr).isEqualTo("pipeline.stages.stage_1__testenv_k8scluster.steps.k8sRollingDeployStep_1.steps");

    resultObj = runtimeFunctor.getParentFqn(1);
    assertThat(resultObj).isInstanceOf(String.class);
    resultStr = (String) resultObj;
    assertThat(resultStr).isNotEmpty();
    assertThat(resultStr).isEqualTo("pipeline.stages.stage_1__testenv_k8scluster.steps");

    resultObj = runtimeFunctor.handleParentFQN(2);
    assertThat(resultObj).isInstanceOf(String.class);
    resultStr = (String) resultObj;
    assertThat(resultStr).isNotEmpty();
    assertThat(resultStr).isEqualTo("pipeline.stages");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testHandleParentFQNWhenEmpty() {
    ambiance = Ambiance.newBuilder().build();
    assertThat(AmbianceUtils.getFQNUsingLevels(ambiance.getLevelsList())).isEmpty();

    runtimeFunctor.ambiance = ambiance;

    String result = runtimeFunctor.handleFQN();
    assertThat(result).isEmpty();

    Object resultObj = runtimeFunctor.getParentFqn(0);
    assertThat(resultObj).isInstanceOf(String.class);
    String resultStr = (String) resultObj;
    assertThat(resultStr).isEmpty();

    resultObj = runtimeFunctor.getParentFqn(1);
    assertThat(resultObj).isInstanceOf(String.class);
    resultStr = (String) resultObj;
    assertThat(resultStr).isEmpty();

    resultObj = runtimeFunctor.handleParentFQN(2);
    assertThat(resultObj).isInstanceOf(String.class);
    resultStr = (String) resultObj;
    assertThat(resultStr).isEmpty();
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testHandleDlcCacheArgsAllValuesPresent() {
    CDStepsExpressionResolver mockResolver = mock(CDStepsExpressionResolver.class);
    ConnectorInputsMapper mockConnectorMapper = mock(ConnectorInputsMapper.class);
    runtimeFunctor = RuntimeFunctor.builder()
                         .ambiance(ambiance)
                         .cdStepsExpressionResolver(mockResolver)
                         .connectorInputsMapper(mockConnectorMapper)
                         .build();

    Map<String, String> resolvedMap = new LinkedHashMap<>();
    resolvedMap.put("backend", "s3");
    resolvedMap.put("bucket", "my-bucket");
    resolvedMap.put("endpoint_url", "https://s3.amazonaws.com");
    resolvedMap.put("region", "us-east-1");
    resolvedMap.put("account_name", null);
    resolvedMap.put("container_name", null);
    resolvedMap.put("connectorRef", "account.ciplay_aws");
    when(mockResolver.updateExpressions(eq(ambiance), any(Map.class), eq(ExpressionMode.RETURN_NULL_IF_UNRESOLVED)))
        .thenReturn(resolvedMap);

    Map<String, Object> connectorFields = new HashMap<>();
    connectorFields.put("accessKey", "test-access-key-id");
    connectorFields.put("secretKey", "test-secret-access-key");
    when(mockConnectorMapper.fetchConnectorFieldsDetails(
             eq("ciplay_aws"), eq("test-account"), eq(null), eq(null), eq(ambiance)))
        .thenReturn(connectorFields);

    String result = runtimeFunctor.handleDlcCacheArgs();

    assertThat(result).isNotNull();
    assertThat(result).contains("type=s3");
    assertThat(result).contains("bucket=my-bucket");
    assertThat(result).contains("endpoint_url=https://s3.amazonaws.com");
    assertThat(result).contains("region=us-east-1");
    assertThat(result).contains("access_key_id=test-access-key-id");
    assertThat(result).contains("secret_access_key=test-secret-access-key");
    assertThat(result).doesNotContain("assume_role_arn");
    assertThat(result).doesNotContain("gcp_json_key");
    assertThat(result).doesNotContain("oidc_token_id");
    assertThat(result).doesNotContain("account_name");
    assertThat(result).doesNotContain("container_name");
    assertThat(result).doesNotContain("client_id");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testHandleDlcCacheArgsWithNullValues() {
    CDStepsExpressionResolver mockResolver = mock(CDStepsExpressionResolver.class);
    ConnectorInputsMapper mockConnectorMapper = mock(ConnectorInputsMapper.class);
    runtimeFunctor = RuntimeFunctor.builder()
                         .ambiance(ambiance)
                         .cdStepsExpressionResolver(mockResolver)
                         .connectorInputsMapper(mockConnectorMapper)
                         .build();

    Map<String, String> resolvedMap = new LinkedHashMap<>();
    resolvedMap.put("backend", "gcs");
    resolvedMap.put("bucket", "gcs-bucket");
    resolvedMap.put("endpoint_url", null);
    resolvedMap.put("region", null);
    resolvedMap.put("account_name", null);
    resolvedMap.put("container_name", null);
    resolvedMap.put("connectorRef", "account.my_gcp_conn");
    when(mockResolver.updateExpressions(eq(ambiance), any(Map.class), eq(ExpressionMode.RETURN_NULL_IF_UNRESOLVED)))
        .thenReturn(resolvedMap);

    Map<String, Object> connectorFields = new HashMap<>();
    connectorFields.put("jsonKey", "base64-encoded-gcp-key");
    when(mockConnectorMapper.fetchConnectorFieldsDetails(
             eq("my_gcp_conn"), eq("test-account"), eq(null), eq(null), eq(ambiance)))
        .thenReturn(connectorFields);

    String result = runtimeFunctor.handleDlcCacheArgs();

    assertThat(result).isNotNull();
    assertThat(result).isEqualTo("type=gcs,bucket=gcs-bucket,gcp_json_key=<{ base64-encoded-gcp-key | getAsBase64 }>");
    assertThat(result).doesNotContain("endpoint_url");
    assertThat(result).doesNotContain("region");
    assertThat(result).doesNotContain("access_key_id");
    assertThat(result).doesNotContain("secret_access_key");
    assertThat(result).doesNotContain("account_name");
    assertThat(result).doesNotContain("container_name");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testHandleDlcCacheArgsAllCredentialsNull() {
    CDStepsExpressionResolver mockResolver = mock(CDStepsExpressionResolver.class);
    ConnectorInputsMapper mockConnectorMapper = mock(ConnectorInputsMapper.class);
    runtimeFunctor = RuntimeFunctor.builder()
                         .ambiance(ambiance)
                         .cdStepsExpressionResolver(mockResolver)
                         .connectorInputsMapper(mockConnectorMapper)
                         .build();

    Map<String, String> resolvedMap = new LinkedHashMap<>();
    resolvedMap.put("backend", "s3");
    resolvedMap.put("bucket", "test-bucket");
    resolvedMap.put("endpoint_url", null);
    resolvedMap.put("region", null);
    resolvedMap.put("account_name", null);
    resolvedMap.put("container_name", null);
    resolvedMap.put("connectorRef", null);
    when(mockResolver.updateExpressions(eq(ambiance), any(Map.class), eq(ExpressionMode.RETURN_NULL_IF_UNRESOLVED)))
        .thenReturn(resolvedMap);

    String result = runtimeFunctor.handleDlcCacheArgs();

    assertThat(result).isNotNull();
    assertThat(result).isEqualTo("type=s3,bucket=test-bucket");
    assertThat(result).doesNotContain("account_name");
    assertThat(result).doesNotContain("container_name");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testHandleDlcCacheArgsPartiallyResolvedConnectorFieldsAreOmitted() {
    CDStepsExpressionResolver mockResolver = mock(CDStepsExpressionResolver.class);
    ConnectorInputsMapper mockConnectorMapper = mock(ConnectorInputsMapper.class);
    runtimeFunctor = RuntimeFunctor.builder()
                         .ambiance(ambiance)
                         .cdStepsExpressionResolver(mockResolver)
                         .connectorInputsMapper(mockConnectorMapper)
                         .build();

    Map<String, String> resolvedMap = new LinkedHashMap<>();
    resolvedMap.put("backend", "s3");
    resolvedMap.put("bucket", "my-bucket");
    resolvedMap.put("endpoint_url", null);
    resolvedMap.put("region", "us-east-1");
    resolvedMap.put("account_name", null);
    resolvedMap.put("container_name", null);
    resolvedMap.put("connectorRef", "account.ciplay_aws");
    when(mockResolver.updateExpressions(eq(ambiance), any(Map.class), eq(ExpressionMode.RETURN_NULL_IF_UNRESOLVED)))
        .thenReturn(resolvedMap);

    Map<String, Object> connectorFields = new HashMap<>();
    connectorFields.put("accessKey", "AKIAEXAMPLE");
    connectorFields.put("secretKey", "secretExample");
    when(mockConnectorMapper.fetchConnectorFieldsDetails(
             eq("ciplay_aws"), eq("test-account"), eq(null), eq(null), eq(ambiance)))
        .thenReturn(connectorFields);

    String result = runtimeFunctor.handleDlcCacheArgs();

    assertThat(result).isNotNull();
    assertThat(result).contains("access_key_id=AKIAEXAMPLE");
    assertThat(result).contains("secret_access_key=secretExample");
    assertThat(result).doesNotContain("gcp_json_key");
    assertThat(result).doesNotContain("account_name");
    assertThat(result).doesNotContain("container_name");
    assertThat(result).doesNotContain("client_id");
    assertThat(result).doesNotContain("tenant_id");
    assertThat(result).doesNotContain("client_secret");
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void testHandleDlcCacheArgsAzureBackend() {
    CDStepsExpressionResolver mockResolver = mock(CDStepsExpressionResolver.class);
    ConnectorInputsMapper mockConnectorMapper = mock(ConnectorInputsMapper.class);
    runtimeFunctor = RuntimeFunctor.builder()
                         .ambiance(ambiance)
                         .cdStepsExpressionResolver(mockResolver)
                         .connectorInputsMapper(mockConnectorMapper)
                         .build();

    Map<String, String> resolvedMap = new LinkedHashMap<>();
    resolvedMap.put("backend", "azure");
    resolvedMap.put("bucket", null);
    resolvedMap.put("endpoint_url", null);
    resolvedMap.put("region", null);
    resolvedMap.put("account_name", "mystorageaccount");
    resolvedMap.put("container_name", "dlc-container");
    resolvedMap.put("connectorRef", "account.ciplay_azure");
    when(mockResolver.updateExpressions(eq(ambiance), any(Map.class), eq(ExpressionMode.RETURN_NULL_IF_UNRESOLVED)))
        .thenReturn(resolvedMap);

    Map<String, Object> connectorFields = new HashMap<>();
    connectorFields.put("clientId", "azure-client-id");
    connectorFields.put("tenantId", "azure-tenant-id");
    connectorFields.put("clientSecret", "azure-client-secret");
    when(mockConnectorMapper.fetchConnectorFieldsDetails(
             eq("ciplay_azure"), eq("test-account"), eq(null), eq(null), eq(ambiance)))
        .thenReturn(connectorFields);

    String result = runtimeFunctor.handleDlcCacheArgs();

    assertThat(result).isNotNull();
    assertThat(result).contains("type=azure");
    assertThat(result).contains("account_name=mystorageaccount");
    assertThat(result).contains("container_name=dlc-container");
    assertThat(result).contains("client_id=azure-client-id");
    assertThat(result).contains("tenant_id=azure-tenant-id");
    assertThat(result).contains("client_secret=azure-client-secret");
    assertThat(result).doesNotContain("bucket=");
    assertThat(result).doesNotContain("access_key_id");
    assertThat(result).doesNotContain("secret_access_key");
  }
}
