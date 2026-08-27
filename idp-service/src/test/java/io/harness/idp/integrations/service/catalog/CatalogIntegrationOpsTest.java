/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.catalog;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.integrations.beans.catalog.CatalogIntegrationSyncRequest;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.CatalogIntegrationRequest;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class CatalogIntegrationOpsTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSealedClassPermits() {
    Class<?>[] permittedSubclasses = CatalogIntegrationOps.class.getPermittedSubclasses();
    assertThat(permittedSubclasses).hasSize(1);
    assertThat(permittedSubclasses[0]).isEqualTo(HarnessCDIntegrationOpsImpl.class);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGenericTypeParameters() {
    Type[] typeParameters = CatalogIntegrationOps.class.getTypeParameters();
    assertThat(typeParameters).hasSize(3);

    assertThat(typeParameters[0].toString()).contains("S");
    assertThat(typeParameters[1].toString()).contains("T");
    assertThat(typeParameters[2].toString()).contains("U");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPrepareMethodSignature() {
    Method[] methods = CatalogIntegrationOps.class.getDeclaredMethods();

    Method prepareMethod = Arrays.stream(methods).filter(m -> m.getName().equals("prepare")).findFirst().orElse(null);

    assertThat(prepareMethod).isNotNull();
    assertThat(prepareMethod.getParameterCount()).isEqualTo(2);
    assertThat(prepareMethod.getParameterTypes()[0]).isEqualTo(String.class);
    assertThat(prepareMethod.getParameterTypes()[1]).isEqualTo(CatalogIntegrationRequest.class);
    assertThat(prepareMethod.getReturnType()).isEqualTo(IntegrationEntity.class);
    assertThat(Modifier.isAbstract(prepareMethod.getModifiers())).isTrue();
    assertThat(Modifier.isProtected(prepareMethod.getModifiers())
        || (!Modifier.isPublic(prepareMethod.getModifiers()) && !Modifier.isPrivate(prepareMethod.getModifiers())
            && !Modifier.isProtected(prepareMethod.getModifiers())))
        .isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPrepareCatalogIntegrationSyncRequestMethod() {
    Method[] methods = CatalogIntegrationOps.class.getDeclaredMethods();

    Method method = Arrays.stream(methods)
                        .filter(m -> m.getName().equals("prepareCatalogIntegrationSyncRequest"))
                        .findFirst()
                        .orElse(null);

    assertThat(method).isNotNull();
    assertThat(method.getParameterCount()).isEqualTo(1);
    assertThat(method.getParameterTypes()[0]).isEqualTo(IntegrationEntity.class);
    assertThat(method.getReturnType()).isEqualTo(CatalogIntegrationSyncRequest.class);
    assertThat(Modifier.isAbstract(method.getModifiers())).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPerformSyncInBackgroundMethod() {
    Method[] methods = CatalogIntegrationOps.class.getDeclaredMethods();

    Method method =
        Arrays.stream(methods).filter(m -> m.getName().equals("performSyncInBackground")).findFirst().orElse(null);

    assertThat(method).isNotNull();
    assertThat(method.getParameterCount()).isEqualTo(1);
    assertThat(method.getParameterTypes()[0]).isEqualTo(CatalogIntegrationSyncRequest.class);
    assertThat(method.getReturnType()).isEqualTo(CompletableFuture.class);
    assertThat(Modifier.isAbstract(method.getModifiers())).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPerformSyncMethod() {
    Method[] methods = CatalogIntegrationOps.class.getDeclaredMethods();

    Method method = Arrays.stream(methods).filter(m -> m.getName().equals("performSync")).findFirst().orElse(null);

    assertThat(method).isNotNull();
    assertThat(method.getParameterCount()).isEqualTo(1);
    assertThat(method.getParameterTypes()[0]).isEqualTo(CatalogIntegrationSyncRequest.class);
    assertThat(method.getReturnType()).isEqualTo(void.class);
    assertThat(Modifier.isAbstract(method.getModifiers())).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPerformCompleteSyncInBackgroundMethod() {
    Method[] methods = CatalogIntegrationOps.class.getDeclaredMethods();

    Method method = Arrays.stream(methods)
                        .filter(m -> m.getName().equals("performCompleteSyncInBackground"))
                        .findFirst()
                        .orElse(null);

    assertThat(method).isNotNull();
    assertThat(method.getParameterCount()).isEqualTo(1);
    assertThat(method.getParameterTypes()[0]).isEqualTo(CatalogIntegrationSyncRequest.class);
    assertThat(method.getReturnType()).isEqualTo(CompletableFuture.class);
    assertThat(Modifier.isAbstract(method.getModifiers())).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPerformCompleteSyncMethod() {
    Method[] methods = CatalogIntegrationOps.class.getDeclaredMethods();

    Method method =
        Arrays.stream(methods).filter(m -> m.getName().equals("performCompleteSync")).findFirst().orElse(null);

    assertThat(method).isNotNull();
    assertThat(method.getParameterCount()).isEqualTo(1);
    assertThat(method.getParameterTypes()[0]).isEqualTo(CatalogIntegrationSyncRequest.class);
    assertThat(method.getReturnType()).isEqualTo(void.class);
    assertThat(Modifier.isAbstract(method.getModifiers())).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPerformIncrementalSyncMethod() {
    Method[] methods = CatalogIntegrationOps.class.getDeclaredMethods();

    Method method =
        Arrays.stream(methods).filter(m -> m.getName().equals("performIncrementalSync")).findFirst().orElse(null);

    assertThat(method).isNotNull();
    assertThat(method.getParameterCount()).isEqualTo(1);
    assertThat(method.getParameterTypes()[0]).isEqualTo(CatalogIntegrationSyncRequest.class);
    assertThat(method.getReturnType()).isEqualTo(void.class);
    assertThat(Modifier.isAbstract(method.getModifiers())).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testTransformMethods() {
    Method[] methods = CatalogIntegrationOps.class.getDeclaredMethods();

    long transformMethodCount = Arrays.stream(methods).filter(m -> m.getName().equals("transform")).count();

    assertThat(transformMethodCount).isEqualTo(2);

    Method singleParamTransform = Arrays.stream(methods)
                                      .filter(m -> m.getName().equals("transform") && m.getParameterCount() == 1)
                                      .findFirst()
                                      .orElse(null);

    assertThat(singleParamTransform).isNotNull();
    assertThat(singleParamTransform.getParameterTypes()[0]).isEqualTo(Object.class);
    assertThat(singleParamTransform.getReturnType()).isEqualTo(Object.class);
    assertThat(Modifier.isAbstract(singleParamTransform.getModifiers())).isTrue();

    Method twoParamTransform = Arrays.stream(methods)
                                   .filter(m -> m.getName().equals("transform") && m.getParameterCount() == 2)
                                   .findFirst()
                                   .orElse(null);

    assertThat(twoParamTransform).isNotNull();
    assertThat(twoParamTransform.getParameterTypes()[0]).isEqualTo(Object.class);
    assertThat(twoParamTransform.getParameterTypes()[1]).isEqualTo(Object.class);
    assertThat(twoParamTransform.getReturnType()).isEqualTo(Object.class);
    assertThat(Modifier.isAbstract(twoParamTransform.getModifiers())).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testAllAbstractMethods() {
    Method[] methods = CatalogIntegrationOps.class.getDeclaredMethods();

    for (Method method : methods) {
      assertThat(Modifier.isAbstract(method.getModifiers()))
          .as("Method %s should be abstract", method.getName())
          .isTrue();
    }
  }
}
