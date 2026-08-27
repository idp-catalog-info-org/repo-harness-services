/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.branding.serializer.morphia;

import io.harness.branding.entities.Branding;
import io.harness.branding.entities.BrandingAsset;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;

import java.util.Set;

public class BrandingMorphiaRegistrars implements MorphiaRegistrar {
  public void registerClasses(Set<Class> set) {
    set.add(Branding.class);
    set.add(BrandingAsset.class);
  }
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    // no classes for registration
  }
}
