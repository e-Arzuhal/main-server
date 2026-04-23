package com.earzuhal.Controller;

import com.earzuhal.Model.User;
import com.earzuhal.Service.UserService;
import com.earzuhal.dto.user.ChangePasswordRequest;
import com.earzuhal.dto.user.NotificationPreferencesRequest;
import com.earzuhal.dto.user.NotificationPreferencesResponse;
import com.earzuhal.dto.user.UserResponse;
import com.earzuhal.dto.user.UserUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {
    @Autowired
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/lookup")
    public ResponseEntity<Map<String, Object>> lookupByTcKimlik(@RequestParam String tcKimlik) {
        return ResponseEntity.ok(userService.lookupByTcKimlik(tcKimlik));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        User user = userService.getUserByUsernameOrEmail(username);
        return ResponseEntity.ok(userService.convertToResponse(user));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUser(@Valid @RequestBody UserUpdateRequest updateRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        User user = userService.getUserByUsernameOrEmail(username);
        UserResponse updatedUser = userService.updateUser(user.getId(), updateRequest);

        return ResponseEntity.ok(updatedUser);
    }

    @GetMapping("/me/notification-preferences")
    public ResponseEntity<NotificationPreferencesResponse> getNotificationPreferences() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(userService.getNotificationPreferences(username));
    }

    @PutMapping("/me/notification-preferences")
    public ResponseEntity<NotificationPreferencesResponse> updateNotificationPreferences(
            @RequestBody NotificationPreferencesRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(userService.updateNotificationPreferences(username, request));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        userService.changePassword(username, request);
        return ResponseEntity.ok(Map.of("message", "Şifre başarıyla güncellendi"));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        userService.deleteCurrentUser(username);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id,
                                                   @Valid @RequestBody UserUpdateRequest updateRequest) {
        UserResponse updatedUser = userService.updateUser(id, updateRequest);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
