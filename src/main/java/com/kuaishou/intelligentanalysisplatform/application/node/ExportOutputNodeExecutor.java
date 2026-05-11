package com.kuaishou.intelligentanalysisplatform.application.node;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kuaishou.intelligentanalysisplatform.application.compute.ComputeDatasetResolver;
import com.kuaishou.intelligentanalysisplatform.application.export.DataExporter;
import com.kuaishou.intelligentanalysisplatform.application.export.ExportFileStore;
import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExecutionStatus;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ExportFormat;
import com.kuaishou.intelligentanalysisplatform.contract.enums.NodeType;
import com.kuaishou.intelligentanalysisplatform.contract.enums.ResultKind;
import com.kuaishou.intelligentanalysisplatform.contract.schema.DatasetDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.ExportOutputNodeConfigDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeMetaDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.NodeResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.schema.StandardResultDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecuteContextDTO;
import com.kuaishou.intelligentanalysisplatform.contract.spi.NodeExecutor;
import com.kuaishou.intelligentanalysisplatform.contract.spi.ValidationResultDTO;
import com.kuaishou.intelligentanalysisplatform.domain.export.ExportFile;
import com.kuaishou.intelligentanalysisplatform.domain.export.ExportFileRepository;
import org.springframework.stereotype.Component;

@Component
public class ExportOutputNodeExecutor implements NodeExecutor<ExportOutputNodeConfigDTO> {

    private final ComputeDatasetResolver computeDatasetResolver;
    private final Map<ExportFormat, DataExporter> exporterMap;
    private final ExportFileStore fileStore;
    private final ExportFileRepository exportFileRepository;

    public ExportOutputNodeExecutor(ComputeDatasetResolver computeDatasetResolver,
                                    List<DataExporter> exporters,
                                    ExportFileStore fileStore,
                                    ExportFileRepository exportFileRepository) {
        this.computeDatasetResolver = computeDatasetResolver;
        this.fileStore = fileStore;
        this.exportFileRepository = exportFileRepository;
        Map<ExportFormat, DataExporter> map = new HashMap<>();
        for (DataExporter exporter : exporters) {
            map.put(exporter.supportFormat(), exporter);
        }
        this.exporterMap = map;
    }

    @Override
    public String supportType() {
        return NodeType.EXPORT_OUTPUT.getCode();
    }

    @Override
    public NodeResultDTO execute(NodeExecuteContextDTO context, ExportOutputNodeConfigDTO config) {
        DatasetDTO dataset = computeDatasetResolver.resolve(config.getDatasetRef(), context.getUpstreamResults());
        ExportFormat format = config.getFormat() != null ? config.getFormat() : ExportFormat.CSV;
        DataExporter exporter = exporterMap.get(format);
        if (exporter == null) {
            throw new BaseBusinessException(ErrorCode.NOT_IMPLEMENTED,
                    "export format not supported: " + format);
        }

        List<String> columns = resolveColumns(dataset, config.getColumns());
        String fileId = UUID.randomUUID().toString();
        String fileName = resolveFileName(config.getFileName(), format);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            exporter.export(dataset.getRows(), columns, config, buffer);
        } catch (IOException e) {
            throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR, "export failed: " + e.getMessage());
        }
        byte[] data = buffer.toByteArray();

        long now = System.currentTimeMillis();
        String storagePath = fileStore.store(fileId, fileName, data);
        int rowCount = dataset.getRows() != null ? dataset.getRows().size() : 0;

        ExportFile exportFile = ExportFile.builder()
                .fileId(fileId)
                .tenantId(context.getRequestContext() != null ? context.getRequestContext().getTenantId() : "default")
                .fileName(fileName)
                .format(format)
                .storagePath(storagePath)
                .fileSizeBytes((long) data.length)
                .rowCount(rowCount)
                .createdAt(now)
                .expiresAt(now + 24L * 3600 * 1000)
                .build();
        exportFileRepository.save(exportFile);

        return NodeResultDTO.builder()
                .nodeId(context.getNodeId())
                .nodeType(supportType())
                .status(ExecutionStatus.SUCCEEDED)
                .result(StandardResultDTO.builder()
                        .kind(ResultKind.VARIABLES)
                        .variables(Map.of(
                                "fileId", fileId,
                                "fileName", fileName,
                                "downloadUrl", "/api/v1/exports/" + fileId + "/download",
                                "rowCount", rowCount,
                                "fileSizeBytes", (long) data.length))
                        .build())
                .build();
    }

    @Override
    public ValidationResultDTO validate(ExportOutputNodeConfigDTO config) {
        if (config == null || config.getDatasetRef() == null) {
            return ValidationResultDTO.builder().valid(false).errorMessage("datasetRef is required").build();
        }
        if (config.getFormat() == ExportFormat.PARQUET) {
            return ValidationResultDTO.builder().valid(false)
                    .errorMessage("PARQUET format is not supported yet").build();
        }
        return ValidationResultDTO.builder().valid(true).build();
    }

    @Override
    public NodeMetaDTO metadata() {
        return null;
    }

    private List<String> resolveColumns(DatasetDTO dataset, List<String> configColumns) {
        if (configColumns != null && !configColumns.isEmpty()) {
            return configColumns;
        }
        if (dataset.getSchema() != null && dataset.getSchema().getFields() != null
                && !dataset.getSchema().getFields().isEmpty()) {
            return dataset.getSchema().getFields().stream()
                    .map(f -> f.getName())
                    .toList();
        }
        if (dataset.getRows() != null && !dataset.getRows().isEmpty()) {
            return new ArrayList<>(dataset.getRows().get(0).keySet());
        }
        return List.of();
    }

    private String resolveFileName(String configFileName, ExportFormat format) {
        String base = (configFileName != null && !configFileName.isBlank())
                ? sanitizeFileName(configFileName)
                : "export-" + System.currentTimeMillis();
        return base + "." + format.getExtension();
    }

    /** 去除路径分隔符，防止路径遍历攻击 */
    private String sanitizeFileName(String name) {
        return name.replaceAll("[/\\\\]", "_");
    }
}
