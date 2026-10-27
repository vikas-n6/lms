package com.lms.reporting.application.port.out;

import com.lms.reporting.domain.CollectionCaseInfo;
import java.util.List;

/** Store for the collection-case projection from collections.events. */
public interface CollectionCaseRepository {

    void upsert(CollectionCaseInfo info);

    List<CollectionCaseInfo> findAll();
}
