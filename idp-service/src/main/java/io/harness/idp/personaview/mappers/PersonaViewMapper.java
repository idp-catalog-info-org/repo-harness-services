/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.personaview.mappers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.catalog.utils.Constants.GROUP_KIND;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.personaview.entities.PersonaViewEntity;
import io.harness.spec.server.idp.v1.model.PersonaView;
import io.harness.spec.server.idp.v1.model.PersonaViewResponse;
import io.harness.spec.server.idp.v1.model.SavePersonaViewBody;
import io.harness.spec.server.idp.v1.model.UserGroupRef;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.IDP)
public class PersonaViewMapper {
  private final CatalogEntityRepository catalogEntityRepository;

  @Inject
  public PersonaViewMapper(CatalogEntityRepository catalogEntityRepository) {
    this.catalogEntityRepository = catalogEntityRepository;
  }

  /**
   * Build the DTO without resolved card objects — the caller is responsible for setting {@code personaView.cards}
   * after resolving the entity's card identifier list through {@code CardService}.
   */
  public PersonaView toDto(PersonaViewEntity entity) {
    PersonaView personaView = new PersonaView();
    personaView.setIdentifier(entity.getIdentifier());
    personaView.setName(entity.getName());
    personaView.setDescription(entity.getDescription());
    personaView.setOotb(Boolean.TRUE.equals(entity.getOotb()));
    personaView.setUserGroupRefs(resolveUserGroupRefs(entity.getAccountIdentifier(), entity.getUserGroupIdentifiers()));
    personaView.setHeader(entity.getHeader());
    personaView.setBanner(entity.getBanner());
    personaView.setCreatedAt(entity.getCreatedAt());
    personaView.setLastUpdatedAt(entity.getLastUpdatedAt());
    return personaView;
  }

  public PersonaViewEntity fromSaveBody(
      SavePersonaViewBody savePersonaViewBody, String accountIdentifier, String identifier, boolean ootb) {
    List<String> cardIdentifiers = isEmpty(savePersonaViewBody.getCards())
        ? List.of()
        : savePersonaViewBody.getCards()
              .stream()
              .map(io.harness.spec.server.idp.v1.model.Card::getIdentifier)
              .collect(Collectors.toList());
    return PersonaViewEntity.builder()
        .accountIdentifier(accountIdentifier)
        .identifier(identifier)
        .name(savePersonaViewBody.getName())
        .description(savePersonaViewBody.getDescription())
        .userGroupIdentifiers(savePersonaViewBody.getUserGroupIdentifiers())
        .cards(cardIdentifiers)
        .header(savePersonaViewBody.getHeader())
        .banner(savePersonaViewBody.getBanner())
        .ootb(ootb)
        .build();
  }

  public PersonaViewResponse toResponse(PersonaView personaView) {
    return new PersonaViewResponse().personaView(personaView);
  }

  /**
   * Resolve user-group identifiers stored on the persona view into {@link UserGroupRef}s (identifier + name).
   *
   * <p>User groups are mirrored into IDP's {@code catalog} collection by
   * {@link io.harness.idp.catalog.iteratorhandler.HarnessToIDPUserGroupSyncHandler} as rows with
   * {@code kind=group} and {@code parentUniqueId=accountIdentifier}. Resolving against the local mirror avoids a
   * cross-service NG call on every persona view read and keeps this path cheap on the hot list/get APIs. Groups
   * that have not yet been synced (or were just deleted in NG) are dropped silently — the caller treats them as
   * unresolved ACL entries, which is the same behavior an out-of-sync NG response would produce.
   */
  public List<UserGroupRef> resolveUserGroupRefs(String accountIdentifier, List<String> userGroupIdentifiers) {
    if (isEmpty(userGroupIdentifiers)) {
      return Collections.emptyList();
    }
    List<CatalogEntity> catalogEntities = catalogEntityRepository.findAllByParentUniqueIdAndKindAndIdentifierIn(
        accountIdentifier, GROUP_KIND, userGroupIdentifiers);
    if (isEmpty(catalogEntities)) {
      return Collections.emptyList();
    }
    Map<String, CatalogEntity> byIdentifier = catalogEntities.stream().collect(
        Collectors.toMap(CatalogEntity::getIdentifier, Function.identity(), (a, b) -> a));
    List<UserGroupRef> refs = new ArrayList<>();
    for (String userGroupIdentifier : userGroupIdentifiers) {
      CatalogEntity catalogEntity = byIdentifier.get(userGroupIdentifier);
      if (catalogEntity != null) {
        refs.add(new UserGroupRef().identifier(catalogEntity.getIdentifier()).name(catalogEntity.getName()));
      }
    }
    return refs;
  }
}
