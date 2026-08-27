/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.COMMA_SEPARATOR;
import static io.harness.idp.common.RbacConstants.IDP_AGGREGATION_RULE;
import static io.harness.idp.common.RbacConstants.IDP_AGGREGATION_RULE_CREATE;
import static io.harness.idp.common.RbacConstants.IDP_AGGREGATION_RULE_VIEW;

import static java.lang.String.format;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.idp.aggregation.rules.mapper.AggregationRulesMapper;
import io.harness.idp.aggregation.rules.service.AggregationRulesService;
import io.harness.idp.common.IdpCommonService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.AggregationRulesApi;
import io.harness.spec.server.idp.v1.model.AggregationRule;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetailsRequest;
import io.harness.spec.server.idp.v1.model.AggregationRuleDetailsResponse;
import io.harness.spec.server.idp.v1.model.AggregationSelectionReviewRequest;
import io.harness.spec.server.idp.v1.model.AggregationSelectionReviewResponse;
import io.harness.utils.PageUtils;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@NextGenManagerAuth
@OwnedBy(HarnessTeam.IDP)
@Slf4j
@Timed
@ResponseMetered
public class AggregationRulesApiImpl implements AggregationRulesApi {
  private final AggregationRulesService aggregationRulesService;
  private final IdpCommonService idpCommonService;
  private static final Map<String, String> ALLOWED_PROPERTIES_API_TO_ENTITY_FIELD_MAP =
      Map.of("name", AggregationRuleEntity.AggregationRuleKeys.name, "last_computed_status",
          AggregationRuleEntity.AggregationRuleKeys.status);

  @Inject
  public AggregationRulesApiImpl(AggregationRulesService aggregationRulesService, IdpCommonService idpCommonService) {
    this.aggregationRulesService = aggregationRulesService;
    this.idpCommonService = idpCommonService;
  }

  @Override
  public Response getAggregationRule(String aggregationRuleId, @AccountIdentifier String harnessAccount) {
    idpCommonService.harnessScopeCheck(harnessAccount);
    try {
      AggregationRuleDetailsResponse aggregationRule =
          aggregationRulesService.getAggregationRule(harnessAccount, aggregationRuleId);
      return Response.status(Response.Status.OK).entity(aggregationRule).build();
    } catch (NotFoundException e) {
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (NGAccessDeniedException e) {
      log.warn("Access denied while fetching aggregation rule: {} in account: {}. Error: {}", aggregationRuleId,
          harnessAccount, e.getMessage());
      return Response.status(Response.Status.FORBIDDEN)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (Exception e) {
      String logMessage =
          String.format("Error occurred while fetching aggregation rule details for accountId: [%s], ruleId: [%s]",
              harnessAccount, aggregationRuleId);
      log.error(logMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message("Failed to fetch aggregation rule").build())
          .build();
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_AGGREGATION_RULE, permission = IDP_AGGREGATION_RULE_VIEW)
  public Response getAggregationRules(
      @AccountIdentifier String harnessAccount, Integer page, Integer limit, String sort, String searchTerm) {
    idpCommonService.harnessScopeCheck(harnessAccount);
    int pageIndex = page == null ? 0 : page;
    int pageLimit = limit == null ? 100 : limit;
    sort = validateSort(sort);
    Pageable pageRequest = isEmpty(sort)
        ? PageRequest.of(pageIndex, pageLimit,
              Sort.by(Sort.Direction.DESC, AggregationRuleEntity.AggregationRuleKeys.lastUpdatedAt))
        : PageUtils.getPageRequest(pageIndex, pageLimit, List.of(sort));
    Page<AggregationRuleEntity> aggregationRuleEntityList =
        aggregationRulesService.getAggregationRules(harnessAccount, pageRequest, searchTerm);
    List<AggregationRule> aggregationRules = new ArrayList<>();
    aggregationRuleEntityList.getContent().forEach(
        aggregationRuleEntity -> aggregationRules.add(AggregationRulesMapper.toSummaryDTO(aggregationRuleEntity)));
    return idpCommonService.buildPageResponse(pageIndex, pageLimit, aggregationRuleEntityList.getTotalElements(),
        AggregationRulesMapper.toResponseList(aggregationRules));
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_AGGREGATION_RULE, permission = IDP_AGGREGATION_RULE_CREATE)
  public Response createAggregationRule(
      @Valid AggregationRuleDetailsRequest body, @AccountIdentifier String harnessAccount) {
    idpCommonService.harnessScopeCheck(harnessAccount);
    try {
      AggregationRuleDetailsResponse aggregationRule =
          aggregationRulesService.createAggregationRule(harnessAccount, body);
      return Response.status(Response.Status.OK).entity(aggregationRule).build();
    } catch (DuplicateKeyException e) {
      String errorMessage = "Aggregation Rule already exists with the same identifier or name";
      log.info("Aggregation Rule conflict for accountId: [{}], identifier: [{}], name: [{}]", harnessAccount,
          body.getAggregationRule().getIdentifier(), body.getAggregationRule().getName());
      return Response.status(Response.Status.CONFLICT)
          .entity(ResponseMessage.builder().message(errorMessage).build())
          .build();
    } catch (Exception e) {
      String logMessage =
          String.format("Error occurred while creating aggregation rule for accountId: [%s], ruleId: [%s]",
              harnessAccount, body.getAggregationRule().getIdentifier());
      log.error(logMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message("Failed to create aggregation rule").build())
          .build();
    }
  }

  @Override
  public Response updateAggregationRule(
      String aggregationRuleId, @Valid AggregationRuleDetailsRequest body, @AccountIdentifier String harnessAccount) {
    idpCommonService.harnessScopeCheck(harnessAccount);
    try {
      if (body == null || body.getAggregationRule() == null || body.getAggregationRule().getIdentifier() == null
          || !aggregationRuleId.equals(body.getAggregationRule().getIdentifier())) {
        String msg = String.format("Path aggregationRuleId [%s] must match body identifier", aggregationRuleId);
        log.info("Update aggregation rule validation failed for accountId: [{}], pathId: [{}], bodyId: [{}]",
            harnessAccount, aggregationRuleId,
            body != null && body.getAggregationRule() != null ? body.getAggregationRule().getIdentifier() : null);
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ResponseMessage.builder().message(msg).build())
            .build();
      }
      AggregationRuleDetailsResponse aggregationRule =
          aggregationRulesService.updateAggregationRule(harnessAccount, body);
      return Response.status(Response.Status.OK).entity(aggregationRule).build();
    } catch (DuplicateKeyException e) {
      String errorMessage = "Aggregation Rule already exists with the same identifier or name";
      log.info("Aggregation Rule conflict for accountId: [{}], identifier: [{}], name: [{}]", harnessAccount,
          body.getAggregationRule().getIdentifier(), body.getAggregationRule().getName());
      return Response.status(Response.Status.CONFLICT)
          .entity(ResponseMessage.builder().message(errorMessage).build())
          .build();
    } catch (BadRequestException e) {
      log.info("Aggregation Rule could not be updated for accountId: [{}], identifier: [{}], name: [{}]",
          harnessAccount, body.getAggregationRule().getIdentifier(), body.getAggregationRule().getName());
      return Response.status(Response.Status.CONFLICT)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (NGAccessDeniedException e) {
      log.warn("Access denied while updating aggregation rule: {} in account: {}. Error: {}", aggregationRuleId,
          harnessAccount, e.getMessage());
      return Response.status(Response.Status.FORBIDDEN)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (Exception e) {
      String logMessage =
          String.format("Error occurred while updating aggregation rule for accountId: [%s], ruleId: [%s]",
              harnessAccount, aggregationRuleId);
      log.error(logMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message("Failed to update aggregation rule").build())
          .build();
    }
  }

  @Override
  public Response deleteAggregationRule(
      String aggregationRuleId, @AccountIdentifier String harnessAccount, Boolean forceDelete) {
    idpCommonService.harnessScopeCheck(harnessAccount);
    try {
      aggregationRulesService.deleteAggregationRule(harnessAccount, aggregationRuleId);
      return Response.status(Response.Status.NO_CONTENT).build();
    } catch (NGAccessDeniedException e) {
      log.warn("Access denied while deleting aggregation rule: {} in account: {}. Error: {}", aggregationRuleId,
          harnessAccount, e.getMessage());
      return Response.status(Response.Status.FORBIDDEN)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (Exception e) {
      String logMessage =
          String.format("Error occurred while deleting aggregation rule for accountId: [%s], ruleId: [%s]",
              harnessAccount, aggregationRuleId);
      log.error(logMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message("Failed to delete aggregation rule").build())
          .build();
    }
  }

  @Override
  public Response reviewAggregationRules(
      @Valid AggregationSelectionReviewRequest body, @AccountIdentifier String harnessAccount) {
    idpCommonService.harnessScopeCheck(harnessAccount);
    try {
      AggregationSelectionReviewResponse aggregationSelectionReviewResponse =
          aggregationRulesService.reviewAggregationRuleSelection(harnessAccount, body);
      return Response.status(Response.Status.OK).entity(aggregationSelectionReviewResponse).build();
    } catch (Exception e) {
      String logMessage =
          String.format("Error occurred while reviewing aggregation rules for accountId: [%s]", harnessAccount);
      log.error(logMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message("Failed to review aggregation rules").build())
          .build();
    }
  }

  @Override
  public Response triggerAggregationRuleComputation(
      String aggregationRuleId, @AccountIdentifier String harnessAccount) {
    idpCommonService.harnessScopeCheck(harnessAccount);
    try {
      aggregationRulesService.triggerComputation(harnessAccount, aggregationRuleId);
      return Response.status(Response.Status.OK).build();
    } catch (NGAccessDeniedException e) {
      log.warn("Access denied while triggering computation for aggregation rule: {} in account: {}. Error: {}",
          aggregationRuleId, harnessAccount, e.getMessage());
      return Response.status(Response.Status.FORBIDDEN)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    } catch (Exception e) {
      String logMessage = String.format(
          "Error occurred while triggering aggregation rule computation for accountId: [%s], ruleId: [%s]",
          harnessAccount, aggregationRuleId);
      log.error(logMessage, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message("Failed to trigger aggregation rule computation").build())
          .build();
    }
  }

  private String validateSort(String sort) {
    if (!isEmpty(sort)) {
      String[] sortSplit = sort.split(COMMA_SEPARATOR);
      if (sortSplit.length == 0 || isEmpty(sortSplit[0])) {
        throw new InvalidArgumentsException("Invalid sort parameter: sort property cannot be empty");
      }

      if (!ALLOWED_PROPERTIES_API_TO_ENTITY_FIELD_MAP.containsKey(sortSplit[0])) {
        throw new InvalidArgumentsException(format("Invalid sort property: %s", sortSplit[0]));
      }
      String mappedField = ALLOWED_PROPERTIES_API_TO_ENTITY_FIELD_MAP.get(sortSplit[0]);
      sort = sort.replace(sortSplit[0], mappedField);
    }
    return sort;
  }
}
