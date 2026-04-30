package com.kuaishou.intelligentanalysisplatform.domain.query.connector;

import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;

public interface ConnectorFactory {
    Connector create(AnalysisDatasource datasource);
}
