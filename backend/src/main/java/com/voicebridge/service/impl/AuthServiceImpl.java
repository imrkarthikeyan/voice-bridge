package com.voicebridge.service.impl;

import com.voicebridge.dto.request.LoginRequest;
import com.voicebridge.dto.request.RegisterOrganizerRequest;
import com.voicebridge.dto.response.AuthResponse;
import com.voicebridge.dto.response.OrganizerSummaryResponse;
import com.voicebridge.entity.Organizer;
import com.voicebridge.exception.DuplicateResourceException;
import com.voicebridge.exception.InvalidCredentialsException;
import com.voicebridge.mapper.OrganizerMapper;
import com.voicebridge.repository.OrganizerRepository;
import com.voicebridge.security.JwtService;
import com.voicebridge.security.OrganizerPrincipal;
import com.voicebridge.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final OrganizerRepository organizerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OrganizerMapper organizerMapper;

    @Override
    @Transactional
    public AuthResponse register(RegisterOrganizerRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (organizerRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("An organizer with this email already exists");
        }

        Organizer organizer = Organizer.builder()
                .name(request.getName().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        organizer = organizerRepository.save(organizer);
        log.info("Registered new organizer: id={}, email={}", organizer.getId(), organizer.getEmail());

        return buildAuthResponse(organizer);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        Organizer organizer = organizerRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), organizer.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        log.info("Organizer logged in: id={}, email={}", organizer.getId(), organizer.getEmail());
        return buildAuthResponse(organizer);
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizerSummaryResponse getCurrentOrganizer(Long organizerId) {
        Organizer organizer = organizerRepository.findById(organizerId)
                .orElseThrow(() -> new InvalidCredentialsException("Organizer account is no longer available"));
        return organizerMapper.toSummaryResponse(organizer);
    }

    private AuthResponse buildAuthResponse(Organizer organizer) {
        OrganizerPrincipal principal = new OrganizerPrincipal(organizer);
        String token = jwtService.generateToken(principal);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresAt(jwtService.extractExpiration(token))
                .organizer(organizerMapper.toSummaryResponse(organizer))
                .build();
    }
}
