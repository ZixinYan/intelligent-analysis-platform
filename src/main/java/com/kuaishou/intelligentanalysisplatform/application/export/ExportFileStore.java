package com.kuaishou.intelligentanalysisplatform.application.export;

public interface ExportFileStore {
    /** 将数据写入存储，返回 storagePath */
    String store(String fileId, String fileName, byte[] data);

    /** 根据 storagePath 读取字节 */
    byte[] load(String storagePath);

    /** 删除文件 */
    void delete(String storagePath);
}
