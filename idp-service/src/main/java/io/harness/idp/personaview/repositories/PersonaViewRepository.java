/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.personaview.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.personaview.entities.PersonaViewEntity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface PersonaViewRepository extends CrudRepository<PersonaViewEntity, String>, PersonaViewRepositoryCustom {
  Optional<PersonaViewEntity> findByAccountIdentifierAndIdentifier(String accountIdentifier, String identifier);

  List<PersonaViewEntity> findByAccountIdentifier(String accountIdentifier);

  long deleteByAccountIdentifierAndIdentifier(String accountIdentifier, String identifier);
}
