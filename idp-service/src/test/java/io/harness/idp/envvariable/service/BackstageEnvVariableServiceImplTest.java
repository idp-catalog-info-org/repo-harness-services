/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.envvariable.service;

import static io.harness.idp.common.Constants.LAST_UPDATED_TIMESTAMP;
import static io.harness.idp.k8s.constants.K8sConstants.BACKSTAGE_SECRET;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.KOTA_KARTHIK;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.mockito.Mockito.*;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptedSecretValue;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.app.IdpServiceRule;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.envvariable.beans.entity.BackstageEnvConfigVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvSecretVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity.BackstageEnvVariableMapper;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableType;
import io.harness.idp.envvariable.repositories.BackstageEnvVariableRepository;
import io.harness.idp.events.producers.SetupUsageProducer;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ngmanager.NgConnectorManagerClient;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.CGRestUtils;
import io.harness.rule.LifecycleRule;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;
import io.harness.spec.server.idp.v1.model.ResolvedEnvVariableResponse;

import com.google.inject.Inject;
import com.google.protobuf.StringValue;
import java.util.*;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class BackstageEnvVariableServiceImplTest extends CategoryTest {
  static final String TEST_SECRET_VALUE = "e55fa2b3e55fe55f";
  static final String TEST_DECRYPTED_VALUE = "abc123";
  static final String TEST_ENV_NAME = "TEST_ENV_NAME1";
  static final String TEST_ENV_NAME1 = "TEST_ENV_NAME2";
  static final String TEST_ENV_NAME2 = "TEST_ENV_NAME3";
  static final String TEST_ENV_NAME3 = "TEST_ENV_NAME4";
  static final String TEST_IDENTIFIER = "accountHarnessKey";
  static final String TEST_SECRET_IDENTIFIER = "accountHarnessKey";
  static final String TEST_SECRET_IDENTIFIER1 = "harnessKey";
  static final String TEST_ACCOUNT_IDENTIFIER = "accountId";
  static final String TEST_NAMESPACE = "namespace";
  static final String TEST_NAMESPACE1 = "namespace1";
  static final String HARNESS_GITHUB_APP_PRIVATE_KEY_REF = "HARNESS_GITHUB_APP_PRIVATE_KEY_REF";
  static final String HARNESS_GITHUB_TOKEN = "HARNESS_GITHUB_TOKEN";
  static final String SHARED_KEY = "abc123key";
  static final String PRIVATE_KEY_HEADER = "-----"
      + "BEGIN PRIVATE KEY"
      + "-----";
  static final String PRIVATE_KEY_FOOTER = "-----"
      + "END PRIVATE KEY"
      + "-----";
  AutoCloseable openMocks;
  @Mock private BackstageEnvVariableRepository backstageEnvVariableRepository;
  @Mock K8sClient k8sClient;
  @Mock NamespaceService namespaceService;
  @Mock SecretManagerClientService ngSecretService;
  @Mock SetupUsageProducer setupUsageProducer;
  @Rule public LifecycleRule lifecycleRule = new LifecycleRule();
  @Rule public IdpServiceRule apiServiceRule = new IdpServiceRule(lifecycleRule.getClosingFactory());
  @Inject private Map<BackstageEnvVariableType, BackstageEnvVariableMapper> mapBinder;
  @InjectMocks IdpCommonService idpCommonService;
  @Mock NgConnectorManagerClient ngConnectorManagerClient;
  private BackstageEnvVariableServiceImpl backstageEnvVariableService;
  private static final String ADMIN_USER_ID = "lv0euRhKRCyiXWzS7pOg6g";
  private static final String ACCOUNT_ID = "123";
  @Captor private ArgumentCaptor<Map<String, byte[]>> secretDataCaptor;

  @Mock private TransactionTemplate transactionTemplate;
  @Mock private OutboxService outboxService;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    backstageEnvVariableService =
        new BackstageEnvVariableServiceImpl(backstageEnvVariableRepository, k8sClient, ngSecretService,
            namespaceService, mapBinder, setupUsageProducer, SHARED_KEY, transactionTemplate, outboxService);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFindByIdAndAccountIdentifier() {
    BackstageEnvSecretVariableEntity envVariableEntity = BackstageEnvSecretVariableEntity.builder().build();
    when(backstageEnvVariableRepository.findByIdAndAccountIdentifier(TEST_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(envVariableEntity));
    Optional<BackstageEnvVariable> envVariableOpt =
        backstageEnvVariableService.findByIdAndAccountIdentifier(TEST_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    assertTrue(envVariableOpt.isPresent());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFindByIdAndAccountIdentifierNotPresent() {
    Optional<BackstageEnvVariable> envVariableOpt =
        backstageEnvVariableService.findByIdAndAccountIdentifier(TEST_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    assertTrue(envVariableOpt.isEmpty());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFindByEnvNameAndAccountIdentifier() {
    BackstageEnvSecretVariableEntity envVariableEntity = BackstageEnvSecretVariableEntity.builder().build();
    when(backstageEnvVariableRepository.findByEnvNameAndAccountIdentifier(TEST_ENV_NAME, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(envVariableEntity));
    Optional<BackstageEnvVariable> envVariableOpt =
        backstageEnvVariableService.findByEnvNameAndAccountIdentifier(TEST_ENV_NAME, TEST_ACCOUNT_IDENTIFIER);
    assertTrue(envVariableOpt.isPresent());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFindByEnvNameAndAccountIdentifierNotPresent() {
    Optional<BackstageEnvVariable> envVariableOpt =
        backstageEnvVariableService.findByEnvNameAndAccountIdentifier(TEST_ENV_NAME, TEST_ACCOUNT_IDENTIFIER);
    assertTrue(envVariableOpt.isEmpty());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreate() {
    checkUserAuth();
    mockAccountNamespaceMapping();
    BackstageEnvSecretVariable envVariable = new BackstageEnvSecretVariable();
    envVariable.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariable.envName(TEST_ENV_NAME);
    envVariable.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvVariableEntity envVariableEntity =
        mapBinder.get(BackstageEnvVariableType.SECRET).fromDto(envVariable, TEST_ACCOUNT_IDENTIFIER, 0L);
    when(backstageEnvVariableRepository.save(envVariableEntity)).thenReturn(envVariableEntity);
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);

    assertEquals(envVariable, backstageEnvVariableService.create(envVariable, TEST_ACCOUNT_IDENTIFIER));

    verify(k8sClient).updateSecretData(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap());
    verify(setupUsageProducer)
        .publishEnvVariableSetupUsage(Collections.singletonList(envVariable), TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateMulti() {
    long secretLastModifiedAt = System.currentTimeMillis();
    checkUserAuth();
    mockAccountNamespaceMapping();
    BackstageEnvSecretVariable envVariable1 = new BackstageEnvSecretVariable();
    envVariable1.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariable1.envName(TEST_ENV_NAME1);
    envVariable1.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvSecretVariable envVariable2 = new BackstageEnvSecretVariable();
    envVariable2.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariable2.envName(TEST_ENV_NAME2);
    envVariable2.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvVariableEntity envVariableEntity1 =
        mapBinder.get(BackstageEnvVariableType.SECRET)
            .fromDto(envVariable1, TEST_ACCOUNT_IDENTIFIER, secretLastModifiedAt);
    BackstageEnvVariableEntity envVariableEntity2 =
        mapBinder.get(BackstageEnvVariableType.SECRET)
            .fromDto(envVariable2, TEST_ACCOUNT_IDENTIFIER, secretLastModifiedAt);
    List<BackstageEnvVariableEntity> entities = Arrays.asList(envVariableEntity1, envVariableEntity2);
    when(backstageEnvVariableRepository.saveAll(entities)).thenReturn(entities);
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    decryptedSecretValue.setLastModifiedAt(secretLastModifiedAt);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);

    List<BackstageEnvVariable> responseVariables =
        backstageEnvVariableService.createMulti(Arrays.asList(envVariable1, envVariable2), TEST_ACCOUNT_IDENTIFIER);

    assertEquals(envVariable1, responseVariables.get(0));
    assertEquals(envVariable2, responseVariables.get(1));
    verify(k8sClient).updateSecretData(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap());
    verify(setupUsageProducer)
        .publishEnvVariableSetupUsage(Arrays.asList(envVariable1, envVariable2), TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdate() {
    long secretLastModifiedAt = System.currentTimeMillis();
    checkUserAuth();
    mockAccountNamespaceMapping();
    BackstageEnvSecretVariable envVariable = new BackstageEnvSecretVariable();
    envVariable.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariable.envName(TEST_ENV_NAME);
    envVariable.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvVariableEntity envVariableEntity =
        mapBinder.get(BackstageEnvVariableType.SECRET)
            .fromDto(envVariable, TEST_ACCOUNT_IDENTIFIER, secretLastModifiedAt);
    when(backstageEnvVariableRepository.update(envVariableEntity)).thenReturn(envVariableEntity);
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    decryptedSecretValue.setLastModifiedAt(secretLastModifiedAt);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);

    assertEquals(envVariable, backstageEnvVariableService.update(envVariable, TEST_ACCOUNT_IDENTIFIER));

    verify(k8sClient).updateSecretData(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap());
    verify(setupUsageProducer)
        .deleteEnvVariableSetupUsage(Collections.singletonList(envVariable), TEST_ACCOUNT_IDENTIFIER);
    verify(setupUsageProducer)
        .publishEnvVariableSetupUsage(Collections.singletonList(envVariable), TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdateMulti() {
    long secretLastModifiedAt = System.currentTimeMillis();
    checkUserAuth();
    mockAccountNamespaceMapping();
    BackstageEnvSecretVariable envVariable1 = new BackstageEnvSecretVariable();
    envVariable1.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariable1.envName(TEST_ENV_NAME1);
    envVariable1.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvSecretVariable envVariable2 = new BackstageEnvSecretVariable();
    envVariable2.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariable2.envName(TEST_ENV_NAME2);
    envVariable2.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvVariableEntity envVariableEntity1 =
        mapBinder.get(BackstageEnvVariableType.SECRET)
            .fromDto(envVariable1, TEST_ACCOUNT_IDENTIFIER, secretLastModifiedAt);
    BackstageEnvVariableEntity envVariableEntity2 =
        mapBinder.get(BackstageEnvVariableType.SECRET)
            .fromDto(envVariable2, TEST_ACCOUNT_IDENTIFIER, secretLastModifiedAt);
    when(backstageEnvVariableRepository.update(envVariableEntity1)).thenReturn(envVariableEntity1);
    when(backstageEnvVariableRepository.update(envVariableEntity2)).thenReturn(envVariableEntity2);
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    decryptedSecretValue.setLastModifiedAt(secretLastModifiedAt);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);

    List<BackstageEnvVariable> responseVariables =
        backstageEnvVariableService.updateMulti(Arrays.asList(envVariable1, envVariable2), TEST_ACCOUNT_IDENTIFIER);

    assertEquals(envVariable1, responseVariables.get(0));
    assertEquals(envVariable2, responseVariables.get(1));
    verify(k8sClient).updateSecretData(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap());
    verify(setupUsageProducer)
        .deleteEnvVariableSetupUsage(Arrays.asList(envVariable1, envVariable2), TEST_ACCOUNT_IDENTIFIER);
    verify(setupUsageProducer)
        .publishEnvVariableSetupUsage(Arrays.asList(envVariable1, envVariable2), TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateOrUpdate() {
    long secretLastModifiedAt = System.currentTimeMillis();
    checkUserAuth();
    mockAccountNamespaceMapping();

    BackstageEnvSecretVariable envVariableToAdd1 = new BackstageEnvSecretVariable();
    envVariableToAdd1.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariableToAdd1.envName(TEST_ENV_NAME);
    envVariableToAdd1.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvSecretVariable envVariableToAdd2 = new BackstageEnvSecretVariable();
    envVariableToAdd2.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariableToAdd2.envName(TEST_ENV_NAME1);
    envVariableToAdd2.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvVariableEntity envVariableEntityToAdd1 =
        mapBinder.get(BackstageEnvVariableType.SECRET)
            .fromDto(envVariableToAdd1, TEST_ACCOUNT_IDENTIFIER, secretLastModifiedAt);
    BackstageEnvVariableEntity envVariableEntityToAdd2 =
        mapBinder.get(BackstageEnvVariableType.SECRET)
            .fromDto(envVariableToAdd2, TEST_ACCOUNT_IDENTIFIER, secretLastModifiedAt);
    List<BackstageEnvVariableEntity> entities = Arrays.asList(envVariableEntityToAdd1, envVariableEntityToAdd2);
    when(backstageEnvVariableRepository.saveAll(entities)).thenReturn(entities);
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    decryptedSecretValue.setLastModifiedAt(secretLastModifiedAt);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);

    BackstageEnvSecretVariable envVariableToUpdate1 = new BackstageEnvSecretVariable();
    envVariableToUpdate1.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER1);
    envVariableToUpdate1.envName(TEST_ENV_NAME2);
    envVariableToUpdate1.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvSecretVariable envVariableToUpdate2 = new BackstageEnvSecretVariable();
    envVariableToUpdate2.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER1);
    envVariableToUpdate2.envName(TEST_ENV_NAME3);
    envVariableToUpdate2.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvSecretVariableEntity envVariableEntityToUpdate1 =
        (BackstageEnvSecretVariableEntity) mapBinder.get(BackstageEnvVariableType.SECRET)
            .fromDto(envVariableToUpdate1, TEST_ACCOUNT_IDENTIFIER, secretLastModifiedAt);
    BackstageEnvSecretVariableEntity envVariableEntityToUpdate2 =
        (BackstageEnvSecretVariableEntity) mapBinder.get(BackstageEnvVariableType.SECRET)
            .fromDto(envVariableToUpdate2, TEST_ACCOUNT_IDENTIFIER, secretLastModifiedAt);
    when(backstageEnvVariableRepository.findAllByAccountIdentifierAndMultipleEnvNames(
             TEST_ACCOUNT_IDENTIFIER, Arrays.asList(TEST_ENV_NAME, TEST_ENV_NAME2, TEST_ENV_NAME1, TEST_ENV_NAME3)))
        .thenReturn(Arrays.asList(envVariableEntityToUpdate1, envVariableEntityToUpdate2));
    envVariableEntityToUpdate1.setSecretLastModifiedAt(secretLastModifiedAt + 1);
    envVariableEntityToUpdate2.setSecretLastModifiedAt(secretLastModifiedAt + 1);
    when(backstageEnvVariableRepository.update(any()))
        .thenReturn(envVariableEntityToUpdate1)
        .thenReturn(envVariableEntityToUpdate2);
    decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    decryptedSecretValue.setLastModifiedAt(secretLastModifiedAt + 1);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER1))
        .thenReturn(decryptedSecretValue);

    List<BackstageEnvVariable> responseVariables = backstageEnvVariableService.createOrUpdate(
        Arrays.asList(envVariableToAdd1, envVariableToUpdate1, envVariableToAdd2, envVariableToUpdate2),
        TEST_ACCOUNT_IDENTIFIER);

    assertEquals(envVariableToAdd1, responseVariables.get(0));
    assertEquals(envVariableToAdd2, responseVariables.get(1));
    assertEquals(envVariableToUpdate1, responseVariables.get(2));
    assertEquals(envVariableToUpdate2, responseVariables.get(3));
    verify(k8sClient, times(2))
        .updateSecretData(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap());
    verify(setupUsageProducer)
        .deleteEnvVariableSetupUsage(
            Arrays.asList(envVariableToUpdate1, envVariableToUpdate1), TEST_ACCOUNT_IDENTIFIER);
    verify(setupUsageProducer)
        .publishEnvVariableSetupUsage(
            Arrays.asList(envVariableToAdd1, envVariableToAdd2, envVariableToUpdate1, envVariableToUpdate2),
            TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifier() {
    BackstageEnvSecretVariableEntity envVariableEntity = BackstageEnvSecretVariableEntity.builder().build();
    envVariableEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    when(backstageEnvVariableRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Collections.singletonList(envVariableEntity));
    List<BackstageEnvVariable> variables = backstageEnvVariableService.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(1, variables.size());
    assertEquals(envVariableEntity,
        mapBinder.get(BackstageEnvVariableType.SECRET).fromDto(variables.get(0), TEST_ACCOUNT_IDENTIFIER, 0L));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteMulti() {
    checkUserAuth();
    mockAccountNamespaceMapping();
    BackstageEnvSecretVariableEntity backstageEnvVariableEntity1 = BackstageEnvSecretVariableEntity.builder().build();
    backstageEnvVariableEntity1.setHarnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    BackstageEnvSecretVariableEntity backstageEnvVariableEntity2 = BackstageEnvSecretVariableEntity.builder().build();
    backstageEnvVariableEntity2.setHarnessSecretIdentifier(TEST_SECRET_IDENTIFIER1);
    List<BackstageEnvVariableEntity> secrets = Arrays.asList(backstageEnvVariableEntity1, backstageEnvVariableEntity2);
    List<String> variableIds = Arrays.asList(TEST_SECRET_IDENTIFIER, TEST_SECRET_IDENTIFIER1);
    when(backstageEnvVariableRepository.findAllById(variableIds)).thenReturn(secrets);
    backstageEnvVariableService.deleteMulti(variableIds, TEST_ACCOUNT_IDENTIFIER);
    verify(k8sClient).updateSecretData(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap());
    verify(backstageEnvVariableRepository).deleteAllById(variableIds);
    verify(setupUsageProducer).deleteEnvVariableSetupUsage(anyList(), eq(TEST_ACCOUNT_IDENTIFIER));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessSecretUpdate() {
    long secretLastModifiedAt = System.currentTimeMillis();
    mockAccountNamespaceMapping();
    BackstageEnvSecretVariable envVariable = new BackstageEnvSecretVariable();
    envVariable.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariable.envName(TEST_ENV_NAME);
    envVariable.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvVariableEntity envVariableEntity =
        mapBinder.get(BackstageEnvVariableType.SECRET)
            .fromDto(envVariable, TEST_ACCOUNT_IDENTIFIER, secretLastModifiedAt);
    when(backstageEnvVariableRepository.findByAccountIdentifierAndHarnessSecretIdentifier(
             TEST_ACCOUNT_IDENTIFIER, TEST_SECRET_IDENTIFIER))
        .thenReturn(Optional.of(envVariableEntity));
    EntityChangeDTO entityChangeDTO = EntityChangeDTO.newBuilder()
                                          .setAccountIdentifier(StringValue.of(TEST_ACCOUNT_IDENTIFIER))
                                          .setIdentifier(StringValue.of(TEST_SECRET_IDENTIFIER))
                                          .build();
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    decryptedSecretValue.setLastModifiedAt(secretLastModifiedAt + 1);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);

    backstageEnvVariableService.processSecretUpdate(entityChangeDTO);

    verify(k8sClient).updateSecretData(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessSecretUpdateNonIdpSecret() {
    BackstageEnvSecretVariable envVariable = new BackstageEnvSecretVariable();
    envVariable.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariable.envName(TEST_ENV_NAME);
    envVariable.type(BackstageEnvVariable.TypeEnum.SECRET);
    EntityChangeDTO entityChangeDTO = EntityChangeDTO.newBuilder()
                                          .setAccountIdentifier(StringValue.of(TEST_ACCOUNT_IDENTIFIER))
                                          .setIdentifier(StringValue.of(TEST_SECRET_IDENTIFIER))
                                          .build();
    backstageEnvVariableService.processSecretUpdate(entityChangeDTO);
    verify(k8sClient, times(0))
        .updateSecretData(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessSecretDelete() {
    BackstageEnvVariableEntity envVariable =
        BackstageEnvSecretVariableEntity.builder().harnessSecretIdentifier(TEST_SECRET_IDENTIFIER).build();
    envVariable.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    envVariable.setEnvName(TEST_ENV_NAME);
    EntityChangeDTO entityChangeDTO = EntityChangeDTO.newBuilder()
                                          .setAccountIdentifier(StringValue.of(TEST_ACCOUNT_IDENTIFIER))
                                          .setIdentifier(StringValue.of(TEST_SECRET_IDENTIFIER))
                                          .build();

    when(backstageEnvVariableRepository.updateSecretIsDeleted(TEST_ACCOUNT_IDENTIFIER, TEST_SECRET_IDENTIFIER, true))
        .thenReturn(Optional.of(envVariable));

    backstageEnvVariableService.processSecretDelete(entityChangeDTO);

    verify(backstageEnvVariableRepository).updateSecretIsDeleted(TEST_ACCOUNT_IDENTIFIER, TEST_SECRET_IDENTIFIER, true);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testSyncConfigValue() {
    checkUserAuth();
    mockAccountNamespaceMapping();
    BackstageEnvConfigVariable envVariable1 = new BackstageEnvConfigVariable();
    envVariable1.envName(TEST_ENV_NAME);
    envVariable1.setValue(TEST_DECRYPTED_VALUE);
    envVariable1.type(BackstageEnvVariable.TypeEnum.CONFIG);

    backstageEnvVariableService.sync(Collections.singletonList(envVariable1), TEST_ACCOUNT_IDENTIFIER);

    verify(k8sClient).updateSecretData(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFindAndSync() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    checkUserAuth();
    mockAccountNamespaceMapping();

    BackstageEnvConfigVariable config = new BackstageEnvConfigVariable();
    config.envName(TEST_ENV_NAME);
    config.setValue(TEST_DECRYPTED_VALUE);
    config.type(BackstageEnvVariable.TypeEnum.CONFIG);
    BackstageEnvVariableEntity configEntity =
        mapBinder.get(BackstageEnvVariableType.CONFIG).fromDto(config, TEST_ACCOUNT_IDENTIFIER, 0L);

    BackstageEnvSecretVariable secret = new BackstageEnvSecretVariable();
    secret.envName(HARNESS_GITHUB_TOKEN);
    secret.setHarnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    secret.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvVariableEntity secretEntity =
        mapBinder.get(BackstageEnvVariableType.SECRET).fromDto(secret, TEST_ACCOUNT_IDENTIFIER, 0L);

    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_DECRYPTED_VALUE);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);
    SecretResponseWrapper secretResponseWrapper =
        SecretResponseWrapper.builder()
            .secret(SecretDTOV2.builder().type(SecretType.SecretFile).identifier(TEST_SECRET_IDENTIFIER).build())
            .build();
    when(ngSecretService.getSecret(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(secretResponseWrapper);
    when(backstageEnvVariableRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Collections.emptyList())
        .thenReturn(Arrays.asList(configEntity, secretEntity));

    backstageEnvVariableService.findAndSync(TEST_ACCOUNT_IDENTIFIER);
    verify(k8sClient, times(0))
        .updateSecretData(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap());

    backstageEnvVariableService.findAndSync(TEST_ACCOUNT_IDENTIFIER);
    verify(k8sClient).updateSecretData(
        eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), secretDataCaptor.capture());
    assertTrue(secretDataCaptor.getValue().containsKey(LAST_UPDATED_TIMESTAMP));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testSyncWithFFEnabled() {
    checkUserAuth();
    mockAccountNamespaceMapping();
    BackstageEnvSecretVariable secret = new BackstageEnvSecretVariable();
    secret.envName(HARNESS_GITHUB_APP_PRIVATE_KEY_REF);
    secret.setHarnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    secret.type(BackstageEnvVariable.TypeEnum.SECRET);

    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_DECRYPTED_VALUE);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);
    SecretResponseWrapper secretResponseWrapper =
        SecretResponseWrapper.builder()
            .secret(SecretDTOV2.builder().type(SecretType.SecretText).identifier(TEST_SECRET_IDENTIFIER).build())
            .build();
    when(ngSecretService.getSecret(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(secretResponseWrapper);

    backstageEnvVariableService.sync(Collections.singletonList(secret), TEST_ACCOUNT_IDENTIFIER);
    verify(k8sClient).updateSecretData(
        eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), secretDataCaptor.capture());
    assertEquals(1, secretDataCaptor.getValue().size());
    assertTrue(secretDataCaptor.getValue().containsKey(LAST_UPDATED_TIMESTAMP));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteMultiUsingEnvNames() {
    List<String> envNames = Collections.singletonList(TEST_ENV_NAME);
    NamespaceInfo namespaceInfo = new NamespaceInfo();
    namespaceInfo.setNamespace(TEST_NAMESPACE);
    when(namespaceService.getNamespaceForAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(namespaceInfo);

    backstageEnvVariableService.deleteMultiUsingEnvNames(envNames, TEST_ACCOUNT_IDENTIFIER);

    verify(k8sClient).updateSecretData(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap());
    verify(backstageEnvVariableRepository).deleteAllByAccountIdentifierAndEnvNames(TEST_ACCOUNT_IDENTIFIER, envNames);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFindByEnvNamesAndAccountIdentifier() {
    List<String> envNames = Collections.singletonList(TEST_ENV_NAME);
    BackstageEnvSecretVariableEntity secretEntity =
        BackstageEnvSecretVariableEntity.builder().harnessSecretIdentifier(TEST_SECRET_IDENTIFIER).build();
    secretEntity.setHarnessSecretIdentifier(TEST_ACCOUNT_IDENTIFIER);
    secretEntity.setEnvName(TEST_ENV_NAME);

    List<BackstageEnvVariableEntity> secretEntities = Collections.singletonList(secretEntity);
    when(
        backstageEnvVariableRepository.findAllByAccountIdentifierAndMultipleEnvNames(TEST_ACCOUNT_IDENTIFIER, envNames))
        .thenReturn(secretEntities);

    List<BackstageEnvVariable> secrets =
        backstageEnvVariableService.findByEnvNamesAndAccountIdentifier(envNames, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(1, secrets.size());
    assertEquals(TEST_ENV_NAME, secrets.get(0).getEnvName());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetAllSecretIdentifierForMultipleEnvVariablesInAccount() {
    List<String> envNames = Collections.singletonList(TEST_ENV_NAME);
    BackstageEnvSecretVariableEntity secretEntity =
        BackstageEnvSecretVariableEntity.builder().harnessSecretIdentifier(TEST_SECRET_IDENTIFIER).build();
    secretEntity.setHarnessSecretIdentifier(TEST_ACCOUNT_IDENTIFIER);
    secretEntity.setEnvName(TEST_ENV_NAME);
    List<BackstageEnvVariableEntity> secretEntities = Collections.singletonList(secretEntity);
    when(
        backstageEnvVariableRepository.findAllByAccountIdentifierAndMultipleEnvNames(TEST_ACCOUNT_IDENTIFIER, envNames))
        .thenReturn(secretEntities);

    List<BackstageEnvSecretVariable> secrets =
        backstageEnvVariableService.getAllSecretIdentifierForMultipleEnvVariablesInAccount(
            TEST_ACCOUNT_IDENTIFIER, envNames);
    assertEquals(1, secrets.size());
    assertEquals(TEST_ENV_NAME, secrets.get(0).getEnvName());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testResolveSecrets() {
    mockAccountNamespaceMapping();
    BackstageEnvConfigVariable config = new BackstageEnvConfigVariable();
    config.envName(TEST_ENV_NAME);
    config.setValue(TEST_DECRYPTED_VALUE);
    config.type(BackstageEnvVariable.TypeEnum.CONFIG);
    BackstageEnvVariableEntity configEntity =
        mapBinder.get(BackstageEnvVariableType.CONFIG).fromDto(config, TEST_ACCOUNT_IDENTIFIER, 0L);

    BackstageEnvSecretVariable secret = new BackstageEnvSecretVariable();
    secret.envName(TEST_ENV_NAME2);
    secret.setHarnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    secret.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvVariableEntity secretEntity =
        mapBinder.get(BackstageEnvVariableType.SECRET).fromDto(secret, TEST_ACCOUNT_IDENTIFIER, 0L);

    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_DECRYPTED_VALUE);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);
    when(backstageEnvVariableRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Arrays.asList(configEntity, secretEntity));

    ResolvedEnvVariableResponse response =
        backstageEnvVariableService.resolveEnvVariables(TEST_ACCOUNT_IDENTIFIER, TEST_NAMESPACE);

    assertNotNull(response.getResolvedEnvVariables());
    // TODO: Need to update this to decrypt the value and test whether required envs are retured with their values.
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testResolveSecretsRetry() {
    mockAccountNamespaceMapping();

    BackstageEnvSecretVariable secret = new BackstageEnvSecretVariable();
    secret.envName(TEST_ENV_NAME2);
    secret.setHarnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    secret.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvVariableEntity secretEntity =
        mapBinder.get(BackstageEnvVariableType.SECRET).fromDto(secret, TEST_ACCOUNT_IDENTIFIER, 0L);

    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_DECRYPTED_VALUE);
    InvalidRequestException exception = new InvalidRequestException(String.format(
        "Invalid request: Secret with identifier %s does not exist in this scope", TEST_SECRET_IDENTIFIER));
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenThrow(exception)
        .thenThrow(exception)
        .thenReturn(decryptedSecretValue);
    when(backstageEnvVariableRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Collections.singletonList(secretEntity));

    ResolvedEnvVariableResponse response =
        backstageEnvVariableService.resolveEnvVariables(TEST_ACCOUNT_IDENTIFIER, TEST_NAMESPACE);

    assertNotNull(response.getResolvedEnvVariables());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testResolveSecretsInvalidNamespace() {
    mockAccountNamespaceMapping();
    backstageEnvVariableService.resolveEnvVariables(TEST_ACCOUNT_IDENTIFIER, TEST_NAMESPACE1);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testUpdateAndAuditEnvironmentVariables() {
    long secretLastModifiedAt = System.currentTimeMillis();
    checkUserAuth();
    mockAccountNamespaceMapping();

    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    BackstageEnvSecretVariable envVariable1 = new BackstageEnvSecretVariable();
    envVariable1.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariable1.envName(TEST_ENV_NAME);
    envVariable1.type(BackstageEnvVariable.TypeEnum.SECRET);

    BackstageEnvVariableEntity envVariableEntity1 =
        mapBinder.get(BackstageEnvVariableType.SECRET)
            .fromDto(envVariable1, TEST_ACCOUNT_IDENTIFIER, secretLastModifiedAt);

    when(backstageEnvVariableRepository.update(envVariableEntity1)).thenReturn(envVariableEntity1);
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    decryptedSecretValue.setLastModifiedAt(secretLastModifiedAt);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);

    when(backstageEnvVariableRepository.findAllByAccountIdentifierAndMultipleEnvNames(any(), any()))
        .thenReturn(Arrays.asList(envVariableEntity1));

    List<BackstageEnvVariable> responseVariables = backstageEnvVariableService.updateAndAuditEnvironmentVariables(
        Arrays.asList(envVariable1), TEST_ACCOUNT_IDENTIFIER);

    assertEquals(responseVariables.size(), 1);
    assertEquals(responseVariables.get(0).getEnvName(), TEST_ENV_NAME);

    BackstageEnvConfigVariable config = new BackstageEnvConfigVariable();
    config.envName(TEST_ENV_NAME3);
    config.setValue(TEST_DECRYPTED_VALUE);
    config.type(BackstageEnvVariable.TypeEnum.CONFIG);

    Exception exception = null;
    try {
      backstageEnvVariableService.updateAndAuditEnvironmentVariables(
          Arrays.asList(envVariable1, config), TEST_ACCOUNT_IDENTIFIER);
    } catch (InvalidRequestException e) {
      exception = e;
    }
    assertNotNull(exception);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testReloadSecrets() {
    mockAccountNamespaceMapping();

    backstageEnvVariableService.reloadSecrets(TEST_ACCOUNT_IDENTIFIER, TEST_NAMESPACE);

    verify(k8sClient).updateSecretData(
        eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), secretDataCaptor.capture());
    assertTrue(secretDataCaptor.getValue().containsKey(LAST_UPDATED_TIMESTAMP));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testReloadSecretsInvalidNamespace() {
    mockAccountNamespaceMapping();
    backstageEnvVariableService.reloadSecrets(TEST_ACCOUNT_IDENTIFIER, TEST_NAMESPACE1);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCleanupEnvSecret() {
    BackstageEnvConfigVariableEntity configEntity =
        BackstageEnvConfigVariableEntity.builder().value(TEST_DECRYPTED_VALUE).build();
    configEntity.setEnvName(TEST_ENV_NAME);
    configEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);

    BackstageEnvSecretVariableEntity secretEntity =
        BackstageEnvSecretVariableEntity.builder().harnessSecretIdentifier(TEST_SECRET_IDENTIFIER).build();
    secretEntity.setEnvName(TEST_ENV_NAME1);
    secretEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);

    when(backstageEnvVariableRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Arrays.asList(configEntity, secretEntity));

    backstageEnvVariableService.cleanupEnvSecret(TEST_ACCOUNT_IDENTIFIER, TEST_NAMESPACE);

    ArgumentCaptor<List<String>> envNamesCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sClient).removeSecretData(eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), envNamesCaptor.capture());

    List<String> capturedEnvNames = envNamesCaptor.getValue();
    assertTrue(capturedEnvNames.contains(TEST_ENV_NAME));
    assertTrue(capturedEnvNames.contains("LAST_UPDATED_TIMESTAMP_FOR_ENV_VARIABLES"));
    assertTrue(capturedEnvNames.contains("LAST_UPDATED_TIMESTAMP_FOR_PLUGIN_WITH_NO_CONFIG"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCleanupEnvSecretWithNoConfigVariables() {
    BackstageEnvSecretVariableEntity secretEntity =
        BackstageEnvSecretVariableEntity.builder().harnessSecretIdentifier(TEST_SECRET_IDENTIFIER).build();
    secretEntity.setEnvName(TEST_ENV_NAME);
    secretEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);

    when(backstageEnvVariableRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Collections.singletonList(secretEntity));

    backstageEnvVariableService.cleanupEnvSecret(TEST_ACCOUNT_IDENTIFIER, TEST_NAMESPACE);

    ArgumentCaptor<List<String>> envNamesCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sClient).removeSecretData(eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), envNamesCaptor.capture());

    List<String> capturedEnvNames = envNamesCaptor.getValue();
    assertEquals(2, capturedEnvNames.size());
    assertTrue(capturedEnvNames.contains("LAST_UPDATED_TIMESTAMP_FOR_ENV_VARIABLES"));
    assertTrue(capturedEnvNames.contains("LAST_UPDATED_TIMESTAMP_FOR_PLUGIN_WITH_NO_CONFIG"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetDecryptedValueAndLastModifiedTimeUnsupportedSecretManager() {
    InvalidRequestException exception = new InvalidRequestException(
        "Invalid request: Decryption is supported only for secrets encrypted via harness managed secret managers");
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenThrow(exception);

    org.apache.commons.math3.util.Pair<String, Long> result =
        backstageEnvVariableService.getDecryptedValueAndLastModifiedTime(
            TEST_ENV_NAME, TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER, null, null);

    assertNotNull(result);
    assertEquals(String.format("<+secrets.getValue('account.%s')>", TEST_SECRET_IDENTIFIER), result.getFirst());
    assertEquals(Long.valueOf(0L), result.getSecond());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetDecryptedValueAndLastModifiedTimeUnsupportedSecretManagerForGitHubAppPrivateKey() {
    InvalidRequestException exception = new InvalidRequestException(
        "Invalid request: Decryption is supported only for secrets encrypted via harness managed secret managers");
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenThrow(exception);

    org.apache.commons.math3.util.Pair<String, Long> result =
        backstageEnvVariableService.getDecryptedValueAndLastModifiedTime(
            HARNESS_GITHUB_APP_PRIVATE_KEY_REF, TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER, null, null);

    assertNotNull(result);
    assertTrue(result.getFirst().contains(PRIVATE_KEY_HEADER));
    assertEquals(Long.valueOf(0L), result.getSecond());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetDecryptedValueAndLastModifiedTimeForGitHubApiAppPrivateKey() {
    InvalidRequestException exception = new InvalidRequestException(
        "Invalid request: Decryption is supported only for secrets encrypted via harness managed secret managers");
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenThrow(exception);

    org.apache.commons.math3.util.Pair<String, Long> result =
        backstageEnvVariableService.getDecryptedValueAndLastModifiedTime(
            "GITHUB_API_APP_PRIVATE_KEY", TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER, null, null);

    assertNotNull(result);
    assertTrue(result.getFirst().contains(PRIVATE_KEY_HEADER));
    assertEquals(Long.valueOf(0L), result.getSecond());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetDecryptedValueAndLastModifiedTimeForIntegrationsGitHubAppPrivateKey() {
    InvalidRequestException exception = new InvalidRequestException(
        "Invalid request: Decryption is supported only for secrets encrypted via harness managed secret managers");
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenThrow(exception);

    org.apache.commons.math3.util.Pair<String, Long> result =
        backstageEnvVariableService.getDecryptedValueAndLastModifiedTime(
            "INTEGRATIONS_GITHUB_APP_PRIVATE_KEY_test", TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER, null, null);

    assertNotNull(result);
    assertTrue(result.getFirst().contains(PRIVATE_KEY_HEADER));
    assertEquals(Long.valueOf(0L), result.getSecond());
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetDecryptedValueAndLastModifiedTimeSecretNotFound() {
    InvalidRequestException exception =
        new InvalidRequestException("Invalid request: Secret with identifier testSecret does not exist in this scope");
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenThrow(exception);

    backstageEnvVariableService.getDecryptedValueAndLastModifiedTime(
        TEST_ENV_NAME, TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER, null, null);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetDecryptedValueForGitHubAppPrivateKeySecretFile() {
    mockAccountNamespaceMapping();
    String base64EncodedKey = java.util.Base64.getEncoder().encodeToString("test-private-key".getBytes());
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(base64EncodedKey);
    decryptedSecretValue.setLastModifiedAt(1000L);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);
    SecretResponseWrapper secretResponseWrapper =
        SecretResponseWrapper.builder()
            .secret(SecretDTOV2.builder().type(SecretType.SecretFile).identifier(TEST_SECRET_IDENTIFIER).build())
            .build();
    when(ngSecretService.getSecret(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(secretResponseWrapper);

    org.apache.commons.math3.util.Pair<String, Long> result =
        backstageEnvVariableService.getDecryptedValueAndLastModifiedTime(
            HARNESS_GITHUB_APP_PRIVATE_KEY_REF, TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER, null, null);

    assertNotNull(result);
    assertEquals("test-private-key", result.getFirst());
    assertEquals(Long.valueOf(1000L), result.getSecond());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetDecryptedValueForGitHubAppPrivateKeySecretText() {
    mockAccountNamespaceMapping();
    String privateKey = PRIVATE_KEY_HEADER + " MIIE " + PRIVATE_KEY_FOOTER;
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(privateKey);
    decryptedSecretValue.setLastModifiedAt(1000L);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);
    SecretResponseWrapper secretResponseWrapper =
        SecretResponseWrapper.builder()
            .secret(SecretDTOV2.builder().type(SecretType.SecretText).identifier(TEST_SECRET_IDENTIFIER).build())
            .build();
    when(ngSecretService.getSecret(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(secretResponseWrapper);

    org.apache.commons.math3.util.Pair<String, Long> result =
        backstageEnvVariableService.getDecryptedValueAndLastModifiedTime(
            HARNESS_GITHUB_APP_PRIVATE_KEY_REF, TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER, null, null);

    assertNotNull(result);
    assertTrue(result.getFirst().contains(PRIVATE_KEY_HEADER));
    assertTrue(result.getFirst().contains(PRIVATE_KEY_FOOTER));
    assertEquals(Long.valueOf(1000L), result.getSecond());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSyncEmptyList() {
    backstageEnvVariableService.sync(Collections.emptyList(), TEST_ACCOUNT_IDENTIFIER);
    verify(k8sClient, never()).updateSecretData(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSyncWithExistingSecretSameValue() {
    mockAccountNamespaceMapping();
    long secretLastModifiedAt = System.currentTimeMillis();

    BackstageEnvSecretVariable secret = new BackstageEnvSecretVariable();
    secret.envName(TEST_ENV_NAME);
    secret.setHarnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    secret.type(BackstageEnvVariable.TypeEnum.SECRET);

    BackstageEnvSecretVariableEntity existingEntity = BackstageEnvSecretVariableEntity.builder()
                                                          .harnessSecretIdentifier(TEST_SECRET_IDENTIFIER)
                                                          .secretLastModifiedAt(secretLastModifiedAt)
                                                          .build();
    existingEntity.setEnvName(TEST_ENV_NAME);
    existingEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);

    when(backstageEnvVariableRepository.findAllByAccountIdentifierAndMultipleEnvNames(
             TEST_ACCOUNT_IDENTIFIER, Collections.singletonList(TEST_ENV_NAME)))
        .thenReturn(Collections.singletonList(existingEntity));

    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_DECRYPTED_VALUE);
    decryptedSecretValue.setLastModifiedAt(secretLastModifiedAt);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);

    backstageEnvVariableService.sync(Collections.singletonList(secret), TEST_ACCOUNT_IDENTIFIER);

    verify(k8sClient).updateSecretData(
        eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), secretDataCaptor.capture());
    assertTrue(secretDataCaptor.getValue().isEmpty());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSyncWithExistingSecretDifferentIdentifier() {
    mockAccountNamespaceMapping();
    long secretLastModifiedAt = System.currentTimeMillis();

    BackstageEnvSecretVariable secret = new BackstageEnvSecretVariable();
    secret.envName(TEST_ENV_NAME);
    secret.setHarnessSecretIdentifier(TEST_SECRET_IDENTIFIER1);
    secret.type(BackstageEnvVariable.TypeEnum.SECRET);

    BackstageEnvSecretVariableEntity existingEntity = BackstageEnvSecretVariableEntity.builder()
                                                          .harnessSecretIdentifier(TEST_SECRET_IDENTIFIER)
                                                          .secretLastModifiedAt(secretLastModifiedAt)
                                                          .build();
    existingEntity.setEnvName(TEST_ENV_NAME);
    existingEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);

    when(backstageEnvVariableRepository.findAllByAccountIdentifierAndMultipleEnvNames(
             TEST_ACCOUNT_IDENTIFIER, Collections.singletonList(TEST_ENV_NAME)))
        .thenReturn(Collections.singletonList(existingEntity));

    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_DECRYPTED_VALUE);
    decryptedSecretValue.setLastModifiedAt(secretLastModifiedAt);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER1))
        .thenReturn(decryptedSecretValue);

    backstageEnvVariableService.sync(Collections.singletonList(secret), TEST_ACCOUNT_IDENTIFIER);

    verify(k8sClient).updateSecretData(
        eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), secretDataCaptor.capture());
    assertTrue(secretDataCaptor.getValue().containsKey(LAST_UPDATED_TIMESTAMP));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSyncWithExistingConfigSameValue() {
    mockAccountNamespaceMapping();

    BackstageEnvConfigVariable config = new BackstageEnvConfigVariable();
    config.envName(TEST_ENV_NAME);
    config.setValue(TEST_DECRYPTED_VALUE);
    config.type(BackstageEnvVariable.TypeEnum.CONFIG);

    BackstageEnvConfigVariableEntity existingEntity =
        BackstageEnvConfigVariableEntity.builder().value(TEST_DECRYPTED_VALUE).build();
    existingEntity.setEnvName(TEST_ENV_NAME);
    existingEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);

    when(backstageEnvVariableRepository.findAllByAccountIdentifierAndMultipleEnvNames(
             TEST_ACCOUNT_IDENTIFIER, Collections.singletonList(TEST_ENV_NAME)))
        .thenReturn(Collections.singletonList(existingEntity));

    backstageEnvVariableService.sync(Collections.singletonList(config), TEST_ACCOUNT_IDENTIFIER);

    verify(k8sClient).updateSecretData(
        eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), secretDataCaptor.capture());
    assertTrue(secretDataCaptor.getValue().isEmpty());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSyncWithExistingConfigDifferentValue() {
    mockAccountNamespaceMapping();

    BackstageEnvConfigVariable config = new BackstageEnvConfigVariable();
    config.envName(TEST_ENV_NAME);
    config.setValue("newValue");
    config.type(BackstageEnvVariable.TypeEnum.CONFIG);

    BackstageEnvConfigVariableEntity existingEntity =
        BackstageEnvConfigVariableEntity.builder().value(TEST_DECRYPTED_VALUE).build();
    existingEntity.setEnvName(TEST_ENV_NAME);
    existingEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);

    when(backstageEnvVariableRepository.findAllByAccountIdentifierAndMultipleEnvNames(
             TEST_ACCOUNT_IDENTIFIER, Collections.singletonList(TEST_ENV_NAME)))
        .thenReturn(Collections.singletonList(existingEntity));

    backstageEnvVariableService.sync(Collections.singletonList(config), TEST_ACCOUNT_IDENTIFIER);

    verify(k8sClient).updateSecretData(
        eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), secretDataCaptor.capture());
    assertTrue(secretDataCaptor.getValue().containsKey(LAST_UPDATED_TIMESTAMP));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testProcessSecretDeleteNotFound() {
    EntityChangeDTO entityChangeDTO = EntityChangeDTO.newBuilder()
                                          .setAccountIdentifier(StringValue.of(TEST_ACCOUNT_IDENTIFIER))
                                          .setIdentifier(StringValue.of(TEST_SECRET_IDENTIFIER))
                                          .build();

    when(backstageEnvVariableRepository.updateSecretIsDeleted(TEST_ACCOUNT_IDENTIFIER, TEST_SECRET_IDENTIFIER, true))
        .thenReturn(Optional.empty());

    backstageEnvVariableService.processSecretDelete(entityChangeDTO);

    verify(backstageEnvVariableRepository).updateSecretIsDeleted(TEST_ACCOUNT_IDENTIFIER, TEST_SECRET_IDENTIFIER, true);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDeleteMultiUsingEnvNamesWithSetupUsage() {
    List<String> envNames = Arrays.asList(TEST_ENV_NAME, TEST_ENV_NAME1);
    NamespaceInfo namespaceInfo = new NamespaceInfo();
    namespaceInfo.setNamespace(TEST_NAMESPACE);
    when(namespaceService.getNamespaceForAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(namespaceInfo);

    BackstageEnvSecretVariableEntity entity1 =
        BackstageEnvSecretVariableEntity.builder().harnessSecretIdentifier(TEST_SECRET_IDENTIFIER).build();
    entity1.setEnvName(TEST_ENV_NAME);
    BackstageEnvSecretVariableEntity entity2 =
        BackstageEnvSecretVariableEntity.builder().harnessSecretIdentifier(TEST_SECRET_IDENTIFIER1).build();
    entity2.setEnvName(TEST_ENV_NAME1);

    when(
        backstageEnvVariableRepository.findAllByAccountIdentifierAndMultipleEnvNames(TEST_ACCOUNT_IDENTIFIER, envNames))
        .thenReturn(Arrays.asList(entity1, entity2));

    backstageEnvVariableService.deleteMultiUsingEnvNames(envNames, TEST_ACCOUNT_IDENTIFIER);

    verify(setupUsageProducer).deleteEnvVariableSetupUsage(anyList(), eq(TEST_ACCOUNT_IDENTIFIER));
    verify(k8sClient).updateSecretData(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap());
    verify(backstageEnvVariableRepository).deleteAllByAccountIdentifierAndEnvNames(TEST_ACCOUNT_IDENTIFIER, envNames);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveEnvVariablesWithSecretResolutionFailure() {
    mockAccountNamespaceMapping();

    BackstageEnvSecretVariable secret = new BackstageEnvSecretVariable();
    secret.envName(TEST_ENV_NAME);
    secret.setHarnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    secret.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvVariableEntity secretEntity =
        mapBinder.get(BackstageEnvVariableType.SECRET).fromDto(secret, TEST_ACCOUNT_IDENTIFIER, 0L);

    when(backstageEnvVariableRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Collections.singletonList(secretEntity));

    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenThrow(new RuntimeException("Connection timeout"));

    ResolvedEnvVariableResponse response =
        backstageEnvVariableService.resolveEnvVariables(TEST_ACCOUNT_IDENTIFIER, TEST_NAMESPACE);

    assertNotNull(response.getResolvedEnvVariables());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCreateOrUpdateWithOnlyUpdates() {
    long secretLastModifiedAt = System.currentTimeMillis();
    checkUserAuth();
    mockAccountNamespaceMapping();

    BackstageEnvSecretVariable envVariable = new BackstageEnvSecretVariable();
    envVariable.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariable.envName(TEST_ENV_NAME);
    envVariable.type(BackstageEnvVariable.TypeEnum.SECRET);

    BackstageEnvSecretVariableEntity existingEntity =
        (BackstageEnvSecretVariableEntity) mapBinder.get(BackstageEnvVariableType.SECRET)
            .fromDto(envVariable, TEST_ACCOUNT_IDENTIFIER, secretLastModifiedAt);

    when(backstageEnvVariableRepository.findAllByAccountIdentifierAndMultipleEnvNames(
             TEST_ACCOUNT_IDENTIFIER, Collections.singletonList(TEST_ENV_NAME)))
        .thenReturn(Collections.singletonList(existingEntity));
    when(backstageEnvVariableRepository.update(any())).thenReturn(existingEntity);

    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    decryptedSecretValue.setLastModifiedAt(secretLastModifiedAt);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);

    List<BackstageEnvVariable> responseVariables =
        backstageEnvVariableService.createOrUpdate(Collections.singletonList(envVariable), TEST_ACCOUNT_IDENTIFIER);

    assertEquals(1, responseVariables.size());
    verify(backstageEnvVariableRepository).saveAll(Collections.emptyList());
    verify(backstageEnvVariableRepository).update(any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCreateOrUpdateWithOnlyCreates() {
    long secretLastModifiedAt = System.currentTimeMillis();
    checkUserAuth();
    mockAccountNamespaceMapping();

    BackstageEnvSecretVariable envVariable = new BackstageEnvSecretVariable();
    envVariable.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariable.envName(TEST_ENV_NAME);
    envVariable.type(BackstageEnvVariable.TypeEnum.SECRET);

    BackstageEnvVariableEntity entity = mapBinder.get(BackstageEnvVariableType.SECRET)
                                            .fromDto(envVariable, TEST_ACCOUNT_IDENTIFIER, secretLastModifiedAt);

    when(backstageEnvVariableRepository.findAllByAccountIdentifierAndMultipleEnvNames(
             TEST_ACCOUNT_IDENTIFIER, Collections.singletonList(TEST_ENV_NAME)))
        .thenReturn(Collections.emptyList());
    when(backstageEnvVariableRepository.saveAll(any())).thenReturn(Collections.singletonList(entity));

    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    decryptedSecretValue.setLastModifiedAt(secretLastModifiedAt);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);

    List<BackstageEnvVariable> responseVariables =
        backstageEnvVariableService.createOrUpdate(Collections.singletonList(envVariable), TEST_ACCOUNT_IDENTIFIER);

    assertEquals(1, responseVariables.size());
    verify(backstageEnvVariableRepository).saveAll(any());
    verify(backstageEnvVariableRepository, never()).update(any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSyncWithSecretLastModifiedAtZero() {
    mockAccountNamespaceMapping();

    BackstageEnvSecretVariable secret = new BackstageEnvSecretVariable();
    secret.envName(TEST_ENV_NAME);
    secret.setHarnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    secret.type(BackstageEnvVariable.TypeEnum.SECRET);

    BackstageEnvSecretVariableEntity existingEntity = BackstageEnvSecretVariableEntity.builder()
                                                          .harnessSecretIdentifier(TEST_SECRET_IDENTIFIER)
                                                          .secretLastModifiedAt(1000L)
                                                          .build();
    existingEntity.setEnvName(TEST_ENV_NAME);
    existingEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);

    when(backstageEnvVariableRepository.findAllByAccountIdentifierAndMultipleEnvNames(
             TEST_ACCOUNT_IDENTIFIER, Collections.singletonList(TEST_ENV_NAME)))
        .thenReturn(Collections.singletonList(existingEntity));

    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_DECRYPTED_VALUE);
    decryptedSecretValue.setLastModifiedAt(0L);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);

    backstageEnvVariableService.sync(Collections.singletonList(secret), TEST_ACCOUNT_IDENTIFIER);

    verify(k8sClient).updateSecretData(
        eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), secretDataCaptor.capture());
    assertTrue(secretDataCaptor.getValue().containsKey(LAST_UPDATED_TIMESTAMP));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testProcessSecretUpdateWithAccountPrefix() {
    long secretLastModifiedAt = System.currentTimeMillis();
    mockAccountNamespaceMapping();
    BackstageEnvSecretVariable envVariable = new BackstageEnvSecretVariable();
    envVariable.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER1);
    envVariable.envName(TEST_ENV_NAME);
    envVariable.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvVariableEntity envVariableEntity =
        mapBinder.get(BackstageEnvVariableType.SECRET)
            .fromDto(envVariable, TEST_ACCOUNT_IDENTIFIER, secretLastModifiedAt);
    when(backstageEnvVariableRepository.findByAccountIdentifierAndHarnessSecretIdentifier(
             TEST_ACCOUNT_IDENTIFIER, TEST_SECRET_IDENTIFIER1))
        .thenReturn(Optional.of(envVariableEntity));
    EntityChangeDTO entityChangeDTO = EntityChangeDTO.newBuilder()
                                          .setAccountIdentifier(StringValue.of(TEST_ACCOUNT_IDENTIFIER))
                                          .setIdentifier(StringValue.of("account." + TEST_SECRET_IDENTIFIER1))
                                          .build();
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    decryptedSecretValue.setLastModifiedAt(secretLastModifiedAt + 1);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER1))
        .thenReturn(decryptedSecretValue);

    backstageEnvVariableService.processSecretUpdate(entityChangeDTO);

    verify(k8sClient).updateSecretData(eq(TEST_ACCOUNT_IDENTIFIER), eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap());
  }

  private void checkUserAuth() {
    MockedStatic<SecurityContextBuilder> mockSecurityContext = mockStatic(SecurityContextBuilder.class);
    mockSecurityContext.when(SecurityContextBuilder::getPrincipal)
        .thenReturn(new UserPrincipal(ADMIN_USER_ID, "admin@harness.io", "admin", ACCOUNT_ID));
    MockedStatic<CGRestUtils> mockRestUtils = mockStatic(CGRestUtils.class);
    mockRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(true);

    idpCommonService.checkUserAuthorization();

    verify(ngConnectorManagerClient, times(1)).isHarnessSupportUser(ADMIN_USER_ID);
    mockSecurityContext.close();
    mockRestUtils.close();
  }

  private void mockAccountNamespaceMapping() {
    NamespaceInfo namespaceInfo = new NamespaceInfo();
    namespaceInfo.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    namespaceInfo.setNamespace(TEST_NAMESPACE);
    when(namespaceService.getNamespaceForAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(namespaceInfo);
  }
}
