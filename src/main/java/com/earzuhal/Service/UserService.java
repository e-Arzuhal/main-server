package com.earzuhal.Service;

import com.earzuhal.Model.User;
import com.earzuhal.Repository.UserRepository;
import com.earzuhal.dto.user.ChangePasswordRequest;
import com.earzuhal.dto.user.NotificationPreferencesRequest;
import com.earzuhal.dto.user.NotificationPreferencesResponse;
import com.earzuhal.dto.user.UserResponse;
import com.earzuhal.dto.user.UserUpdateRequest;
import com.earzuhal.exception.BadRequestException;
import com.earzuhal.exception.ResourceNotFoundException;
import com.earzuhal.exception.UserAlreadyExistsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
    @Autowired
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TcKimlikEncryptionService encryptionService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       TcKimlikEncryptionService encryptionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.encryptionService = encryptionService;
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return convertToResponse(user);
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    public User getUserByUsernameOrEmail(String usernameOrEmail) {
        return userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username or email: " + usernameOrEmail));
    }

    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest updateRequest) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        if (updateRequest.getFirstName() != null) {
            user.setFirstName(updateRequest.getFirstName());
        }

        if (updateRequest.getLastName() != null) {
            user.setLastName(updateRequest.getLastName());
        }

        if (updateRequest.getEmail() != null && !updateRequest.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(updateRequest.getEmail())) {
                throw new UserAlreadyExistsException("Email already in use");
            }
            user.setEmail(updateRequest.getEmail());
        }

        user.setUpdatedAt(OffsetDateTime.now());
        User updatedUser = userRepository.save(user);

        return convertToResponse(updatedUser);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        userRepository.delete(user);
    }

    public java.util.Map<String, Object> lookupByTcKimlik(String tcKimlik) {
        // Frontend plaintext TC gönderir; DB'de şifreli aranmalı
        String encryptedTc = encryptionService.encrypt(tcKimlik);
        return userRepository.findByTcKimlik(encryptedTc)
                .map(u -> {
                    java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                    result.put("found", true);
                    result.put("displayName", u.getFirstName() != null && u.getLastName() != null
                            ? u.getFirstName() + " " + u.getLastName()
                            : u.getUsername());
                    return result;
                })
                .orElseGet(() -> {
                    java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                    result.put("found", false);
                    return result;
                });
    }

    public NotificationPreferencesResponse getNotificationPreferences(String username) {
        User user = getUserByUsernameOrEmail(username);
        return NotificationPreferencesResponse.builder()
                .email(Boolean.TRUE.equals(user.getNotifEmail()))
                .sms(Boolean.TRUE.equals(user.getNotifSms()))
                .push(Boolean.TRUE.equals(user.getNotifPush()))
                .contractUpdates(Boolean.TRUE.equals(user.getNotifContractUpdates()))
                .approvalRequests(Boolean.TRUE.equals(user.getNotifApprovalRequests()))
                .marketing(Boolean.TRUE.equals(user.getNotifMarketing()))
                .build();
    }

    @Transactional
    public NotificationPreferencesResponse updateNotificationPreferences(
            String username, NotificationPreferencesRequest request) {
        User user = getUserByUsernameOrEmail(username);

        if (request.getEmail() != null) user.setNotifEmail(request.getEmail());
        if (request.getSms() != null) user.setNotifSms(request.getSms());
        if (request.getPush() != null) user.setNotifPush(request.getPush());
        if (request.getContractUpdates() != null) user.setNotifContractUpdates(request.getContractUpdates());
        if (request.getApprovalRequests() != null) user.setNotifApprovalRequests(request.getApprovalRequests());
        if (request.getMarketing() != null) user.setNotifMarketing(request.getMarketing());

        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
        return getNotificationPreferences(username);
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = getUserByUsernameOrEmail(username);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Mevcut şifre hatalı");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Yeni şifre mevcut şifreyle aynı olamaz");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    @Transactional
    public void deleteCurrentUser(String username) {
        User user = getUserByUsernameOrEmail(username);
        userRepository.delete(user);
    }

    public boolean isUsernameExists(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean isEmailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    public UserResponse convertToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
