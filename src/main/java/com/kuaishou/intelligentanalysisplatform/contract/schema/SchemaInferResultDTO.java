package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SchemaInferResultDTO {
    private String protocolVersion;
    private String schemaId;
    private String schemaVersion;
    private String kind;
    private Map<String, Object> summary;
    private List<FieldSchemaDTO> fields;
    private MappingHintsDTO mappingHints;
    private Map<String, Object> rawSchema;
    private Map<String, Object> extensions;
}
