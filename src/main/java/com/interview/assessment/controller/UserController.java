package com.interview.assessment.controller;

import com.interview.assessment.dto.UserRoleUpdateDTO;
import com.interview.assessment.dto.UserSummaryDTO;
import com.interview.assessment.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Module 2: admin-only screen for managing recruiter/panel/admin accounts. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserSummaryDTO> list() {
        return userService.listAll();
    }

    @PutMapping("/{id}/role")
    public UserSummaryDTO updateRole(@PathVariable Long id, @Valid @RequestBody UserRoleUpdateDTO dto) {
        return userService.updateRole(id, dto);
    }
}
