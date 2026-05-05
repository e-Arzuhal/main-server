package com.earzuhal.config;

import com.earzuhal.security.CustomUserDetailsService;
import com.earzuhal.security.AuthRateLimitFilter;
import com.earzuhal.security.jwt.JwtAuthenticationEntryPoint;
import com.earzuhal.security.jwt.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthRateLimitFilter authRateLimitFilter;

    @Value("${swagger.enabled:false}")
    private boolean swaggerEnabled;

    public SecurityConfig(CustomUserDetailsService customUserDetailsService,
                         JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                         JwtAuthenticationFilter jwtAuthenticationFilter,
                         AuthRateLimitFilter authRateLimitFilter) {
        this.customUserDetailsService = customUserDetailsService;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authRateLimitFilter = authRateLimitFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentTypeOptions(opt -> {})
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                        )
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'")
                        )
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> {
                    auth
                        // Public endpoints
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/landing/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                    // Swagger UI — only in dev (swagger.enabled=true)
                    if (swaggerEnabled) {
                        auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                    }

                    auth
                        // User endpoints
                        .requestMatchers("/api/users/me", "/api/users/me/**").authenticated()

                        // TC Kimlik lookup — sözleşme oluştururken karşı tarafı
                        // doğrulamak için her authenticated user'a açık olmalı.
                        // ADMIN-only "/api/users/**" matcher'ından ÖNCE tanımlandı,
                        // aksi halde mobil/web client lookup yaparken 401/403
                        // alıp oturumdan düşüyor.
                        .requestMatchers(HttpMethod.GET, "/api/users/lookup").authenticated()

                        // Disclaimer endpoints — controller okuma/kayıt için kimlik
                        // doğrulanmış kullanıcı bekliyor; permitAll altında anonim
                        // istekler "anonymousUser" adıyla işlem yapardı.
                        .requestMatchers("/api/disclaimer/**").authenticated()

                        // Admin endpoints
                        .requestMatchers("/api/users/**").hasRole("ADMIN")

                        // All other endpoints require authentication
                        .anyRequest().authenticated();
                })
                .authenticationProvider(authenticationProvider())
                // Spring Security 7'de addFilterBefore yalnızca kayıtlı (Spring Security'nin
                // tanıdığı) filtre sınıflarını referans alabilir. İki özel filtreyi de
                // UsernamePasswordAuthenticationFilter'dan önce ekliyoruz; ekleme sırası
                // çalışma sırasıdır: önce rate-limit, ardından JWT doğrulaması.
                .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
