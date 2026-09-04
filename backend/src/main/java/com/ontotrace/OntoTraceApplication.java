package com.ontotrace;

import com.ontotrace.config.OntoTraceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 溯知应用入口。
 *
 * @author hanbd
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(OntoTraceProperties.class)
public class OntoTraceApplication {

    /**
     * 启动应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(OntoTraceApplication.class, args);
    }
}
