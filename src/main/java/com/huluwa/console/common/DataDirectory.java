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
    /** 环境变量名（compose / 部署脚本使用） */
    public static final String ENV_NAME = "HULUWA_DATA_DIR";

    private DataDirectory() {
    }

    public static void prepare(String[] args) {
        // 优先级：命令行 --huluwa.data-dir=xxx → -D 系统属性 → 环境变量 → 默认 ./data
        String dir = optionValue(args, "--" + PROPERTY);
        if (dir == null || dir.isBlank()) {
            dir = System.getProperty(PROPERTY);
        }
        if (dir == null || dir.isBlank()) {
            dir = System.getenv(ENV_NAME);
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

    /** 取 --name=value 形式的命令行取值；未命中返回 null */
    private static String optionValue(String[] args, String name) {
        if (args == null) {
            return null;
        }
        for (String arg : args) {
            if (arg != null && arg.startsWith(name + "=")) {
                return arg.substring(name.length() + 1).trim();
            }
        }
        return null;
    }
}