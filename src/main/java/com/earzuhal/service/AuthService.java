package com.earzuhal.service;

import com.earzuhal.Model.PasswordResetToken;
import com.earzuhal.Model.TwoFactorCode;
import com.earzuhal.Model.User;
import com.earzuhal.Repository.PasswordResetTokenRepository;
import com.earzuhal.Repository.TwoFactorCodeRepository;
import com.earzuhal.Repository.UserRepository;
import com.earzuhal.config.JwtConfig;
import com.earzuhal.dto.auth.AuthResponse;
import com.earzuhal.dto.auth.LoginRequest;
import com.earzuhal.dto.auth.RegisterRequest;
import com.earzuhal.exception.BadRequestException;
import com.earzuhal.exception.UserAlreadyExistsException;
import com.earzuhal.security.CustomUserDetailsService;
import com.earzuhal.security.jwt.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RNG = new SecureRandom();

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtConfig jwtConfig;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final TwoFactorCodeRepository twoFactorCodeRepository;
    private final MailService mailService;

    @Value("${app.public-url:http://localhost:3000}")
    private String appPublicUrl;

    public AuthService(UserRepository userRepository,
                      UserService userService,
                      PasswordEncoder passwordEncoder,
                      JwtTokenProvider jwtTokenProvider,
                      AuthenticationManager authenticationManager,
                      CustomUserDetailsService userDetailsService,
                      JwtConfig jwtConfig,
                      PasswordResetTokenRepository passwordResetTokenRepository,
                      TwoFactorCodeRepository twoFactorCodeRepository,
                      MailService mailService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtConfig = jwtConfig;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.twoFactorCodeRepository = twoFactorCodeRepository;
        this.mailService = mailService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new UserAlreadyExistsException("Bu kullanıcı adı zaten kullanılıyor.");
        }
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new UserAlreadyExistsException("Bu e-posta adresi zaten kullanılıyor.");
        }

        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        user.setPasswordHash(passwordEncoder.encode(registerRequest.getPassword()));
        user.setFirstName(registerRequest.getFirstName());
        user.setLastName(registerRequest.getLastName());
        user.setRole("USER");
        user.setIsActive(true);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        User savedUser = userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(savedUser.getUsername());
        String token = jwtTokenProvider.generateToken(userDetails);

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtConfig.getExpirationMs())
                .userInfo(userService.convertToResponse(savedUser))
                .build();
    }

    public AuthResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsernameOrEmail(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = jwtTokenProvider.generateToken(authentication);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userService.getUserByUsernameOrEmail(userDetails.getUsername());

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtConfig.getExpirationMs())
                .userInfo(userService.convertToResponse(user))
                .build();
    }

    /**
     * Şifre sıfırlama bağlantısını e-posta ile gönderir.
     * Kullanıcı bulunamazsa bile başarılı dönüyoruz (e-posta enumerasyonunu engellemek için).
     */
    @Transactional
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) return;

        Optional<User> maybeUser = userRepository.findByEmail(email.trim().toLowerCase());
        if (maybeUser.isEmpty()) {
            log.info("Şifre sıfırlama: e-posta bulunamadı, sessizce başarılı dönülüyor: {}", email);
            return;
        }
        User user = maybeUser.get();

        // Kullanıcının önceki tokenlarını geçersiz kıl
        passwordResetTokenRepository.invalidateAllForUser(user.getId());

        String token = generateUrlSafeToken(48);
        PasswordResetToken prt = new PasswordResetToken();
        prt.setToken(token);
        prt.setUserId(user.getId());
        prt.setExpiresAt(OffsetDateTime.now().plusHours(1));
        prt.setUsed(false);
        prt.setCreatedAt(OffsetDateTime.now());
        passwordResetTokenRepository.save(prt);

        String resetUrl = appPublicUrl + "/reset-password?token=" + token;
        String body = "Merhaba " + (user.getFirstName() != null ? user.getFirstName() : user.getUsername()) + ",\n\n"
                + "Şifrenizi sıfırlamak için aşağıdaki bağlantıya tıklayın (1 saat geçerli):\n\n"
                + resetUrl + "\n\n"
                + "Bu isteği siz yapmadıysanız bu e-postayı görmezden gelebilirsiniz.\n\n"
                + "e-Arzuhal Ekibi";
        mailService.send(user.getEmail(), "e-Arzuhal — Şifre Sıfırlama", body);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Geçersiz veya süresi dolmuş bağlantı.");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BadRequestException("Şifre en az 6 karakter olmalıdır.");
        }

        PasswordResetToken prt = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Geçersiz veya süresi dolmuş bağlantı."));

        if (Boolean.TRUE.equals(prt.getUsed()) || prt.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("Geçersiz veya süresi dolmuş bağlantı.");
        }

        User user = userRepository.findById(prt.getUserId())
                .stream().findFirst()
                .orElseThrow(() -> new BadRequestException("Hesap bulunamadı."));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);

        prt.setUsed(true);
        passwordResetTokenRepository.save(prt);

        // Bilgilendirme maili
        mailService.send(user.getEmail(), "e-Arzuhal — Şifreniz Değiştirildi",
                "Merhaba,\n\nHesabınızın şifresi az önce değiştirildi. Bu işlemi siz yapmadıysanız lütfen hemen destek ekibimizle iletişime geçin.\n\ne-Arzuhal Ekibi");
    }

    /** 2FA için 6 haneli kodu kullanıcıya e-posta ile gönderir. */
    @Transactional
    public void send2faCode(String username, String purpose) {
        User user = userService.getUserByUsernameOrEmail(username);
        String safePurpose = (purpose == null || purpose.isBlank()) ? "enable" : purpose.trim();

        twoFactorCodeRepository.invalidateActive(user.getId(), safePurpose);

        String code = String.format("%06d", RNG.nextInt(1_000_000));
        TwoFactorCode tfc = new TwoFactorCode();
        tfc.setUserId(user.getId());
        tfc.setCode(code);
        tfc.setPurpose(safePurpose);
        tfc.setExpiresAt(OffsetDateTime.now().plusMinutes(10));
        tfc.setUsed(false);
        tfc.setCreatedAt(OffsetDateTime.now());
        twoFactorCodeRepository.save(tfc);

        String body = "Merhaba,\n\nİki faktörlü doğrulama kodunuz: " + code + "\n\n"
                + "Bu kod 10 dakika boyunca geçerlidir. Kodu kimseyle paylaşmayın.\n\n"
                + "e-Arzuhal Ekibi";
        mailService.send(user.getEmail(), "e-Arzuhal — Doğrulama Kodu", body);
    }

    @Transactional
    public void verify2faCode(String username, String code, String action) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Lütfen doğrulama kodunu girin.");
        }
        User user = userService.getUserByUsernameOrEmail(username);
        String safePurpose = (action == null || action.isBlank()) ? "enable" : action.trim();

        TwoFactorCode tfc = twoFactorCodeRepository
                .findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(user.getId(), safePurpose)
                .orElseThrow(() -> new BadRequestException("Doğrulama kodu geçersiz veya süresi dolmuş."));

        if (tfc.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("Doğrulama kodunun süresi dolmuş, lütfen yeni kod talep edin.");
        }
        if (!tfc.getCode().equals(code.trim())) {
            throw new BadRequestException("Doğrulama kodu hatalı.");
        }

        tfc.setUsed(true);
        twoFactorCodeRepository.save(tfc);
    }

    private static String generateUrlSafeToken(int byteLen) {
        byte[] buf = new byte[byteLen];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
