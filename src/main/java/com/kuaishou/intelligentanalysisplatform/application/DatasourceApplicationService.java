package com.kuaishou.intelligentanalysisplatform.application;

import com.kuaishou.intelligentanalysisplatform.common.response.PageResult;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceCreateRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceQueryAccessDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceQueryRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceTestConnectionRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceTestConnectionResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasourceUpdateRequestDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.RequestContextDTO;

import java.util.List;

public interface DatasourceApplicationService {
    DatasourceDTO create(DatasourceCreateRequestDTO request);

    DatasourceDTO update(String id, DatasourceUpdateRequestDTO request);

    void delete(String id, RequestContextDTO context);

    DatasourceDTO getById(String id, RequestContextDTO context);

    PageResult<DatasourceDTO> list(DatasourceQueryRequestDTO request);

    DatasourceTestConnectionResultDTO testConnection(DatasourceTestConnectionRequestDTO request);

    DatasourceQueryAccessDTO getQueryAccess(String datasourceId, RequestContextDTO context);

    List<String> listTables(String datasourceId, RequestContextDTO context);
}
