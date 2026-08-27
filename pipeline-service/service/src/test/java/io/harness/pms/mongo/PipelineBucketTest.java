/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.mongo;

import static io.harness.rule.OwnerRule.BHUMIJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoConfig.QueryBudgetConfig;
import io.harness.mongo.MongoConfig.QueryBudgetMode;
import io.harness.rule.Owner;
import io.harness.springdata.BucketRegistry;
import io.harness.springdata.BudgetedQuery;
import io.harness.springdata.HMongoTemplate;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Guards {@link PipelineBucket}'s enum-to-{@code Keys} contract: the annotations reference {@code Keys.*} while
 * {@link BucketRegistry} keys on {@link PipelineBucket#value()}, so drift between them would silently mis-bucket.
 */
public class PipelineBucketTest extends CategoryTest {
  private MongoServer mongoServer;
  private MongoClient mongoClient;
  private SimpleMongoClientDatabaseFactory mongoDbFactory;
  private MappingMongoConverter mongoConverter;

  @Before
  public void setUp() {
    mongoServer = new MongoServer(new MemoryBackend());
    mongoServer.bind("localhost", 0);
    InetSocketAddress serverAddress = mongoServer.getLocalAddress();

    mongoClient = MongoClients.create(
        MongoClientSettings.builder()
            .applyToClusterSettings(builder -> builder.hosts(Arrays.asList(new ServerAddress(serverAddress))))
            .build());

    mongoDbFactory = new SimpleMongoClientDatabaseFactory(mongoClient, "testdb");
    MongoMappingContext mappingContext = new MongoMappingContext();
    mongoConverter = new MappingMongoConverter(new DefaultDbRefResolver(mongoDbFactory), mappingContext);
    mongoConverter.afterPropertiesSet();
  }

  @After
  public void tearDown() {
    mongoClient.close();
    mongoServer.shutdownNow();
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void enforceMode_realPipelineBucketRegistry_resolvesSlowBudgetInsteadOfBackstop() {
    // Regression guard: pipeline-service's own PipelinePersistenceModule wires its secondary/analytics template
    // with the 6-arg HMongoTemplate ctor + shared BucketRegistry(PipelineBucket.class) so a BudgetedQuery.withBudget
    // SLOW query resolves to the configured budget, not the 300000ms backstop, under ENFORCE.
    Map<String, Integer> budgets = new HashMap<>();
    budgets.put(PipelineBucket.FAST.value(), 10000);
    budgets.put(PipelineBucket.SLOW.value(), 60000);
    MongoConfig mongoConfig =
        MongoConfig.builder()
            .queryBudget(QueryBudgetConfig.builder().mode(QueryBudgetMode.ENFORCE).budgets(budgets).build())
            .build();
    HMongoTemplate template = new HMongoTemplate(
        mongoDbFactory, mongoConverter, mongoConfig, new BucketRegistry(PipelineBucket.class), null, null);
    template.insert(new Document("_id", "1"), "testCollection");

    Query query = BudgetedQuery.withBudget(new Query(Criteria.where("_id").is("1")), PipelineBucket.SLOW);
    template.find(query, Document.class, "testCollection");

    assertThat(query.getMeta().getMaxTimeMsec()).isEqualTo(60000L);
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void value_matchesKeysConstants() {
    assertThat(PipelineBucket.FAST.value()).isEqualTo(PipelineBucket.Keys.FAST);
    assertThat(PipelineBucket.SLOW.value()).isEqualTo(PipelineBucket.Keys.SLOW);
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void keys_areStableWireValues() {
    // Config-map keys and metric labels: changing them is a breaking change, not a rename.
    assertThat(PipelineBucket.Keys.FAST).isEqualTo("FAST");
    assertThat(PipelineBucket.Keys.SLOW).isEqualTo("SLOW");
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void bucketKeys_areUnique() {
    assertThat(PipelineBucket.FAST.value()).isNotEqualTo(PipelineBucket.SLOW.value());
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void registryBuiltFromPipelineBucket_resolvesBothKeysToTheirConfiguredBudgets() {
    // Sanity: the enum seeds a BucketRegistry and both keys resolve via their value() strings.
    BucketRegistry registry = new BucketRegistry(PipelineBucket.class);
    Map<String, Integer> budgets = new HashMap<>();
    budgets.put(PipelineBucket.FAST.value(), 10000);
    budgets.put(PipelineBucket.SLOW.value(), 60000);

    assertThat(registry.resolve(PipelineBucket.FAST.value(), budgets, 300000)).isEqualTo(Duration.ofMillis(10000));
    assertThat(registry.resolve(PipelineBucket.SLOW.value(), budgets, 300000)).isEqualTo(Duration.ofMillis(60000));
  }
}
