package com.interview.assessment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Module 5: where uploaded resumes/screenshots are stored on disk. */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.file-storage")
public class FileStorageProperties {
    private String directory = "uploads";
    private long maxSizeBytes = 10L * 1024 * 1024; // 10MB
}
