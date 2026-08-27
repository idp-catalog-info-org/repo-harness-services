/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.buckets.resources.gcs;

import static io.harness.rule.OwnerRule.ANIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.buckets.resources.service.GcsResourceService;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.buckets.resources.BucketsResourceUtils;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.utils.IdentifierRefHelper;

import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

public class GcsBucketsResourceTest extends CategoryTest {
  private static final String CONNECTOR_REF = "connectorRef";
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";

  @Mock GcsResourceService gcsResourceService;
  @Mock BucketsResourceUtils bucketsResourceUtils;
  @Mock ArtifactResourceUtils artifactResourceUtils;

  IdentifierRef identifierRef = mock(IdentifierRef.class);

  @InjectMocks GcsBucketsResource gcsBucketsResource;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void getListBucketsEnforcesConnectorAccess() {
    Map<String, String> buckets = Collections.singletonMap("b1", "b1");
    try (MockedStatic<IdentifierRefHelper> ignore = mockStatic(IdentifierRefHelper.class)) {
      when(IdentifierRefHelper.getIdentifierRef(anyString(), anyString(), anyString(), anyString()))
          .thenAnswer(i -> identifierRef);
      when(gcsResourceService.listBuckets(any(), anyString(), anyString(), anyString())).thenReturn(buckets);

      ResponseDTO<Map<String, String>> result = gcsBucketsResource.getListBuckets(
          CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, null);

      assertThat(result.getData()).isEqualTo(buckets);
      verify(artifactResourceUtils).checkConnectorAccess(identifierRef);
    }
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void getListBucketsDeniedConnectorAccessThrows() {
    try (MockedStatic<IdentifierRefHelper> ignore = mockStatic(IdentifierRefHelper.class)) {
      when(IdentifierRefHelper.getIdentifierRef(anyString(), anyString(), anyString(), anyString()))
          .thenAnswer(i -> identifierRef);
      doThrow(new InvalidRequestException("access denied"))
          .when(artifactResourceUtils)
          .checkConnectorAccess(identifierRef);

      assertThatThrownBy(()
                             -> gcsBucketsResource.getListBuckets(
                                 CONNECTOR_REF, ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, null, null))
          .isInstanceOf(InvalidRequestException.class);

      verify(gcsResourceService, never()).listBuckets(any(), anyString(), anyString(), anyString());
    }
  }
}
