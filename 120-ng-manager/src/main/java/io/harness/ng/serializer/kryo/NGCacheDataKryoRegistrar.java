/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.serializer.kryo;

import io.harness.ng.chaos.ChaosCleanupNotifyCallback;
import io.harness.ng.chaos.ChaosNotifyCallback;
import io.harness.ng.core.api.cache.JwtTokenPublicKeysJsonData;
import io.harness.ng.core.api.cache.JwtTokenScimAccountSettingsData;
import io.harness.ng.core.api.cache.JwtTokenServiceAccountData;
import io.harness.ng.runner.scheduledtask.response.driftdetection.DriftDetectionDiffNotifyCallback;
import io.harness.ng.runner.scheduledtask.response.driftdetection.DriftDetectionFetchValuesNotifyCallback;
import io.harness.ng.servicediscovery.ServiceDiscoveryNotifyCallback;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingSource;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

public class NGCacheDataKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(JwtTokenScimAccountSettingsData.class, 10000218);
    kryo.register(JwtTokenServiceAccountData.class, 10000219);
    kryo.register(JwtTokenPublicKeysJsonData.class, 10000220);
    kryo.register(SettingDTO.class, 10000221);
    kryo.register(SettingResponseDTO.class, 10000222);
    kryo.register(SettingCategory.class, 10000224);
    kryo.register(SettingValueType.class, 10000225);
    kryo.register(SettingSource.class, 10000226);
    kryo.register(DriftDetectionDiffNotifyCallback.class, 10000532);
    kryo.register(DriftDetectionFetchValuesNotifyCallback.class, 10000533);
    kryo.register(ChaosNotifyCallback.class, 10000534);
    kryo.register(ChaosCleanupNotifyCallback.class, 10000535);
    kryo.register(ServiceDiscoveryNotifyCallback.class, 10000536);
  }
}
