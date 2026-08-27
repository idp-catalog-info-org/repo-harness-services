/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.serializer;

import io.harness.branding.serializer.morphia.BrandingMorphiaRegistrars;
import io.harness.morphia.MorphiaRegistrar;

import com.google.common.collect.ImmutableSet;

public class BrandingRegistrars {
  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder().add(BrandingMorphiaRegistrars.class).build();
}
