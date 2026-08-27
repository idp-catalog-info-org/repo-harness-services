/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.factory;

import static io.harness.idp.scorecard.datapoints.constants.DataPoints.AVG_RESOLVED_TIME_FOR_LAST_TEN_RESOLVED_INCIDENTS_IN_MINUTES;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.IS_ESCALATION_POLICY_SET;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.IS_ON_CALL_SET;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.NO_OF_INCIDENTS_IN_LAST_THIRTY_DAYS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.datapoints.parser.DefaultCatalogDSLParser;
import io.harness.idp.scorecard.datapoints.parser.DefaultHQLParser;
import io.harness.idp.scorecard.datapoints.parser.pagerduty.PagerDutyAvgResolvedTimeForLastTenResolvedIncidents;
import io.harness.idp.scorecard.datapoints.parser.pagerduty.PagerDutyIsEscalationPolicySetParser;
import io.harness.idp.scorecard.datapoints.parser.pagerduty.PagerDutyNoOfIncidentsInLastThirtyDaysParser;
import io.harness.idp.scorecard.datapoints.parser.pagerduty.PagerDutyOnCallSetParser;
import io.harness.idp.scorecard.datasourcelocations.beans.DataSourceLocationType;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.IDP)
public class PagerDutyDataPointParserFactory extends IntegrationDataPointParserFactory {
  private final PagerDutyOnCallSetParser pagerDutyOnCallSetParser;
  private final PagerDutyIsEscalationPolicySetParser pagerDutyIsEscalationPolicySetParser;
  private final PagerDutyNoOfIncidentsInLastThirtyDaysParser pagerDutyNoOfIncidentsInLastThirtyDaysParser;
  private final PagerDutyAvgResolvedTimeForLastTenResolvedIncidents pagerDutyAvgResolvedTimeForLastTenResolvedIncidents;

  @Inject
  public PagerDutyDataPointParserFactory(PagerDutyOnCallSetParser pagerDutyOnCallSetParser,
      PagerDutyIsEscalationPolicySetParser pagerDutyIsEscalationPolicySetParser,
      PagerDutyNoOfIncidentsInLastThirtyDaysParser pagerDutyNoOfIncidentsInLastThirtyDaysParser,
      PagerDutyAvgResolvedTimeForLastTenResolvedIncidents pagerDutyAvgResolvedTimeForLastTenResolvedIncidents,
      DefaultCatalogDSLParser defaultCatalogDSLParser, DefaultHQLParser defaultHQLParser) {
    super(defaultHQLParser, defaultCatalogDSLParser);
    this.pagerDutyOnCallSetParser = pagerDutyOnCallSetParser;
    this.pagerDutyIsEscalationPolicySetParser = pagerDutyIsEscalationPolicySetParser;
    this.pagerDutyNoOfIncidentsInLastThirtyDaysParser = pagerDutyNoOfIncidentsInLastThirtyDaysParser;
    this.pagerDutyAvgResolvedTimeForLastTenResolvedIncidents = pagerDutyAvgResolvedTimeForLastTenResolvedIncidents;
  }

  public DataPointParser getParser(String identifier, DataSourceLocationType dataSourceLocationType) {
    switch (identifier) {
      case IS_ON_CALL_SET:
        return pagerDutyOnCallSetParser;
      case IS_ESCALATION_POLICY_SET:
        return pagerDutyIsEscalationPolicySetParser;
      case NO_OF_INCIDENTS_IN_LAST_THIRTY_DAYS:
        return pagerDutyNoOfIncidentsInLastThirtyDaysParser;
      case AVG_RESOLVED_TIME_FOR_LAST_TEN_RESOLVED_INCIDENTS_IN_MINUTES:
        return pagerDutyAvgResolvedTimeForLastTenResolvedIncidents;
      default:
        return super.getParser(identifier, dataSourceLocationType);
    }
  }
}
