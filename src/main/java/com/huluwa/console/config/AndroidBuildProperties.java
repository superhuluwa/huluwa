package com.huluwa.console.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

/** The Android worker and web service share only this durable task mailbox. */
@ConfigurationProperties(prefix = "huluwa.android-build")
public record AndroidBuildProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("/app/android-build") Path directory,
        @DefaultValue("1800") int timeoutSeconds,
        @DefaultValue("10") int retention) {
}
