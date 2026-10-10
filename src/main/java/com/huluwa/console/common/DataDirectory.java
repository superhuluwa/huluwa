package com.huluwa.console.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 数据目录准备：SQLite 库文件所在目录必须在 DataSource 建立连接前就存在，
 * 因此这里在 Spring 启动之前同步执行。
 */
public final class DataDirectory {

    private static final Logger log = LoggerFactory.getLogger(DataDirectory.class);
    public static final String PROPERTY = "huluwa.data-dir";

    private DataDirectory() {
    }

    public static void prepare(String[] args) {
        String dir = System.getProperty(PROPERTY);
        if (dir == null || dir.isBlank()) {
            dir = System.getenv("HULUWA_DATA_DIR");
        }
        if (dir == null || dir.isBlank()) {
            dir = "./data";
        }
        System.setProperty(PROPERTY, dir);
        try {
            Path path = Paths.get(dir).toAbsolutePath().normalize();
            Files.createDirectories(path);
            log.info("数据目录: {}", path);
        } catch (Exception ex) {
            throw new IllegalStateException("无法创建数据目录: " + dir, ex);
        }
    }
}