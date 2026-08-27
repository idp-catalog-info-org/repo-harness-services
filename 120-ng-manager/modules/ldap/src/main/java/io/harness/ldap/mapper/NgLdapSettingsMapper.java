/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.mapper;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.encryption.SecretRefData;
import io.harness.ldap.entity.NGLdapSettings;
import io.harness.spec.server.ng.v1.model.LdapConnectionSettingsDTO;
import io.harness.spec.server.ng.v1.model.LdapGroupSettingsDTO;
import io.harness.spec.server.ng.v1.model.LdapSettingsDTO;
import io.harness.spec.server.ng.v1.model.LdapUserSettingsDTO;
import io.harness.utils.IdentifierRefHelper;

import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapGroupSettings;
import software.wings.beans.sso.LdapUserSettings;
import software.wings.beans.sso.SSOType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;

@OwnedBy(PL)
@Slf4j
public class NgLdapSettingsMapper {
  public NGLdapSettings ngLdapSettings(LdapSettingsDTO ldapSettingsDTO) {
    return NGLdapSettings.builder()
        .accountIdentifier(ldapSettingsDTO.getAccountIdentifier())
        .identifier(ldapSettingsDTO.getIdentifier())
        .name(ldapSettingsDTO.getName())
        .disabled(BooleanUtils.isTrue(ldapSettingsDTO.isDisabled()))
        .cronExpression(ldapSettingsDTO.getCronExpression())
        .type(SSOType.LDAP)
        .url(ldapSettingsDTO.getUrl())
        .connectionSettings(mapToLdapConnectionSettingsEntity(
            ldapSettingsDTO.getLdapConnectionSettings(), ldapSettingsDTO.getAccountIdentifier()))
        .groupSettingsList(ldapGroupSettingsEntityList(ldapSettingsDTO.getLdapGroupSettings()))
        .userSettingsList(ldapUserSettingsEntityList(ldapSettingsDTO.getLdapUserSettings()))
        .build();
  }

  public LdapSettingsDTO toLdapSettingsDTO(NGLdapSettings ngLdapSettings) {
    LdapSettingsDTO dto = new LdapSettingsDTO();
    dto.setAccountIdentifier(ngLdapSettings.getAccountIdentifier());
    dto.setIdentifier(ngLdapSettings.getIdentifier());
    dto.setName(ngLdapSettings.getName());
    dto.setDisabled(ngLdapSettings.isDisabled());
    dto.setCronExpression(ngLdapSettings.getCronExpression());
    dto.setUrl(ngLdapSettings.getUrl());
    dto.setLdapConnectionSettings(mapToLdapConnectionSettingsDTO(ngLdapSettings.getConnectionSettings()));
    dto.setLdapUserSettings(toLdapUserSettingsDtoList(ngLdapSettings.getUserSettingsList()));
    dto.setLdapGroupSettings(toLdapGroupSettingsDtoList(ngLdapSettings.getGroupSettingsList()));
    dto.setSsoType(ngLdapSettings.getType().name());
    return dto;
  }

  public NGLdapSettings toNgLdapSettingsFromCG(software.wings.beans.sso.LdapSettingsDTO ldapSettings) {
    String identifier = (ldapSettings.getDisplayName().trim()).replace(' ', '_');
    return NGLdapSettings.builder()
        .accountIdentifier(ldapSettings.getAccountId())
        .identifier(identifier)
        .name(ldapSettings.getDisplayName())
        .disabled(ldapSettings.isDisabled())
        .cronExpression(ldapSettings.getCronExpression())
        .type(SSOType.LDAP)
        .url(ldapSettings.getUrl())
        .connectionSettings(ldapSettings.getConnectionSettings())
        .groupSettingsList(ldapSettings.getGroupSettingsList())
        .userSettingsList(ldapSettings.getUserSettingsList())
        .build();
  }

  private LdapConnectionSettings mapToLdapConnectionSettingsEntity(LdapConnectionSettingsDTO dto, String accountId) {
    LdapConnectionSettings entity = new LdapConnectionSettings();
    entity.setHost(dto.getHost());
    entity.setPort(dto.getPort());
    entity.setSslEnabled(BooleanUtils.isTrue(dto.isSslEnabled()));
    if (dto.isReferralsEnabled() != null) {
      entity.setReferralsEnabled(dto.isReferralsEnabled());
    }
    if (dto.getMaxReferralHops() != null) {
      entity.setMaxReferralHops(dto.getMaxReferralHops());
    }
    entity.setConnectTimeout(dto.getConnectionTimeout());
    entity.setResponseTimeout(dto.getResponseTimeout());
    entity.setBindDN(dto.getBindDN());
    entity.setUseRecursiveGroupMembershipSearch(dto.isUseRecursiveGroupMembershipSearch());
    // we only accept account level secrets for ldap
    IdentifierRef secretIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(dto.getSecretRefPath(), accountId, null, null);
    entity.setPasswordRef(SecretRefData.builder()
                              .identifier(secretIdentifierRef.getIdentifier())
                              .scope(secretIdentifierRef.getScope())
                              .build());

    if (isNotEmpty(dto.getDelegateSelectors())) {
      Set<String> delegateSelectors = new HashSet<>(dto.getDelegateSelectors());
      entity.setDelegateSelectors(delegateSelectors);
    }
    return entity;
  }

  private LdapConnectionSettingsDTO mapToLdapConnectionSettingsDTO(LdapConnectionSettings entity) {
    LdapConnectionSettingsDTO dto = new LdapConnectionSettingsDTO();
    dto.setHost(entity.getHost());
    dto.setPort(entity.getPort());
    dto.setSslEnabled(entity.isSslEnabled());
    dto.setReferralsEnabled(BooleanUtils.isTrue(entity.isReferralsEnabled()));
    dto.setMaxReferralHops(entity.getMaxReferralHops());
    dto.setConnectionTimeout(entity.getConnectTimeout());
    dto.setResponseTimeout(entity.getResponseTimeout());
    dto.setUseRecursiveGroupMembershipSearch(entity.getUseRecursiveGroupMembershipSearch());
    dto.setBindDN(entity.getBindDN());
    if (isNotEmpty(entity.getDelegateSelectors())) {
      List<String> delegateSelectors = new ArrayList<>(entity.getDelegateSelectors());
      dto.setDelegateSelectors(delegateSelectors);
    }
    if (entity.getPasswordRef() != null) {
      dto.setSecretRefPath(entity.getPasswordRef().toSecretRefStringValue());
    }
    return dto;
  }

  private List<LdapUserSettings> ldapUserSettingsEntityList(List<LdapUserSettingsDTO> ldapUserSettingsDTOList) {
    return ldapUserSettingsDTOList.stream().map(this::mapToLdapUserSettingsEntity).collect(Collectors.toList());
  }

  public LdapUserSettings mapToLdapUserSettingsEntity(LdapUserSettingsDTO dto) {
    LdapUserSettings entity = new LdapUserSettings();
    entity.setBaseDN(dto.getBaseDN());
    entity.setSearchFilter(dto.getSearchFilter());
    entity.setUidAttr(dto.getUidAttr());
    entity.setSamAccountNameAttr(dto.getSamAccountNameAttr());
    entity.setEmailAttr(dto.getEmailAttr());
    entity.setDisplayNameAttr(dto.getDisplayNameAttr());
    entity.setGroupMembershipAttr(dto.getGroupMembershipAttr());
    return entity;
  }

  private List<LdapUserSettingsDTO> toLdapUserSettingsDtoList(List<LdapUserSettings> ldapUserSettingsList) {
    return ldapUserSettingsList.stream().map(this::mapToLdapUserSettingsDTO).collect(Collectors.toList());
  }

  public LdapUserSettingsDTO mapToLdapUserSettingsDTO(LdapUserSettings entity) {
    LdapUserSettingsDTO dto = new LdapUserSettingsDTO();
    dto.setBaseDN(entity.getBaseDN());
    dto.setSearchFilter(entity.getSearchFilter());
    dto.setUidAttr(entity.getUidAttr());
    dto.setSamAccountNameAttr(entity.getSamAccountNameAttr());
    dto.setEmailAttr(entity.getEmailAttr());
    dto.setDisplayNameAttr(entity.getDisplayNameAttr());
    dto.setGroupMembershipAttr(entity.getGroupMembershipAttr());
    return dto;
  }

  private List<LdapGroupSettings> ldapGroupSettingsEntityList(List<LdapGroupSettingsDTO> ldapGroupSettingsDTOList) {
    return ldapGroupSettingsDTOList.stream().map(this::mapToLdapGroupSettingsEntity).collect(Collectors.toList());
  }

  public LdapGroupSettings mapToLdapGroupSettingsEntity(LdapGroupSettingsDTO dto) {
    LdapGroupSettings entity = new LdapGroupSettings();
    entity.setBaseDN(dto.getBaseDN());
    entity.setSearchFilter(dto.getSearchFilter());
    entity.setNameAttr(dto.getNameAttr());
    entity.setDescriptionAttr(dto.getDescriptionAttr());
    entity.setUserMembershipAttr(dto.getUserMembershipAttr());
    entity.setReferencedUserAttr(dto.getReferencedUserAttr());
    return entity;
  }

  private List<LdapGroupSettingsDTO> toLdapGroupSettingsDtoList(List<LdapGroupSettings> ldapGroupSettingsList) {
    return ldapGroupSettingsList.stream().map(this::mapToLdapGroupSettingsDTO).collect(Collectors.toList());
  }

  public LdapGroupSettingsDTO mapToLdapGroupSettingsDTO(LdapGroupSettings entity) {
    LdapGroupSettingsDTO dto = new LdapGroupSettingsDTO();
    dto.setBaseDN(entity.getBaseDN());
    dto.setSearchFilter(entity.getSearchFilter());
    dto.setNameAttr(entity.getNameAttr());
    dto.setDescriptionAttr(entity.getDescriptionAttr());
    dto.setUserMembershipAttr(entity.getUserMembershipAttr());
    dto.setReferencedUserAttr(entity.getReferencedUserAttr());
    return dto;
  }
}
