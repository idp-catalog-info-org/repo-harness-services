/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.serializer;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cimanager.serializer.CIContractsKryoRegistrar;
import io.harness.idp.serializer.kryo.IdpServiceKryoRegistrar;
import io.harness.idp.serializer.morphia.IdpServiceMorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.AccessControlClientRegistrars;
import io.harness.serializer.ConnectorBeansRegistrars;
import io.harness.serializer.ConnectorNextGenRegistrars;
import io.harness.serializer.ContainerRegistrars;
import io.harness.serializer.DelegateServiceBeansRegistrars;
import io.harness.serializer.DelegateTaskRegistrars;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.LicenseManagerRegistrars;
import io.harness.serializer.NGCommonModuleRegistrars;
import io.harness.serializer.NGCoreBeansRegistrars;
import io.harness.serializer.ProjectAndOrgRegistrars;
import io.harness.serializer.SMCoreRegistrars;
import io.harness.serializer.SecretManagerClientRegistrars;
import io.harness.serializer.WaitEngineRegistrars;
import io.harness.serializer.YamlBeansModuleRegistrars;
import io.harness.serializer.kryo.ApiServiceBeansKryoRegister;
import io.harness.serializer.kryo.CommonsKryoRegistrar;
import io.harness.serializer.kryo.DelegateBeansKryoRegistrar;
import io.harness.serializer.kryo.DelegateTasksBeansKryoRegister;
import io.harness.serializer.kryo.NGCommonsKryoRegistrar;
import io.harness.serializer.kryo.NgPersistenceKryoRegistrar;
import io.harness.serializer.kryo.NotificationBeansKryoRegistrar;

import com.google.common.collect.ImmutableSet;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(IDP)
public class IdpServiceRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .add(IdpServiceKryoRegistrar.class)
          .add(DelegateTasksBeansKryoRegister.class)
          .add(NGCommonsKryoRegistrar.class)
          .add(CommonsKryoRegistrar.class)
          .add(ApiServiceBeansKryoRegister.class)
          .add(DelegateBeansKryoRegistrar.class)
          .addAll(ConnectorBeansRegistrars.kryoRegistrars)
          .addAll(DelegateTaskRegistrars.kryoRegistrars)

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
          .add(CIContractsKryoRegistrar.class)
          .add(NotificationBeansKryoRegistrar.class)

          .build();
  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder().add(IdpServiceMorphiaRegistrar.class).build();
}
