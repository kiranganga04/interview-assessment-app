package com.interview.assessment.controller;

import com.interview.assessment.dto.AttachmentDTO;
import com.interview.assessment.entity.Attachment;
import com.interview.assessment.entity.AttachmentOwnerType;
import com.interview.assessment.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Module 5: upload/download for candidate resumes and interview screenshots/exports. */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping(value = "/{ownerType}/{ownerId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentDTO upload(@PathVariable AttachmentOwnerType ownerType,
                                 @PathVariable Long ownerId,
                                 @RequestParam("file") MultipartFile file) {
        return fileStorageService.store(ownerType, ownerId, file);
    }

    @GetMapping("/{ownerType}/{ownerId}")
    public List<AttachmentDTO> listFor(@PathVariable AttachmentOwnerType ownerType, @PathVariable Long ownerId) {
        return fileStorageService.listFor(ownerType, ownerId);
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable Long attachmentId) {
        Attachment attachment = fileStorageService.findMeta(attachmentId);
        Resource resource = fileStorageService.loadAsResource(attachment);
        MediaType mediaType = attachment.getContentType() != null
                ? MediaType.parseMediaType(attachment.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Content-Disposition", "attachment; filename=\"" + attachment.getOriginalFilename() + "\"")
                .body(resource);
    }
}
