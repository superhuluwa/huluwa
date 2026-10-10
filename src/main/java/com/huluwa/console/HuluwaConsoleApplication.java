package com.huluwa.console;

import com.huluwa.console.common.DataDirectory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class HuluwaConsoleApplication {

    public static void main(String[] args) {
        // SQLite 库文件所在目录必须先存在，否则 DataSource 初始化会失败
        DataDirectory.prepare(args);
        SpringApplication.run(HuluwaConsoleApplication.class, args);
    }
}