/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.ngsubscriptions.entity.DailyAccountUsers;
import io.harness.ngsubscriptions.entity.ModuleAccess;
import io.harness.ngsubscriptions.entity.TotalAccountUsers;

import java.util.Set;

public class NgSubscriptionsMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(TotalAccountUsers.class);
    set.add(ModuleAccess.class);
    set.add(DailyAccountUsers.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    // no classes for registration
  }
}
