package com.interview.assessment.service;

import com.interview.assessment.config.FileStorageProperties;
import com.interview.assessment.dto.AttachmentDTO;
import com.interview.assessment.entity.Attachment;
import com.interview.assessment.entity.AttachmentOwnerType;
import com.interview.assessment.exception.BadRequestException;
import com.interview.assessment.exception.ResourceNotFoundException;
import com.interview.assessment.repository.AttachmentRepository;
import com.interview.assessment.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * Module 5: local-disk file storage for resumes / interview screenshots. Deliberately
 * behind a narrow interface-shaped service so swapping in S3 later only touches this class.
 *
 * Access control (added after a baseline OpenSpec review found none of this existed at all):
 * a CANDIDATE_RESUME is readable by any authenticated role, same as the candidate directory
 * itself, but only ADMIN/RECRUITER may upload one -- matching who can create/edit a candidate
 * record. An INTERVIEW_SCREENSHOT follows the exact same rule as the interview it belongs to
 * (see InterviewService.assertPanelCanAccessInterview): ADMIN/RECRUITER always, PANEL only for
 * an interview they're the assigned interviewer on -- for upload, listing, and download alike.
 */
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "application/pdf", "image/png", "image/jpeg", "image/jpg",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final FileStorageProperties properties;
    private final AttachmentRepository attachmentRepository;
    private final InterviewService interviewService;

    @Transactional
    public AttachmentDTO store(AttachmentOwnerType ownerType, Long ownerId, MultipartFile file) {
        enforceAccess(ownerType, ownerId, true);
        if (file.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty.");
        }
        if (file.getSize() > properties.getMaxSizeBytes()) {
            throw new BadRequestException("File exceeds the maximum allowed size.");
        }
        if (file.getContentType() != null && !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BadRequestException("Unsupported file type: " + file.getContentType());
        }

        try {
            Path root = Path.of(properties.getDirectory());
            Files.createDirectories(root);

            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
            String extension = originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf('.'))
                    : "";
            String storedFilename = UUID.randomUUID() + extension;
            Path target = root.resolve(storedFilename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            Attachment attachment = new Attachment();
            attachment.setOwnerType(ownerType);
            attachment.setOwnerId(ownerId);
            attachment.setOriginalFilename(originalFilename);
            attachment.setStoredFilename(storedFilename);
            attachment.setContentType(file.getContentType());
            attachment.setSizeBytes(file.getSize());
            attachment.setUploadedBy(CurrentUser.emailOrSystem());
            attachment = attachmentRepository.save(attachment);

            return toDto(attachment);
        } catch (IOException e) {
            throw new BadRequestException("Failed to store file: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<AttachmentDTO> listFor(AttachmentOwnerType ownerType, Long ownerId) {
        enforceAccess(ownerType, ownerId, false);
        return attachmentRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Attachment findMeta(Long attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + attachmentId));
        enforceAccess(attachment.getOwnerType(), attachment.getOwnerId(), false);
        return attachment;
    }

    /**
     * @param uploading true for the upload path (stricter -- CANDIDATE_RESUME needs
     *                  ADMIN/RECRUITER), false for list/download (CANDIDATE_RESUME open to any
     *                  authenticated role). INTERVIEW_SCREENSHOT is checked the same way either
     *                  way, since it always follows the underlying interview's ownership rule.
     */
    private void enforceAccess(AttachmentOwnerType ownerType, Long ownerId, boolean uploading) {
        if (ownerType == AttachmentOwnerType.INTERVIEW_SCREENSHOT) {
            interviewService.assertPanelCanAccessInterview(ownerId);
        } else if (ownerType == AttachmentOwnerType.CANDIDATE_RESUME && uploading && CurrentUser.hasRole("PANEL")) {
            throw new AccessDeniedException("Only Admin or Recruiter can upload a candidate resume.");
        }
    }

    public Resource loadAsResource(Attachment attachment) {
        try {
            Path file = Path.of(properties.getDirectory()).resolve(attachment.getStoredFilename());
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Stored file is missing on disk: " + attachment.getStoredFilename());
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Invalid file path for attachment " + attachment.getAttachmentId());
        }
    }

    private AttachmentDTO toDto(Attachment attachment) {
        AttachmentDTO dto = new AttachmentDTO();
        dto.setAttachmentId(attachment.getAttachmentId());
        dto.setOwnerType(attachment.getOwnerType().name());
        dto.setOwnerId(attachment.getOwnerId());
        dto.setOriginalFilename(attachment.getOriginalFilename());
        dto.setDownloadUrl("/api/files/" + attachment.getAttachmentId());
        dto.setContentType(attachment.getContentType());
        dto.setSizeBytes(attachment.getSizeBytes());
        dto.setUploadedAt(attachment.getUploadedAt());
        return dto;
    }
}
