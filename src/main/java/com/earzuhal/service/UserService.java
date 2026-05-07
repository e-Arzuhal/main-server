package com.earzuhal.service;

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
        return userRepository.findActiveByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
    }

    public User getUserByEmail(String email) {
        return userRepository.findActiveByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    public User getUserByUsernameOrEmail(String usernameOrEmail) {
        // Soft-deleted kullanıcılar bulunmaz; bu sayede hesabı silinmiş
        // kişi giriş yapamaz, ayar değiştiremez vb.
        return userRepository.findActiveByUsernameOrEmail(usernameOrEmail)
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
            if (Boolean.TRUE.equals(userRepository.existsActiveByEmail(updateRequest.getEmail()))) {
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
        // Frontend plaintext TC gönderir; DB'de şifreli aranmalı.
        // Yalnızca aktif (silinmemiş) kullanıcılar lookup'ta gösterilsin.
        String encryptedTc = encryptionService.encrypt(tcKimlik);
        return userRepository.findActiveByTcKimlik(encryptedTc)
                .map(u -> {
                    java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                    result.put("found", true);
                    String fullName = u.getFirstName() != null && u.getLastName() != null
                            ? u.getFirstName() + " " + u.getLastName()
                            : u.getUsername();
                    result.put("displayName", maskName(fullName));
                    return result;
                })
                .orElseGet(() -> {
                    java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                    result.put("found", false);
                    return result;
                });
    }

    /**
     * Mahremiyet için isim maskele: "Ahmet Yılmaz" -> "A**** Y*****".
     * Karşı tarafın TC'si girildiğinde tam adı sızdırmamak için kullanılır.
     */
    private String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        String[] parts = fullName.trim().split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            sb.append(p.charAt(0));
            for (int j = 1; j < p.length(); j++) sb.append('*');
            if (i < parts.length - 1) sb.append(' ');
        }
        return sb.toString();
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
        // Soft delete: row'u silmiyoruz çünkü contracts.user_id FK'si var
        // (disclaimer_acceptances, identity_verifications vs. dahil). Bunun
        // yerine deletedAt'i set edip giriş yapmasını engelliyoruz; aynı TC
        // ile yeniden kayıtta AuthService bu satırı "revive" eder.
        user.setDeletedAt(OffsetDateTime.now());
        // username ve email'i kullanılmaz hale getir → başkası aynı email'le
        // kayıt olabilsin. tcKimlik'i KORU ki kullanıcı aynı TC ile yeniden
        // kayıt olduğunda eski hesabını revive edebilelim.
        long id = user.getId();
        user.setUsername("_deleted_" + id + "_" + user.getUsername());
        // 100 char DB limiti — uzun email'leri budayarak prefix ekle
        String prefixedEmail = "_deleted_" + id + "_" + user.getEmail();
        if (prefixedEmail.length() > 100) prefixedEmail = prefixedEmail.substring(0, 100);
        user.setEmail(prefixedEmail);
        // 2FA aktifse pasifleştir; aktif token'lar zaten geçersiz sayılır
        user.setTwoFactorEnabled(false);
        user.setIsActive(false);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    public boolean isUsernameExists(String username) {
        // Soft-deleted kullanıcılar uniqueness için sayılmaz; aksi halde
        // hesabı silinmiş bir kullanıcının username'i sonsuza dek "alınmış"
        // kalırdı.
        return Boolean.TRUE.equals(userRepository.existsActiveByUsername(username));
    }

    public boolean isEmailExists(String email) {
        return Boolean.TRUE.equals(userRepository.existsActiveByEmail(email));
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
                .twoFactorEnabled(Boolean.TRUE.equals(user.getTwoFactorEnabled()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
