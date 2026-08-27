/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.common.accountdetails.cache.memory;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.UnexpectedException;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.common.accountdetails.AccountDetailsDTO;
import io.harness.idp.common.accountdetails.AccountsDetailsCache;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Singleton
@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class AccountsDetailsInMemoryCache implements AccountsDetailsCache {
  private static final long MAX_CACHE_SIZE = 1000;
  @Inject IdpCommonService idpCommonService;

  LoadingCache<String, AccountDetailsDTO> cache =
      CacheBuilder.newBuilder().maximumSize(MAX_CACHE_SIZE).build(new CacheLoader<>() {
        @Override
        public AccountDetailsDTO load(@NotNull String accountIdentifier) {
          return AccountDetailsDTO.builder()
              .subdomainUrl(idpCommonService.getAccountDTO(accountIdentifier).getSubdomainURL())
              .build();
        }
      });

  @Override
  public AccountDetailsDTO get(String accountIdentifier) {
    AccountDetailsDTO accountDetailsDTO;
    try {
      accountDetailsDTO = cache.get(accountIdentifier);
    } catch (ExecutionException e) {
      log.error("Error in fetching Account Details. Error = {}", e.getMessage(), e);
      throw new UnexpectedException(e.getMessage());
    }
    return accountDetailsDTO;
  }

  @Override
  public void put(String accountIdentifier, AccountDetailsDTO accountDetailsDTO) {
    cache.put(accountIdentifier, accountDetailsDTO);
  }
}
