/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.envvariable.beans.entity;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.mongo.index.MongoIndex;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariableResponse;
import io.harness.spec.server.idp.v1.model.ResolvedEnvVariableResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class BackstageEnvVariableEntityTest extends CategoryTest {
  static final String TEST_ENV_NAME = "TEST_ENV_NAME";
  static final String TEST_IDENTIFIER = "testIdentifier";
  static final String TEST_ACCOUNT_IDENTIFIER = "accountId";
  static final String TEST_SECRET_IDENTIFIER = "secretId";
  static final String TEST_VALUE = "testValue";
  static final Long TEST_CREATED_AT = 1000L;
  static final Long TEST_UPDATED_AT = 2000L;

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMongoIndexes() {
    List<MongoIndex> indexes = BackstageEnvVariableEntity.mongoIndexes();

    assertNotNull(indexes);
    assertEquals(1, indexes.size());

    MongoIndex index = indexes.get(0);
    assertEquals("unique_account_envName", index.getName());
    assertTrue(index.isUnique());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageEnvSecretVariableEntityGetType() {
    BackstageEnvSecretVariableEntity entity = BackstageEnvSecretVariableEntity.builder()
                                                  .harnessSecretIdentifier(TEST_SECRET_IDENTIFIER)
                                                  .isDeleted(false)
                                                  .secretLastModifiedAt(1000L)
                                                  .build();
    assertEquals(BackstageEnvVariableType.SECRET, entity.getType());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageEnvConfigVariableEntityGetType() {
    BackstageEnvConfigVariableEntity entity = BackstageEnvConfigVariableEntity.builder().value(TEST_VALUE).build();
    assertEquals(BackstageEnvVariableType.CONFIG, entity.getType());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageEnvSecretVariableMapperFromDto() {
    BackstageEnvSecretVariableEntity.BackstageEnvSecretVariableMapper mapper =
        new BackstageEnvSecretVariableEntity.BackstageEnvSecretVariableMapper();

    BackstageEnvSecretVariable dto = new BackstageEnvSecretVariable();
    dto.setIdentifier(TEST_IDENTIFIER);
    dto.setEnvName(TEST_ENV_NAME);
    dto.setHarnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    dto.setIsDeleted(false);
    dto.setCreated(TEST_CREATED_AT);
    dto.setUpdated(TEST_UPDATED_AT);

    long secretLastModifiedAt = 3000L;
    BackstageEnvSecretVariableEntity entity = mapper.fromDto(dto, TEST_ACCOUNT_IDENTIFIER, secretLastModifiedAt);

    assertNotNull(entity);
    assertEquals(TEST_IDENTIFIER, entity.getId());
    assertEquals(TEST_ENV_NAME, entity.getEnvName());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, entity.getAccountIdentifier());
    assertEquals(TEST_SECRET_IDENTIFIER, entity.getHarnessSecretIdentifier());
    assertFalse(entity.isDeleted());
    assertEquals(secretLastModifiedAt, entity.getSecretLastModifiedAt());
    assertEquals(TEST_CREATED_AT, entity.getCreatedAt());
    assertEquals(TEST_UPDATED_AT, entity.getLastModifiedAt());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageEnvSecretVariableMapperToDto() {
    BackstageEnvSecretVariableEntity.BackstageEnvSecretVariableMapper mapper =
        new BackstageEnvSecretVariableEntity.BackstageEnvSecretVariableMapper();

    BackstageEnvSecretVariableEntity entity = BackstageEnvSecretVariableEntity.builder()
                                                  .harnessSecretIdentifier(TEST_SECRET_IDENTIFIER)
                                                  .isDeleted(true)
                                                  .secretLastModifiedAt(3000L)
                                                  .build();
    entity.setId(TEST_IDENTIFIER);
    entity.setEnvName(TEST_ENV_NAME);
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setCreatedAt(TEST_CREATED_AT);
    entity.setLastModifiedAt(TEST_UPDATED_AT);

    BackstageEnvSecretVariable dto = mapper.toDto(entity);

    assertNotNull(dto);
    assertEquals(TEST_IDENTIFIER, dto.getIdentifier());
    assertEquals(TEST_ENV_NAME, dto.getEnvName());
    assertEquals(TEST_SECRET_IDENTIFIER, dto.getHarnessSecretIdentifier());
    assertTrue(dto.isIsDeleted());
    assertEquals(TEST_CREATED_AT, dto.getCreated());
    assertEquals(TEST_UPDATED_AT, dto.getUpdated());
    assertEquals(BackstageEnvVariable.TypeEnum.SECRET, dto.getType());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageEnvConfigVariableMapperFromDto() {
    BackstageEnvConfigVariableEntity.BackstageEnvConfigVariableMapper mapper =
        new BackstageEnvConfigVariableEntity.BackstageEnvConfigVariableMapper();

    BackstageEnvConfigVariable dto = new BackstageEnvConfigVariable();
    dto.setIdentifier(TEST_IDENTIFIER);
    dto.setEnvName(TEST_ENV_NAME);
    dto.setValue(TEST_VALUE);
    dto.setCreated(TEST_CREATED_AT);
    dto.setUpdated(TEST_UPDATED_AT);

    BackstageEnvConfigVariableEntity entity = mapper.fromDto(dto, TEST_ACCOUNT_IDENTIFIER, 0L);

    assertNotNull(entity);
    assertEquals(TEST_IDENTIFIER, entity.getId());
    assertEquals(TEST_ENV_NAME, entity.getEnvName());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, entity.getAccountIdentifier());
    assertEquals(TEST_VALUE, entity.getValue());
    assertEquals(TEST_CREATED_AT, entity.getCreatedAt());
    assertEquals(TEST_UPDATED_AT, entity.getLastModifiedAt());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageEnvConfigVariableMapperToDto() {
    BackstageEnvConfigVariableEntity.BackstageEnvConfigVariableMapper mapper =
        new BackstageEnvConfigVariableEntity.BackstageEnvConfigVariableMapper();

    BackstageEnvConfigVariableEntity entity = BackstageEnvConfigVariableEntity.builder().value(TEST_VALUE).build();
    entity.setId(TEST_IDENTIFIER);
    entity.setEnvName(TEST_ENV_NAME);
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setCreatedAt(TEST_CREATED_AT);
    entity.setLastModifiedAt(TEST_UPDATED_AT);

    BackstageEnvConfigVariable dto = mapper.toDto(entity);

    assertNotNull(dto);
    assertEquals(TEST_IDENTIFIER, dto.getIdentifier());
    assertEquals(TEST_ENV_NAME, dto.getEnvName());
    assertEquals(TEST_VALUE, dto.getValue());
    assertEquals(TEST_CREATED_AT, dto.getCreated());
    assertEquals(TEST_UPDATED_AT, dto.getUpdated());
    assertEquals(BackstageEnvVariable.TypeEnum.CONFIG, dto.getType());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToResponseList() {
    BackstageEnvSecretVariable secretVariable = new BackstageEnvSecretVariable();
    secretVariable.setEnvName("SECRET_ENV");
    secretVariable.setHarnessSecretIdentifier(TEST_SECRET_IDENTIFIER);

    BackstageEnvConfigVariable configVariable = new BackstageEnvConfigVariable();
    configVariable.setEnvName("CONFIG_ENV");
    configVariable.setValue(TEST_VALUE);

    List<BackstageEnvVariable> variables = Arrays.asList(secretVariable, configVariable);

    List<BackstageEnvVariableResponse> responseList =
        BackstageEnvVariableEntity.BackstageEnvVariableMapper.toResponseList(variables);

    assertNotNull(responseList);
    assertEquals(2, responseList.size());
    assertEquals(secretVariable, responseList.get(0).getEnvVariable());
    assertEquals(configVariable, responseList.get(1).getEnvVariable());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToResponseListEmpty() {
    List<BackstageEnvVariable> variables = new ArrayList<>();

    List<BackstageEnvVariableResponse> responseList =
        BackstageEnvVariableEntity.BackstageEnvVariableMapper.toResponseList(variables);

    assertNotNull(responseList);
    assertTrue(responseList.isEmpty());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToResolvedVariableResponse() {
    String resolvedVariables = "{\"envName\":\"TEST\",\"value\":\"testValue\"}";

    ResolvedEnvVariableResponse response =
        BackstageEnvVariableEntity.BackstageEnvVariableMapper.toResolvedVariableResponse(resolvedVariables);

    assertNotNull(response);
    assertEquals(resolvedVariables, response.getResolvedEnvVariables());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToResolvedVariableResponseEmpty() {
    String resolvedVariables = "";

    ResolvedEnvVariableResponse response =
        BackstageEnvVariableEntity.BackstageEnvVariableMapper.toResolvedVariableResponse(resolvedVariables);

    assertNotNull(response);
    assertEquals(resolvedVariables, response.getResolvedEnvVariables());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageEnvSecretVariableMapperWithIsDeletedTrue() {
    BackstageEnvSecretVariableEntity.BackstageEnvSecretVariableMapper mapper =
        new BackstageEnvSecretVariableEntity.BackstageEnvSecretVariableMapper();

    BackstageEnvSecretVariable dto = new BackstageEnvSecretVariable();
    dto.setIdentifier(TEST_IDENTIFIER);
    dto.setEnvName(TEST_ENV_NAME);
    dto.setHarnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    dto.setIsDeleted(true);

    BackstageEnvSecretVariableEntity entity = mapper.fromDto(dto, TEST_ACCOUNT_IDENTIFIER, 5000L);

    assertTrue(entity.isDeleted());
    assertEquals(5000L, entity.getSecretLastModifiedAt());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEntityFieldsSettersAndGetters() {
    BackstageEnvSecretVariableEntity entity = BackstageEnvSecretVariableEntity.builder()
                                                  .harnessSecretIdentifier(TEST_SECRET_IDENTIFIER)
                                                  .isDeleted(false)
                                                  .secretLastModifiedAt(1000L)
                                                  .build();

    entity.setId(TEST_IDENTIFIER);
    entity.setEnvName(TEST_ENV_NAME);
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setCreatedAt(TEST_CREATED_AT);
    entity.setLastModifiedAt(TEST_UPDATED_AT);

    assertEquals(TEST_IDENTIFIER, entity.getId());
    assertEquals(TEST_ENV_NAME, entity.getEnvName());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, entity.getAccountIdentifier());
    assertEquals(TEST_CREATED_AT, entity.getCreatedAt());
    assertEquals(TEST_UPDATED_AT, entity.getLastModifiedAt());
    assertEquals(TEST_SECRET_IDENTIFIER, entity.getHarnessSecretIdentifier());
    assertFalse(entity.isDeleted());
    assertEquals(1000L, entity.getSecretLastModifiedAt());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testConfigEntityFieldsSettersAndGetters() {
    BackstageEnvConfigVariableEntity entity = BackstageEnvConfigVariableEntity.builder().value(TEST_VALUE).build();

    entity.setId(TEST_IDENTIFIER);
    entity.setEnvName(TEST_ENV_NAME);
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setCreatedAt(TEST_CREATED_AT);
    entity.setLastModifiedAt(TEST_UPDATED_AT);

    assertEquals(TEST_IDENTIFIER, entity.getId());
    assertEquals(TEST_ENV_NAME, entity.getEnvName());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, entity.getAccountIdentifier());
    assertEquals(TEST_CREATED_AT, entity.getCreatedAt());
    assertEquals(TEST_UPDATED_AT, entity.getLastModifiedAt());
    assertEquals(TEST_VALUE, entity.getValue());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageEnvSecretVariableMapperWithNullTimestamps() {
    BackstageEnvSecretVariableEntity.BackstageEnvSecretVariableMapper mapper =
        new BackstageEnvSecretVariableEntity.BackstageEnvSecretVariableMapper();

    BackstageEnvSecretVariable dto = new BackstageEnvSecretVariable();
    dto.setIdentifier(TEST_IDENTIFIER);
    dto.setEnvName(TEST_ENV_NAME);
    dto.setHarnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    dto.setIsDeleted(false);
    dto.setCreated(null);
    dto.setUpdated(null);

    BackstageEnvSecretVariableEntity entity = mapper.fromDto(dto, TEST_ACCOUNT_IDENTIFIER, 0L);

    assertNotNull(entity);
    assertEquals(TEST_IDENTIFIER, entity.getId());
    assertEquals(TEST_ENV_NAME, entity.getEnvName());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testBackstageEnvConfigVariableMapperWithNullTimestamps() {
    BackstageEnvConfigVariableEntity.BackstageEnvConfigVariableMapper mapper =
        new BackstageEnvConfigVariableEntity.BackstageEnvConfigVariableMapper();

    BackstageEnvConfigVariable dto = new BackstageEnvConfigVariable();
    dto.setIdentifier(TEST_IDENTIFIER);
    dto.setEnvName(TEST_ENV_NAME);
    dto.setValue(TEST_VALUE);
    dto.setCreated(null);
    dto.setUpdated(null);

    BackstageEnvConfigVariableEntity entity = mapper.fromDto(dto, TEST_ACCOUNT_IDENTIFIER, 0L);

    assertNotNull(entity);
    assertEquals(TEST_IDENTIFIER, entity.getId());
    assertEquals(TEST_ENV_NAME, entity.getEnvName());
    assertEquals(TEST_VALUE, entity.getValue());
  }
}
