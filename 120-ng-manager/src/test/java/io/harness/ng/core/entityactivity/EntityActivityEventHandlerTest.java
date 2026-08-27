/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entityactivity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.activityhistory.NGActivityType;
import io.harness.ng.core.activityhistory.dto.NGActivityDTO;
import io.harness.ng.core.entityactivity.connector.ConnectorEntityActivityEventHandler;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.PmsFeatureFlagHelper;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EntityActivityEventHandlerTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";

  @Mock ConnectorEntityActivityEventHandler connectorEntityActivityEventHandler;

  @Mock PmsFeatureFlagHelper featureFlagHelperService;
  @InjectMocks EntityActivityEventHandler entityActivityEventHandler;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testUpdateActivityResultInEntityFFTrue() {
    NGActivityDTO ngActivityDTO = NGActivityDTO.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .type(NGActivityType.ENTITY_UPDATE)
                                      .referredEntity(EntityDetail.builder().type(EntityType.CONNECTORS).build())
                                      .build();
    entityActivityEventHandler.updateActivityResultInEntity(ngActivityDTO);
    verify(connectorEntityActivityEventHandler, times(1)).resetPerpetualTasksForConnector(any());
  }
}
