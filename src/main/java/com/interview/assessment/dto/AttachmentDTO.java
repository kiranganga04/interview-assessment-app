package com.interview.assessment.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AttachmentDTO {
    private Long attachmentId;
    private String ownerType;
    private Long ownerId;
    private String originalFilename;
    private String downloadUrl;
    private String contentType;
    private Long sizeBytes;
    private LocalDateTime uploadedAt;
}
