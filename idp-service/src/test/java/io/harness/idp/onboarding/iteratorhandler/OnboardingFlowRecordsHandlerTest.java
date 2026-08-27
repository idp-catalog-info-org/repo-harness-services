/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.iteratorhandler;

import static io.harness.rule.OwnerRule.SATHISH;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.UnexpectedException;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.idp.onboarding.service.impl.OnboardingServiceV2Impl;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.pojos.IteratorConfig;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class OnboardingFlowRecordsHandlerTest extends CategoryTest {
  AutoCloseable openMocks;

  @InjectMocks OnboardingFlowRecordsHandler onboardingFlowRecordsHandler;

  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock MongoTemplate mongoTemplate;
  @Mock OnboardingServiceV2Impl onboardingServiceV2;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandle() {
    doNothing().when(onboardingServiceV2).asyncImport();
    onboardingFlowRecordsHandler.handle(IteratorEntity.builder().build());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleError() {
    doThrow(new UnexpectedException("Exception Throw")).when(onboardingServiceV2).asyncImport();
    onboardingFlowRecordsHandler.handle(IteratorEntity.builder().build());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    IteratorConfig iteratorConfig =
        IteratorConfig.builder().enabled(true).threadPoolCount(3).targetIntervalInSeconds(1000).build();
    onboardingFlowRecordsHandler.registerIterators(iteratorConfig);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
