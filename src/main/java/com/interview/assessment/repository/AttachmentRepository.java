package com.interview.assessment.repository;

import com.interview.assessment.entity.Attachment;
import com.interview.assessment.entity.AttachmentOwnerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByOwnerTypeAndOwnerId(AttachmentOwnerType ownerType, Long ownerId);
}
