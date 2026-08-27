/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.homepage.mappers;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.homepage.entities.HomePageLayoutEntity;
import io.harness.spec.server.idp.v1.model.Card;
import io.harness.spec.server.idp.v1.model.HomePageLayoutInfo;
import io.harness.spec.server.idp.v1.model.HomePageLayoutResponse;

import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@UtilityClass
public class HomePageLayoutMapper {
  public HomePageLayoutInfo toDTO(HomePageLayoutEntity homePageLayoutEntity, List<Card> cards) {
    HomePageLayoutInfo homePageLayoutInfo = new HomePageLayoutInfo();
    homePageLayoutInfo.setBanner(homePageLayoutEntity.getBanner());
    homePageLayoutInfo.setHeader(homePageLayoutEntity.getHeader());
    homePageLayoutInfo.setCards(cards);
    return homePageLayoutInfo;
  }

  public HomePageLayoutEntity fromDTO(
      HomePageLayoutInfo homePageLayoutInfo, String accountIdentifier, List<String> cardIdentifiers) {
    return HomePageLayoutEntity.builder()
        .banner(homePageLayoutInfo.getBanner())
        .header(homePageLayoutInfo.getHeader())
        .cards(cardIdentifiers)
        .accountIdentifier(accountIdentifier)
        .build();
  }

  public HomePageLayoutResponse toResponse(HomePageLayoutEntity homePageLayoutEntity, List<Card> cards) {
    HomePageLayoutInfo homePageLayoutInfo = toDTO(homePageLayoutEntity, cards);
    HomePageLayoutResponse homePageLayoutResponse = new HomePageLayoutResponse();
    homePageLayoutResponse.setHomePageLayout(homePageLayoutInfo);
    return homePageLayoutResponse;
  }
}
