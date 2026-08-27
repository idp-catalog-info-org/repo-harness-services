/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.agent;

import static io.harness.rule.OwnerRule.NISARG;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.clients.IdpAgentClient;
import io.harness.clients.IdpAgentSearchTechDocsRequest;
import io.harness.clients.IdpAgentSearchTechDocsResponse;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.proxy.agent.resource.IdpAgentProxyApiImpl;
import io.harness.remote.client.RestResponse;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.MatchingDoc;
import io.harness.spec.server.idp.v1.model.SearchTechDocsRequest;
import io.harness.spec.server.idp.v1.model.SearchTechDocsResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.ws.rs.core.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;

@OwnedBy(HarnessTeam.IDP)
public class IdpAgentProxyApiImplTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "test-account";
  private static final String TEST_QUERY = "test query";
  private static final String ENTITY_ID = "test-entity";
  private static final String KIND = "Component";
  private static final String SCOPE = "default";
  private static final String CONTENT = "test content";
  private static final String DOC_PATH = "/docs/test";

  AutoCloseable openMocks;

  @Mock IdpAgentClient idpAgentClient;
  @Mock CatalogServiceHelper catalogServiceHelper;
  @Mock Call<RestResponse<IdpAgentSearchTechDocsResponse>> call;

  @InjectMocks IdpAgentProxyApiImpl idpAgentProxyApiImpl;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSemanticSearchTechDocsSuccess() throws Exception {
    SearchTechDocsRequest request = new SearchTechDocsRequest();
    request.setQuery(TEST_QUERY);

    IdpAgentSearchTechDocsResponse agentResponse = new IdpAgentSearchTechDocsResponse();
    List<IdpAgentSearchTechDocsResponse.TechDoc> docs = new ArrayList<>();
    IdpAgentSearchTechDocsResponse.TechDoc doc = new IdpAgentSearchTechDocsResponse.TechDoc();
    doc.setEntityId(ENTITY_ID);
    doc.setKind(KIND);
    doc.setScope(SCOPE);
    doc.setContent(CONTENT);
    doc.setDocPath(DOC_PATH);
    docs.add(doc);
    agentResponse.setDocs(docs);

    Set<String> allowedEntityRefs = new HashSet<>();
    allowedEntityRefs.add(KIND + ":" + SCOPE + "/" + ENTITY_ID);

    when(idpAgentClient.searchTechDocs(any(IdpAgentSearchTechDocsRequest.class))).thenReturn(call);
    when(call.execute()).thenReturn(retrofit2.Response.success(new RestResponse<>(agentResponse)));
    when(catalogServiceHelper.checkEntityRefsPermission(eq(ACCOUNT_IDENTIFIER), any(), eq("view")))
        .thenReturn(allowedEntityRefs);

    Response response = idpAgentProxyApiImpl.semanticSearchTechDocs(request, ACCOUNT_IDENTIFIER);

    assertEquals(200, response.getStatus());
    SearchTechDocsResponse responseEntity = (SearchTechDocsResponse) response.getEntity();
    assertEquals(1, responseEntity.size());
    MatchingDoc matchingDoc = responseEntity.get(0);
    assertEquals(ENTITY_ID, matchingDoc.getEntityId());
    assertEquals(KIND, matchingDoc.getKind());
    assertEquals(SCOPE, matchingDoc.getScope());
    assertEquals(CONTENT, matchingDoc.getContent());
    assertEquals(DOC_PATH, matchingDoc.getDocPath());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSemanticSearchTechDocsEmptyResponse() throws Exception {
    SearchTechDocsRequest request = new SearchTechDocsRequest();
    request.setQuery(TEST_QUERY);

    IdpAgentSearchTechDocsResponse agentResponse = new IdpAgentSearchTechDocsResponse();
    agentResponse.setDocs(null);

    when(idpAgentClient.searchTechDocs(any(IdpAgentSearchTechDocsRequest.class))).thenReturn(call);
    when(call.execute()).thenReturn(retrofit2.Response.success(new RestResponse<>(agentResponse)));

    Response response = idpAgentProxyApiImpl.semanticSearchTechDocs(request, ACCOUNT_IDENTIFIER);

    assertEquals(200, response.getStatus());
    SearchTechDocsResponse responseEntity = (SearchTechDocsResponse) response.getEntity();
    assertEquals(0, responseEntity.size());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSemanticSearchTechDocsFilteredByPermissions() throws Exception {
    SearchTechDocsRequest request = new SearchTechDocsRequest();
    request.setQuery(TEST_QUERY);

    IdpAgentSearchTechDocsResponse agentResponse = new IdpAgentSearchTechDocsResponse();
    List<IdpAgentSearchTechDocsResponse.TechDoc> docs = new ArrayList<>();
    IdpAgentSearchTechDocsResponse.TechDoc doc = new IdpAgentSearchTechDocsResponse.TechDoc();
    doc.setEntityId(ENTITY_ID);
    doc.setKind(KIND);
    doc.setScope(SCOPE);
    doc.setContent(CONTENT);
    doc.setDocPath(DOC_PATH);
    docs.add(doc);
    agentResponse.setDocs(docs);

    Set<String> allowedEntityRefs = new HashSet<>();

    when(idpAgentClient.searchTechDocs(any(IdpAgentSearchTechDocsRequest.class))).thenReturn(call);
    when(call.execute()).thenReturn(retrofit2.Response.success(new RestResponse<>(agentResponse)));
    when(catalogServiceHelper.checkEntityRefsPermission(eq(ACCOUNT_IDENTIFIER), any(), eq("view")))
        .thenReturn(allowedEntityRefs);

    Response response = idpAgentProxyApiImpl.semanticSearchTechDocs(request, ACCOUNT_IDENTIFIER);

    assertEquals(200, response.getStatus());
    SearchTechDocsResponse responseEntity = (SearchTechDocsResponse) response.getEntity();
    assertEquals(0, responseEntity.size());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSemanticSearchTechDocsException() throws Exception {
    SearchTechDocsRequest request = new SearchTechDocsRequest();
    request.setQuery(TEST_QUERY);

    when(idpAgentClient.searchTechDocs(any(IdpAgentSearchTechDocsRequest.class)))
        .thenThrow(new RuntimeException("Test exception"));

    Response response = idpAgentProxyApiImpl.semanticSearchTechDocs(request, ACCOUNT_IDENTIFIER);

    assertEquals(500, response.getStatus());
    assertEquals("Error processing search request", response.getEntity());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testSemanticSearchTechDocsMultipleDocsForEntity() throws Exception {
    SearchTechDocsRequest request = new SearchTechDocsRequest();
    request.setQuery(TEST_QUERY);

    IdpAgentSearchTechDocsResponse agentResponse = new IdpAgentSearchTechDocsResponse();
    List<IdpAgentSearchTechDocsResponse.TechDoc> docs = new ArrayList<>();

    IdpAgentSearchTechDocsResponse.TechDoc doc1 = new IdpAgentSearchTechDocsResponse.TechDoc();
    doc1.setEntityId(ENTITY_ID);
    doc1.setKind(KIND);
    doc1.setScope(SCOPE);
    doc1.setContent("content1");
    doc1.setDocPath("/docs/test1");
    docs.add(doc1);

    IdpAgentSearchTechDocsResponse.TechDoc doc2 = new IdpAgentSearchTechDocsResponse.TechDoc();
    doc2.setEntityId(ENTITY_ID);
    doc2.setKind(KIND);
    doc2.setScope(SCOPE);
    doc2.setContent("content2");
    doc2.setDocPath("/docs/test2");
    docs.add(doc2);

    agentResponse.setDocs(docs);

    Set<String> allowedEntityRefs = new HashSet<>();
    allowedEntityRefs.add(KIND + ":" + SCOPE + "/" + ENTITY_ID);

    when(idpAgentClient.searchTechDocs(any(IdpAgentSearchTechDocsRequest.class))).thenReturn(call);
    when(call.execute()).thenReturn(retrofit2.Response.success(new RestResponse<>(agentResponse)));
    when(catalogServiceHelper.checkEntityRefsPermission(eq(ACCOUNT_IDENTIFIER), any(), eq("view")))
        .thenReturn(allowedEntityRefs);

    Response response = idpAgentProxyApiImpl.semanticSearchTechDocs(request, ACCOUNT_IDENTIFIER);

    assertEquals(200, response.getStatus());
    SearchTechDocsResponse responseEntity = (SearchTechDocsResponse) response.getEntity();
    assertEquals(2, responseEntity.size());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
