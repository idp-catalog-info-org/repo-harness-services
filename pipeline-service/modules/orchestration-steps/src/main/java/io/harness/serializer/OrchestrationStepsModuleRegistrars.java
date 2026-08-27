/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.kryo.CommonEntitiesKryoRegistrar;
import io.harness.serializer.kryo.DelegateServiceBeansKryoRegistrar;
import io.harness.serializer.kryo.NGCoreBeansKryoRegistrar;
import io.harness.serializer.kryo.NotificationBeansKryoRegistrar;
import io.harness.serializer.kryo.OrchestrationStepsContractKryoRegistrar;
import io.harness.serializer.kryo.OrchestrationStepsKryoRegistrar;
import io.harness.serializer.kryo.YamlKryoRegistrar;
import io.harness.serializer.morphia.NotificationBeansMorphiaRegistrar;
import io.harness.serializer.morphia.OrchestrationStepsContractMorphiaRegistrar;
import io.harness.serializer.morphia.OrchestrationStepsMorphiaRegistrar;
import io.harness.serializer.morphia.OrchestrationVisualizationMorphiaRegistrar;
import io.harness.serializer.morphia.PMSPipelineMorphiaRegistrar;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.morphia.converters.TypeConverter;
import lombok.experimental.UtilityClass;
import org.springframework.core.convert.converter.Converter;

@UtilityClass
@OwnedBy(PIPELINE)
public class OrchestrationStepsModuleRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(OrchestrationRegistrars.kryoRegistrars)
          .add(OrchestrationStepsKryoRegistrar.class)
          .add(YamlKryoRegistrar.class)
          .add(NGCoreBeansKryoRegistrar.class)
          .add(DelegateServiceBeansKryoRegistrar.class)
          .add(CommonEntitiesKryoRegistrar.class)
          .add(OrchestrationStepsContractKryoRegistrar.class)
          .addAll(PmsCommonsModuleRegistrars.kryoRegistrars)
          .addAll(YamlBeansModuleRegistrars.kryoRegistrars)
          .addAll(RetentionModuleRegistrars.kryoRegistrars)
          .add(NotificationBeansKryoRegistrar.class)
          .addAll(RbacCoreRegistrars.kryoRegistrars)
          .addAll(NGCoreClientRegistrars.kryoRegistrars)
          .addAll(DelegateTaskRegistrars.kryoRegistrars)
          .addAll(ContainerRegistrars.kryoRegistrars)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .add(OrchestrationStepsMorphiaRegistrar.class)
          .add(NotificationBeansMorphiaRegistrar.class)
          .add(OrchestrationStepsContractMorphiaRegistrar.class)
          .addAll(OrchestrationRegistrars.morphiaRegistrars)
          .addAll(RetentionModuleRegistrars.morphiaRegistrars)
          .addAll(YamlBeansModuleRegistrars.morphiaRegistrars)
          .addAll(RbacCoreRegistrars.morphiaRegistrars)
          .addAll(NGCoreClientRegistrars.morphiaRegistrars)
          .addAll(DelegateTaskRegistrars.morphiaRegistrars)
          .addAll(FeatureFlagBeansRegistrars.morphiaRegistrars)
          .addAll(ContainerRegistrars.morphiaRegistrars)
          .add(PMSPipelineMorphiaRegistrar.class)
          .add(OrchestrationVisualizationMorphiaRegistrar.class)
          .build();

  public static final ImmutableSet<Class<? extends TypeConverter>> morphiaConverters =
      ImmutableSet.<Class<? extends TypeConverter>>builder()
          .addAll(OrchestrationBeansRegistrars.morphiaConverters)
          .build();

  public static final ImmutableList<Class<? extends Converter<?, ?>>> springConverters =
      ImmutableList.<Class<? extends Converter<?, ?>>>builder()
          .addAll(OrchestrationRegistrars.springConverters)
          .build();
}
