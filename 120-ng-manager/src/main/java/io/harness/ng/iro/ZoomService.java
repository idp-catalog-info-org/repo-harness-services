/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import io.harness.beans.IdentifierRef;

import clients.iromanager.remote.connectors.zoom.ZoomDeAuthPayload;
import clients.iromanager.remote.connectors.zoom.ZoomOAuthResponse;

public interface ZoomService {
  ZoomOAuthResponse generateZoomAccessToken(IdentifierRef identifierRef, String connectorIdentifier);
  ZoomDeAuthorizationResult deAuthorizeZoomUser(ZoomDeAuthPayload requestDTO);
}