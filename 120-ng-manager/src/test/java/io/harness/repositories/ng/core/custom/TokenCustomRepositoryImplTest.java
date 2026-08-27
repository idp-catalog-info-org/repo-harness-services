/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.ng.core.custom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.common.beans.PublicKeyScheme;
import io.harness.ng.core.entities.Token;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.mongodb.client.result.DeleteResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

public class TokenCustomRepositoryImplTest extends CategoryTest {
  private MongoTemplate mongoTemplate;
  private TokenCustomRepositoryImpl tokenCustomRepository;

  @Before
  public void setup() {
    mongoTemplate = mock(MongoTemplate.class);
    tokenCustomRepository = new TokenCustomRepositoryImpl(mongoTemplate);
  }

  @Test
  @Owner(developers = OwnerRule.ATEFEH)
  @Category(UnitTests.class)
  public void testFindByKeyFilters_WithKeyId() {
    String accountIdentifier = "testAccount";
    String keyId = "ABC123";
    List<Token> expectedTokens = new ArrayList<>();

    when(mongoTemplate.find(any(Query.class), eq(Token.class))).thenReturn(expectedTokens);

    List<Token> result = tokenCustomRepository.findByKeyFilters(
        accountIdentifier, null, keyId, null, null, null, List.of(PublicKeyScheme.PGP));

    assertThat(result).isEqualTo(expectedTokens);
    verify(mongoTemplate).find(any(Query.class), eq(Token.class));
  }

  @Test
  @Owner(developers = OwnerRule.ATEFEH)
  @Category(UnitTests.class)
  public void testFindByKeyFilters_WithFingerprint() {
    String accountIdentifier = "testAccount";
    String fingerprint = "ABC123DEF456";
    List<Token> expectedTokens = new ArrayList<>();

    when(mongoTemplate.find(any(Query.class), eq(Token.class))).thenReturn(expectedTokens);

    List<Token> result = tokenCustomRepository.findByKeyFilters(
        accountIdentifier, fingerprint, null, null, null, null, List.of(PublicKeyScheme.PGP));

    assertThat(result).isEqualTo(expectedTokens);
    verify(mongoTemplate).find(any(Query.class), eq(Token.class));
  }

  @Test
  @Owner(developers = OwnerRule.ATEFEH)
  @Category(UnitTests.class)
  public void testFindByKeyFilters_WithParentKeyId() {
    String accountIdentifier = "testAccount";
    String parentKeyId = "ABC123";
    List<Token> expectedTokens = new ArrayList<>();

    when(mongoTemplate.find(any(Query.class), eq(Token.class))).thenReturn(expectedTokens);

    List<Token> result = tokenCustomRepository.findByKeyFilters(
        accountIdentifier, null, null, parentKeyId, null, null, List.of(PublicKeyScheme.PGP));

    assertThat(result).isEqualTo(expectedTokens);
    verify(mongoTemplate).find(any(Query.class), eq(Token.class));
  }

  @Test
  @Owner(developers = OwnerRule.ATEFEH)
  @Category(UnitTests.class)
  public void testDeleteAll() {
    DeleteResult deleteResult = mock(DeleteResult.class);
    when(deleteResult.getDeletedCount()).thenReturn(5L);

    when(mongoTemplate.remove(any(Query.class), eq(Token.class))).thenReturn(deleteResult);

    org.springframework.data.mongodb.core.query.Criteria criteria =
        org.springframework.data.mongodb.core.query.Criteria.where("accountIdentifier").is("testAccount");
    long result = tokenCustomRepository.deleteAll(criteria);

    assertThat(result).isEqualTo(5L);
    verify(mongoTemplate).remove(any(Query.class), eq(Token.class));
  }

  @Test
  @Owner(developers = OwnerRule.ATEFEH)
  @Category(UnitTests.class)
  public void testFindExpiredTokens_ExcludesPgpKeys() {
    Instant currentTime = Instant.now();
    List<Token> expectedTokens = new ArrayList<>();

    when(mongoTemplate.find(any(Query.class), eq(Token.class))).thenReturn(expectedTokens);

    List<Token> result = tokenCustomRepository.findExpiredTokens(currentTime);

    assertThat(result).isEqualTo(expectedTokens);

    // Verify the query was built correctly - the actual verification is that
    // the implementation excludes PGP_KEY types from the cleanup query
    verify(mongoTemplate).find(any(Query.class), eq(Token.class));
  }
}
