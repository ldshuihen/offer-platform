package com.xi.oss.entity;

/**
 * 文件类
 *
 * 💡 注意：MinIO 本身没有“目录”概念（所有对象都是扁平存储），但可通过前缀（如 docs/）模拟目录结构，此时 directoryFlag 由客户端逻辑推断。
 *
 * @author: ChickenWing
 * @date: 2023/10/12
 */
public class FileInfo {

    // 文件（或目录）的名称，例如 "report.pdf" 或 "images/"
    private String fileName;

    // 标识该项是否为目录：
    private Boolean directoryFlag;

    /**
     * 文件的 ETag（Entity Tag），通常用于：
     * - 校验文件一致性
     * - 判断文件是否被修改（类似 MD5 或版本标识）
     * - 在 HTTP 缓存或断点续传中使用
     */
    private String etag;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Boolean getDirectoryFlag() {
        return directoryFlag;
    }

    public void setDirectoryFlag(Boolean directoryFlag) {
        this.directoryFlag = directoryFlag;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }
}
