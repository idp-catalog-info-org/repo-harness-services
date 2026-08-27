/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.datastructures.EphemeralCacheService;
import io.harness.exception.InvalidRequestException;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;
import io.harness.pms.contracts.plan.InitializeSdkRequest;
import io.harness.pms.contracts.plan.InitializeSdkResponse;
import io.harness.pms.exception.InitializeSdkException;
import io.harness.pms.pipeline.service.yamlschema.SchemaFetcher;
import io.harness.pms.sdk.PmsSdkInstance.PmsSdkInstanceKeys;
import io.harness.repositories.sdk.PmsSdkInstanceRepository;
import io.harness.rule.Owner;
import io.harness.springdata.TransactionHelper;

import com.google.protobuf.LazyStringArrayList;
import com.google.protobuf.ProtocolStringList;
import io.grpc.stub.StreamObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsSdkInstanceTest extends CategoryTest {
  @Mock PmsSdkInstanceRepository pmsSdkInstanceRepository;
  @Mock MongoTemplate mongoTemplate;
  @Mock PersistentLocker persistentLocker;
  @Mock SchemaFetcher schemaFetcher;
  @Mock TransactionHelper transactionHelper;
  @Mock EphemeralCacheService ephemeralCacheService;
  @Mock StreamObserver<InitializeSdkResponse> responseObserver;
  @Mock SecondaryMongoTemplateHolder secondaryMongoTemplateHolder;
  PmsSdkInstanceService pmsSdkInstanceService;

  @Before
  public void SetUp() {
    MockitoAnnotations.initMocks(this);
    pmsSdkInstanceService = new PmsSdkInstanceService(pmsSdkInstanceRepository, mongoTemplate, persistentLocker,
        schemaFetcher, true, false, transactionHelper, ephemeralCacheService, secondaryMongoTemplateHolder);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testSaveSdkInstance() {
    InitializeSdkRequest request = InitializeSdkRequest.newBuilder().putStaticAliases("alias", "value").build();
    assertThatCode(() -> pmsSdkInstanceService.saveSdkInstance(request)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testNewRegisteredSdkFunctors() {
    InitializeSdkRequest request =
        InitializeSdkRequest.newBuilder().setName("cd").addSdkFunctors("sdk1").addSdkFunctors("sdk2").build();
    Query query = query(where(PmsSdkInstanceKeys.name).is(request.getName()));

    ProtocolStringList protocolStringList = new LazyStringArrayList();
    protocolStringList.add("sdk1");
    protocolStringList.add("sdk2");

    PmsSdkInstance pmsInstance = PmsSdkInstance.builder().sdkFunctors(protocolStringList).build();

    // case1: when db sdk = request sdk functors
    when(mongoTemplate.findOne(query, PmsSdkInstance.class)).thenReturn(pmsInstance);
    assertThatCode(() -> pmsSdkInstanceService.ensureNewFunctorsAreRegisteredOnlyInPipeline(request, query))
        .doesNotThrowAnyException();

    // case2: new functors registered in sdk
    InitializeSdkRequest request2 = InitializeSdkRequest.newBuilder()
                                        .setName("cd")
                                        .addSdkFunctors("sdk1")
                                        .addSdkFunctors("sdk2")
                                        .addSdkFunctors("sdk3")
                                        .build();
    assertThatThrownBy(() -> pmsSdkInstanceService.ensureNewFunctorsAreRegisteredOnlyInPipeline(request2, query))
        .isInstanceOf(InitializeSdkException.class);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetSdkInstanceCacheValueEmpty() {
    // With empty cache and no DB results, should return empty map
    java.util.Map<String, PmsSdkInstance> sdkInstanceMap = pmsSdkInstanceService.getSdkInstanceCacheValue();
    assertThat(sdkInstanceMap).isEmpty();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testInitializeSdk() {
    InitializeSdkRequest.Builder requestBuilder = InitializeSdkRequest.newBuilder().putStaticAliases("alias", "value");

    // passing request without name
    InitializeSdkRequest requestWithoutName = requestBuilder.build();
    assertThatThrownBy(() -> pmsSdkInstanceService.initializeSdk(requestWithoutName, responseObserver))
        .isInstanceOf(InvalidRequestException.class);

    // passing request with name
    InitializeSdkRequest requestWithName = InitializeSdkRequest.newBuilder().setName("name").build();
    assertThatThrownBy(() -> pmsSdkInstanceService.initializeSdk(requestWithName, responseObserver))
        .isInstanceOf(InitializeSdkException.class);

    // dummy lock
    AcquiredLock<?> acquiredLock = mock(AcquiredLock.class);
    doReturn(acquiredLock).when(persistentLocker).waitToAcquireLockOptional(any(), any(), any());
    pmsSdkInstanceService.initializeSdk(requestWithName, responseObserver);
    verify(ephemeralCacheService, times(1)).getDistributedSet("sdkStepsVisibleInUI");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testInitializeSdkLockIssue() {
    InitializeSdkRequest.Builder requestBuilder = InitializeSdkRequest.newBuilder().putStaticAliases("alias", "value");

    // passing request without name
    InitializeSdkRequest requestWithoutName = requestBuilder.build();
    assertThatThrownBy(() -> pmsSdkInstanceService.initializeSdk(requestWithoutName, responseObserver))
        .isInstanceOf(InvalidRequestException.class);

    // passing request with name
    InitializeSdkRequest requestWithName = InitializeSdkRequest.newBuilder().setName("name").build();
    assertThatThrownBy(() -> pmsSdkInstanceService.initializeSdk(requestWithName, responseObserver))
        .isInstanceOf(InitializeSdkException.class);

    doReturn(null).when(persistentLocker).waitToAcquireLockOptional(any(), any(), any());
    assertThatThrownBy(() -> pmsSdkInstanceService.initializeSdk(requestWithName, responseObserver))
        .isInstanceOf(InitializeSdkException.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testInitializeSdkSkipsMongoWhenConfigured() {
    pmsSdkInstanceService = new PmsSdkInstanceService(pmsSdkInstanceRepository, mongoTemplate, persistentLocker,
        schemaFetcher, true, true, transactionHelper, ephemeralCacheService, secondaryMongoTemplateHolder);
    InitializeSdkRequest requestWithName = InitializeSdkRequest.newBuilder().setName("cd").build();

    pmsSdkInstanceService.initializeSdk(requestWithName, responseObserver);

    verify(responseObserver, times(1)).onNext(any());
    verify(responseObserver, times(1)).onCompleted();
  }
}
