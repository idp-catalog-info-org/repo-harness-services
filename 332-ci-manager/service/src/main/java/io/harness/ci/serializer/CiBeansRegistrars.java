/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ci.execution.serializer.morphia.CIExecutionMorphiaRegistrar;
import io.harness.cimanager.serializer.CIContractsKryoRegistrar;
import io.harness.cimanager.serializer.CIContractsMorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.oidc.entities.OidcRegistrars;
import io.harness.serializer.common.CommonsRegistrars;
import io.harness.serializer.kryo.CIBeansKryoRegistrar;
import io.harness.serializer.kryo.NgPersistenceKryoRegistrar;
import io.harness.serializer.kryo.NotificationBeansKryoRegistrar;
import io.harness.serializer.morphia.CIBeansMorphiaRegistrar;
import io.harness.serializer.morphia.NgPersistenceMorphiaRegistrar;
import io.harness.serializer.morphia.NotificationBeansMorphiaRegistrar;
import io.harness.serializer.morphia.YamlMorphiaRegistrar;
import io.harness.unified.cd.infrastructure.InfraConfig;
import io.harness.unified.cd.service.spec.ServiceConfig;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(HarnessTeam.CI)
public class CiBeansRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(ProjectAndOrgRegistrars.kryoRegistrars)
          .addAll(NGCoreBeansRegistrars.kryoRegistrars)
          .addAll(SecretManagerClientRegistrars.kryoRegistrars)
          .addAll(ConnectorBeansRegistrars.kryoRegistrars)
          .addAll(YamlBeansModuleRegistrars.kryoRegistrars)
          .addAll(NGCommonModuleRegistrars.kryoRegistrars)
          .addAll(DelegateTaskRegistrars.kryoRegistrars)
          .addAll(WaitEngineRegistrars.kryoRegistrars)
          .addAll(DelegateServiceBeansRegistrars.kryoRegistrars)
          .addAll(AccessControlClientRegistrars.kryoRegistrars)
          .addAll(DelegateTaskRegistrars.kryoRegistrars)
          .addAll(SMCoreRegistrars.kryoRegistrars)
          .addAll(ConnectorNextGenRegistrars.kryoRegistrars)
          .addAll(LicenseManagerRegistrars.kryoRegistrars)
          .addAll(ContainerRegistrars.kryoRegistrars)
          .add(NgPersistenceKryoRegistrar.class)
          .add(CIBeansKryoRegistrar.class)
          .add(CIContractsKryoRegistrar.class)
          .add(NotificationBeansKryoRegistrar.class)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .addAll(ProjectAndOrgRegistrars.morphiaRegistrars)
          .addAll(NGCoreBeansRegistrars.morphiaRegistrars)
          .addAll(SecretManagerClientRegistrars.morphiaRegistrars)
          .addAll(YamlBeansModuleRegistrars.morphiaRegistrars)
          .addAll(NGCommonModuleRegistrars.morphiaRegistrars)
          .addAll(DelegateTaskRegistrars.morphiaRegistrars)
          .addAll(WaitEngineRegistrars.morphiaRegistrars)
          .addAll(DelegateServiceBeansRegistrars.morphiaRegistrars)
          .addAll(AccessControlClientRegistrars.morphiaRegistrars)
          .addAll(DelegateTaskRegistrars.morphiaRegistrars)
          .addAll(CommonsRegistrars.morphiaRegistrars)
          .addAll(ConnectorNextGenRegistrars.morphiaRegistrars)
          .addAll(LicenseManagerRegistrars.morphiaRegistrars)
          .addAll(PrimaryVersionManagerRegistrars.morphiaRegistrars)
          .addAll(FeatureFlagBeansRegistrars.morphiaRegistrars)
          .addAll(ContainerRegistrars.morphiaRegistrars)
          .addAll(OidcRegistrars.morphiaRegistrars)
          .add(NotificationBeansMorphiaRegistrar.class)
          .add(CIBeansMorphiaRegistrar.class)
          .add(CIContractsMorphiaRegistrar.class)
          .add(CIExecutionMorphiaRegistrar.class)
          .add(YamlMorphiaRegistrar.class)
          .add(NgPersistenceMorphiaRegistrar.class)
          .build();

  public static final ImmutableList<YamlSchemaRootClass> yamlSchemaRegistrars =
      ImmutableList.<YamlSchemaRootClass>builder()
          .addAll(ConnectorNextGenRegistrars.yamlSchemaRegistrars)
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.SERVICE)
                   .availableAtProjectLevel(true)
                   .availableAtAccountLevel(true)
                   .availableAtOrgLevel(true)
                   .clazz(ServiceConfig.class)
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.INFRASTRUCTURE)
                   .availableAtProjectLevel(true)
                   .availableAtAccountLevel(true)
                   .availableAtOrgLevel(true)
                   .clazz(InfraConfig.class)
                   .build())
          .build();
}
