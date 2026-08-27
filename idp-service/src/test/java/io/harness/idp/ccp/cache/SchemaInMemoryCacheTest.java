/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.ccp.cache;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.common.CommonUtils;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class SchemaInMemoryCacheTest extends CategoryTest {
  private static final String TEST_KIND1 = "component";
  private static final String TEST_KIND2 = "random";
  private AutoCloseable openMocks;
  public static final String COMPONENT_SCHEMA = "{}";
  @InjectMocks SchemaInMemoryCache schemaCache;
  MockedStatic<CommonUtils> commonUtilsMockedStatic;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    commonUtilsMockedStatic = mockStatic(CommonUtils.class);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGet() {
    when(CommonUtils.readFileFromClassPath(any())).thenReturn(COMPONENT_SCHEMA);
    String componentSchema = schemaCache.get(TEST_KIND1);
    assertEquals(COMPONENT_SCHEMA, componentSchema);
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testGetException() {
    when(CommonUtils.readFileFromClassPath(any())).thenThrow(InvalidRequestException.class);
    schemaCache.get(TEST_KIND2);
  }

  @After
  public void tearDown() throws Exception {
    schemaCache.cache.invalidateAll();
    commonUtilsMockedStatic.close();
    openMocks.close();
  }
}
