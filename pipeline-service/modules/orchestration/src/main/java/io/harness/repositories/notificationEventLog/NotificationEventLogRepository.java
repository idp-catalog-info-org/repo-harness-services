/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.notificationEventLog;

import io.harness.annotation.HarnessRepo;
import io.harness.entity.eventlog.NotificationEventLog;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
public interface NotificationEventLogRepository
    extends PagingAndSortingRepository<NotificationEventLog, String>, CrudRepository<NotificationEventLog, String>,
            NotificationEventLogRepositoryCustom {}
