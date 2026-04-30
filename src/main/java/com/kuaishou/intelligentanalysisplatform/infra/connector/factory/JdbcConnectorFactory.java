package com.kuaishou.intelligentanalysisplatform.infra.connector.factory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.DatasourceType;
import com.kuaishou.intelligentanalysisplatform.domain.datasource.AnalysisDatasource;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.Connector;
import com.kuaishou.intelligentanalysisplatform.domain.query.connector.ConnectorFactory;
import org.springframework.stereotype.Component;

@Component
public class JdbcConnectorFactory implements ConnectorFactory {
    private final Map<DatasourceType, Connector> connectors;

    public JdbcConnectorFactory(List<Connector> connectors) {
        this.connectors = new EnumMap<>(DatasourceType.class);
        for (Connector connector : connectors) {
            this.connectors.put(connector.type(), connector);
        }
    }

    @Override
    public Connector create(AnalysisDatasource datasource) {
        Connector connector = connectors.get(datasource.getType());
        if (connector == null) {
            throw new BaseBusinessException(ErrorCode.NOT_IMPLEMENTED, "unsupported datasource type");
        }
        return connector;
    }
}
