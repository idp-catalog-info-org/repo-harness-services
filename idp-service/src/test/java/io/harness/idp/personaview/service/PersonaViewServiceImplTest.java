/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.personaview.service;

import static io.harness.idp.personaview.PersonaViewConstants.DEVELOPER_VIEW_IDENTIFIER;
import static io.harness.idp.personaview.PersonaViewConstants.DEVELOPER_VIEW_NAME;
import static io.harness.rule.OwnerRule.HARJAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.common.CloudStorageUtil;
import io.harness.idp.homepage.config.HomePageCardIconConfig;
import io.harness.idp.homepage.entities.HomePageLayoutEntity;
import io.harness.idp.homepage.repositories.HomePageLayoutRepository;
import io.harness.idp.homepage.service.CardReferenceResolution;
import io.harness.idp.homepage.service.CardService;
import io.harness.idp.homepage.service.HomePageLayoutService;
import io.harness.idp.personaview.entities.PersonaViewEntity;
import io.harness.idp.personaview.events.PersonaViewCreateEvent;
import io.harness.idp.personaview.events.PersonaViewDeleteEvent;
import io.harness.idp.personaview.events.PersonaViewUpdateEvent;
import io.harness.idp.personaview.mappers.PersonaViewMapper;
import io.harness.idp.personaview.repositories.PersonaViewRepository;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.Card;
import io.harness.spec.server.idp.v1.model.HomePageLayoutResponse;
import io.harness.spec.server.idp.v1.model.PersonaView;
import io.harness.spec.server.idp.v1.model.PersonaViewResponse;
import io.harness.spec.server.idp.v1.model.SavePersonaViewBody;
import io.harness.spec.server.idp.v1.model.SavePersonaViewRequest;

import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.IDP)
public class PersonaViewServiceImplTest extends CategoryTest {
  private static final String ACCOUNT = "test-account";
  private static final String CUSTOM_VIEW_ID = "my-custom-view";

  @Mock private PersonaViewRepository personaViewRepository;
  @Mock private PersonaViewMapper personaViewMapper;
  @Mock private CardService cardService;
  @Mock private CatalogEntityRepository catalogEntityRepository;
  @Mock private CloudStorageUtil cloudStorageUtil;
  @Mock @Named("homePageCardIconConfig") private HomePageCardIconConfig homePageCardIconConfig;
  @Mock private HomePageLayoutRepository homePageLayoutRepository;
  @Mock private HomePageLayoutService homePageLayoutService;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private OutboxService outboxService;

  @InjectMocks private PersonaViewServiceImpl personaViewService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(homePageCardIconConfig.getStorageType()).thenReturn("GCS");
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(cardService.getCardsByIdentifiers(anyList(), anyList())).thenReturn(List.of());
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testSaveCustomViewCreatePublishesCreateEvent() {
    SavePersonaViewRequest request =
        new SavePersonaViewRequest().personaView(new SavePersonaViewBody().name("Custom View").cards(List.of()));

    when(personaViewRepository.findByAccountIdentifierAndIdentifier(ACCOUNT, CUSTOM_VIEW_ID))
        .thenReturn(Optional.empty());
    when(cardService.resolveCardReferences(anyList()))
        .thenReturn(new CardReferenceResolution(List.of("card-1"), List.of()));

    PersonaViewEntity savedEntity = PersonaViewEntity.builder()
                                        .accountIdentifier(ACCOUNT)
                                        .identifier(CUSTOM_VIEW_ID)
                                        .name("Custom View")
                                        .cards(List.of("card-1"))
                                        .build();
    when(personaViewMapper.fromSaveBody(any(), eq(ACCOUNT), eq(CUSTOM_VIEW_ID), eq(false))).thenReturn(savedEntity);
    when(personaViewRepository.save(any())).thenReturn(savedEntity);

    PersonaView personaView = new PersonaView().identifier(CUSTOM_VIEW_ID).name("Custom View");
    when(personaViewMapper.toDto(savedEntity)).thenReturn(personaView);
    when(personaViewMapper.toResponse(any(PersonaView.class)))
        .thenReturn(new PersonaViewResponse().personaView(personaView));

    personaViewService.savePersonaView(ACCOUNT, CUSTOM_VIEW_ID, request);

    verify(outboxService).save(any(PersonaViewCreateEvent.class));
    verify(outboxService, never()).save(any(PersonaViewUpdateEvent.class));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testSaveCustomViewUpdatePublishesUpdateEvent() {
    SavePersonaViewRequest request =
        new SavePersonaViewRequest().personaView(new SavePersonaViewBody().name("Custom View").cards(List.of()));

    PersonaViewEntity existing = PersonaViewEntity.builder()
                                     .accountIdentifier(ACCOUNT)
                                     .identifier(CUSTOM_VIEW_ID)
                                     .name("Custom View")
                                     .cards(List.of("card-old"))
                                     .build();
    when(personaViewRepository.findByAccountIdentifierAndIdentifier(ACCOUNT, CUSTOM_VIEW_ID))
        .thenReturn(Optional.of(existing));
    when(cardService.resolveCardReferences(anyList()))
        .thenReturn(new CardReferenceResolution(List.of("card-1"), List.of()));

    PersonaViewEntity savedEntity = PersonaViewEntity.builder()
                                        .accountIdentifier(ACCOUNT)
                                        .identifier(CUSTOM_VIEW_ID)
                                        .name("Custom View")
                                        .cards(List.of("card-1"))
                                        .build();
    when(personaViewMapper.fromSaveBody(any(), eq(ACCOUNT), eq(CUSTOM_VIEW_ID), eq(false))).thenReturn(savedEntity);
    when(personaViewRepository.save(any())).thenReturn(savedEntity);

    PersonaView oldPersonaView = new PersonaView().identifier(CUSTOM_VIEW_ID).name("Custom View");
    PersonaView newPersonaView = new PersonaView().identifier(CUSTOM_VIEW_ID).name("Custom View");
    when(personaViewMapper.toDto(existing)).thenReturn(oldPersonaView);
    when(personaViewMapper.toDto(savedEntity)).thenReturn(newPersonaView);
    when(personaViewMapper.toResponse(any(PersonaView.class)))
        .thenReturn(new PersonaViewResponse().personaView(newPersonaView));

    personaViewService.savePersonaView(ACCOUNT, CUSTOM_VIEW_ID, request);

    ArgumentCaptor<PersonaViewUpdateEvent> eventCaptor = ArgumentCaptor.forClass(PersonaViewUpdateEvent.class);
    verify(outboxService).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getOldPersonaView()).isEqualTo(oldPersonaView);
    assertThat(eventCaptor.getValue().getNewPersonaView()).isEqualTo(newPersonaView);
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testDeletePersonaViewPublishesDeleteEvent() {
    PersonaViewEntity entity = PersonaViewEntity.builder()
                                   .accountIdentifier(ACCOUNT)
                                   .identifier(CUSTOM_VIEW_ID)
                                   .name("Custom View")
                                   .cards(List.of("card-1"))
                                   .ootb(false)
                                   .build();
    when(personaViewRepository.findByAccountIdentifierAndIdentifier(ACCOUNT, CUSTOM_VIEW_ID))
        .thenReturn(Optional.of(entity));

    PersonaView oldPersonaView = new PersonaView().identifier(CUSTOM_VIEW_ID).name("Custom View");
    when(personaViewMapper.toDto(entity)).thenReturn(oldPersonaView);

    personaViewService.deletePersonaView(ACCOUNT, CUSTOM_VIEW_ID);

    ArgumentCaptor<PersonaViewDeleteEvent> eventCaptor = ArgumentCaptor.forClass(PersonaViewDeleteEvent.class);
    verify(outboxService).save(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getOldPersonaView()).isEqualTo(oldPersonaView);
    verify(personaViewRepository).deleteByAccountIdentifierAndIdentifier(ACCOUNT, CUSTOM_VIEW_ID);
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testSaveDeveloperViewUpdatePublishesPersonaViewUpdateEvent() {
    SavePersonaViewRequest request =
        new SavePersonaViewRequest().personaView(new SavePersonaViewBody().cards(List.of(new Card())));

    HomePageLayoutEntity existingLayout =
        HomePageLayoutEntity.builder().accountIdentifier(ACCOUNT).cards(List.of("card-old")).build();
    HomePageLayoutEntity savedLayout =
        HomePageLayoutEntity.builder().accountIdentifier(ACCOUNT).cards(List.of("card-new")).build();

    when(homePageLayoutRepository.findByAccountIdentifier(ACCOUNT))
        .thenReturn(Optional.of(existingLayout))
        .thenReturn(Optional.of(savedLayout));
    when(homePageLayoutService.saveHomePageLayout(any(), eq(ACCOUNT))).thenReturn(new HomePageLayoutResponse());

    when(personaViewMapper.toResponse(any(PersonaView.class)))
        .thenAnswer(invocation -> new PersonaViewResponse().personaView(invocation.getArgument(0)));

    PersonaViewResponse response = personaViewService.savePersonaView(ACCOUNT, DEVELOPER_VIEW_IDENTIFIER, request);

    verify(homePageLayoutService).saveHomePageLayout(any(), eq(ACCOUNT));
    verify(outboxService).save(any(PersonaViewUpdateEvent.class));
    verify(outboxService, never()).save(any(PersonaViewCreateEvent.class));
    assertThat(response.getPersonaView().getIdentifier()).isEqualTo(DEVELOPER_VIEW_IDENTIFIER);
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testSaveDeveloperViewCreatePublishesPersonaViewCreateEvent() {
    SavePersonaViewRequest request =
        new SavePersonaViewRequest().personaView(new SavePersonaViewBody().cards(List.of(new Card())));

    HomePageLayoutEntity savedLayout =
        HomePageLayoutEntity.builder().accountIdentifier(ACCOUNT).cards(List.of("card-new")).build();

    when(homePageLayoutRepository.findByAccountIdentifier(ACCOUNT))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(savedLayout));
    when(homePageLayoutService.saveHomePageLayout(any(), eq(ACCOUNT))).thenReturn(new HomePageLayoutResponse());

    PersonaView newPersonaView =
        new PersonaView().identifier(DEVELOPER_VIEW_IDENTIFIER).name(DEVELOPER_VIEW_NAME).cards(List.of());
    when(personaViewMapper.toResponse(any(PersonaView.class)))
        .thenReturn(new PersonaViewResponse().personaView(newPersonaView));

    personaViewService.savePersonaView(ACCOUNT, DEVELOPER_VIEW_IDENTIFIER, request);

    verify(homePageLayoutService).saveHomePageLayout(any(), eq(ACCOUNT));
    verify(outboxService).save(any(PersonaViewCreateEvent.class));
    verify(outboxService, never()).save(any(PersonaViewUpdateEvent.class));
  }
}
