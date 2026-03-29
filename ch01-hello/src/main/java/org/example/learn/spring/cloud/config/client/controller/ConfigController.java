package org.example.learn.spring.cloud.config.client.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @RefreshScope 支持动态刷新配置
 * application-context中存在的ConfigController实例其实是ConfigController代理类实例(代理类后面还有ConfigController类实例)
 * 每当RefreshScope刷新,application-context中的代理类实例是不会改变的,但是代理类实例中的ConfigController类实例会被新建
 */
@RestController
@RefreshScope
public class ConfigController {

    @Value("${app.name:Default App Name}")
    private String appName;

    @Value("${app.description:Default Description}")
    private String description;

    @Value("${app.version:1.0.0}")
    private String version;

    @Value("${app.author:Unknown}")
    private String author;

    @GetMapping("/config")
    public ConfigInfo getConfig() {
        return new ConfigInfo(appName, description, version, author);
    }

    @GetMapping("/hello")
    public String hello() {
        return "Hello from " + appName + "!";
    }

    static class ConfigInfo {
        private String appName;
        private String description;
        private String version;
        private String author;

        public ConfigInfo(String appName, String description, String version, String author) {
            this.appName = appName;
            this.description = description;
            this.version = version;
            this.author = author;
        }

        // Getters and Setters
        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
    }
}
