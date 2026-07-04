package com.interview.assessment.controller;

import com.interview.assessment.dto.CreateUserRequest;
import com.interview.assessment.dto.UserRoleUpdateDTO;
import com.interview.assessment.dto.UserSummaryDTO;
import com.interview.assessment.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    /** Admin-provisioned account creation with a role chosen up front (ADMIN/RECRUITER/PANEL). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummaryDTO create(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request);
    }

    @PutMapping("/{id}/role")
    public UserSummaryDTO updateRole(@PathVariable Long id, @Valid @RequestBody UserRoleUpdateDTO dto) {
        return userService.updateRole(id, dto);
    }
}
