/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.app;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.factory.ClosingFactory;
import io.harness.idp.envvariable.beans.entity.BackstageEnvConfigVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvSecretVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableType;
import io.harness.idp.homepage.entities.CardEntity;
import io.harness.idp.homepage.entities.CustomLinkCardEntity;
import io.harness.idp.homepage.entities.LearnMoreCardEntity;
import io.harness.idp.homepage.entities.MarkdownCardEntity;
import io.harness.idp.homepage.entities.RecentlyVisitedCardEntity;
import io.harness.idp.homepage.entities.SelfServiceCardEntity;
import io.harness.idp.homepage.entities.StarredEntitiesCardEntity;
import io.harness.idp.homepage.entities.TopVisitedCardEntity;
import io.harness.idp.homepage.entities.VideoCardEntity;
import io.harness.rule.InjectorRuleMixinNew;
import io.harness.spec.server.idp.v1.model.Card;
import io.harness.testlib.module.MongoRuleMixin;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.multibindings.MapBinder;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class IdpServiceRule implements MethodRule, InjectorRuleMixinNew, MongoRuleMixin {
  ClosingFactory closingFactory;
  static final Injector[] injector = {null};

  public IdpServiceRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) throws Exception {
    List<Module> modules = new ArrayList<>();
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        MapBinder<BackstageEnvVariableType, BackstageEnvVariableEntity.BackstageEnvVariableMapper>
            backstageEnvVariableMapBinder = MapBinder.newMapBinder(
                binder(), BackstageEnvVariableType.class, BackstageEnvVariableEntity.BackstageEnvVariableMapper.class);
        backstageEnvVariableMapBinder.addBinding(BackstageEnvVariableType.CONFIG)
            .to(BackstageEnvConfigVariableEntity.BackstageEnvConfigVariableMapper.class);
        backstageEnvVariableMapBinder.addBinding(BackstageEnvVariableType.SECRET)
            .to(BackstageEnvSecretVariableEntity.BackstageEnvSecretVariableMapper.class);
      }
    });

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        // MapBinder for Cards Type for HomePage Layout
        MapBinder<Card.TypeEnum, CardEntity.CardMapper> cardTypeCardMapperMapBinder =
            MapBinder.newMapBinder(binder(), Card.TypeEnum.class, CardEntity.CardMapper.class);
        cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.LEARN_MORE)
            .to(LearnMoreCardEntity.LearnMoreCardMapper.class);
        cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.TOP_VISITED)
            .to(TopVisitedCardEntity.TopVisitedCardMapper.class);
        cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.RECENTLY_VISITED)
            .to(RecentlyVisitedCardEntity.RecentlyVisitedCardMapper.class);
        cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.STARRED_ENTITIES)
            .to(StarredEntitiesCardEntity.StarredEntitiesCardMapper.class);
        cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.MARKDOWN).to(MarkdownCardEntity.MarkdownCardMapper.class);
        cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.VIDEO).to(VideoCardEntity.VideoCardMapper.class);
        cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.CUSTOM_LINK)
            .to(CustomLinkCardEntity.CustomLinkCardMapper.class);
        cardTypeCardMapperMapBinder.addBinding(Card.TypeEnum.SELF_SERVICE)
            .to(SelfServiceCardEntity.SelfServiceCardMapper.class);
      }
    });

    return modules;
  }

  @Override
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object target) {
    return applyInjector(log, statement, frameworkMethod, target, injector);
  }
}
