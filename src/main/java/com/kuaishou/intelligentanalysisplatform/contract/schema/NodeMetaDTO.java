package com.kuaishou.intelligentanalysisplatform.contract.schema;

import java.util.List;
import java.util.Map;

import com.kuaishou.intelligentanalysisplatform.contract.enums.NodeCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeMetaDTO {
    private String protocolVersion;
    private String metadataVersion;
    private String nodeType;
    private String nodeVersion;
    private String displayName;
    private NodeCategory category;
    private Integer sortOrder;
    private String icon;
    private String description;
    private String helpLink;
    private Boolean singleton;
    private Boolean startNode;
    private Boolean endNode;
    private List<String> tags;
    private NodeConfigSchemaDTO configSchema;
    private List<NodePortMetaDTO> inputPorts;
    private List<NodePortMetaDTO> outputPorts;
    private List<NodeCapabilityDTO> capabilities;
    private Map<String, Object> defaults;
    private Map<String, Object> extensions;
}
