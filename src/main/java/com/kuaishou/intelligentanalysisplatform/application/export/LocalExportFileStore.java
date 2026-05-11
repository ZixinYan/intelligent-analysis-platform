package com.kuaishou.intelligentanalysisplatform.application.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.kuaishou.intelligentanalysisplatform.common.error.BaseBusinessException;
import com.kuaishou.intelligentanalysisplatform.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 本地磁盘文件存储实现。
 * 存储路径：{localPath}/{fileId}/{fileName}
 * 通过 export.storage.local-path 配置根目录。
 */
@Component
public class LocalExportFileStore implements ExportFileStore {

    private final String localPath;

    public LocalExportFileStore(
            @Value("${export.storage.local-path:/tmp/exports}") String localPath) {
        this.localPath = localPath;
    }

    @Override
    public String store(String fileId, String fileName, byte[] data) {
        Path dir = Paths.get(localPath, fileId);
        Path filePath = dir.resolve(fileName);
        try {
            Files.createDirectories(dir);
            Files.write(filePath, data);
        } catch (IOException e) {
            throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR,
                    "failed to store export file: " + e.getMessage());
        }
        return filePath.toString();
    }

    @Override
    public byte[] load(String storagePath) {
        Path filePath = Paths.get(storagePath);
        if (!Files.exists(filePath)) {
            throw new BaseBusinessException(ErrorCode.EXPORT_NOT_FOUND,
                    "export file not found on disk: " + storagePath);
        }
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new BaseBusinessException(ErrorCode.INTERNAL_ERROR,
                    "failed to load export file: " + e.getMessage());
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            Files.deleteIfExists(Paths.get(storagePath));
        } catch (IOException e) {
            // 删除失败不中断业务，记录日志即可
        }
    }
}
