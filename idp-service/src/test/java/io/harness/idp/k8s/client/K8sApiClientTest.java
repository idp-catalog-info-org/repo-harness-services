/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.k8s.client;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.k8s.exception.ClusterCredentialsNotFoundException;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1PodList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class K8sApiClientTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testK8sClientInterfaceMethods() {
    K8sClient k8sClient = new K8sClient() {
      @Override
      public void updateSecretData(
          String accountIdentifier, String namespace, String secretName, Map<String, byte[]> data) {}

      @Override
      public V1ConfigMap updateConfigMapData(
          String namespace, String configMapName, Map<String, String> data, boolean replace) {
        return new V1ConfigMap();
      }

      @Override
      public V1PodList getBackstagePodList(String namespace) {
        return new V1PodList();
      }

      @Override
      public void removeSecretData(String namespace, String backstageSecret, List<String> envNames) {}

      @Override
      public V1Namespace createNamespace(String namespace) {
        return new V1Namespace();
      }

      @Override
      public void createNamespaceForFailoverCluster(String namespace) {}

      @Override
      public void deleteConfigMap(String accountIdentifier, String namespace, String configMapName) {}
    };

    assertNotNull(k8sClient);
    assertNotNull(k8sClient.updateConfigMapData("ns", "cm", Map.of(), false));
    assertNotNull(k8sClient.getBackstagePodList("ns"));
    assertNotNull(k8sClient.createNamespace("ns"));
  }

  @Test(expected = ClusterCredentialsNotFoundException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testClusterCredentialsNotFoundForMasterUrl() {
    throw new ClusterCredentialsNotFoundException("Master URL not found");
  }

  @Test(expected = ClusterCredentialsNotFoundException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testClusterCredentialsNotFoundForToken() {
    throw new ClusterCredentialsNotFoundException("Service Account Token not found");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testK8sClientIsInterface() {
    assertTrue(K8sClient.class.isInterface());
  }
}
