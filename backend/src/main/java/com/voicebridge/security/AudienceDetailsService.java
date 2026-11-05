package com.voicebridge.security;
import com.voicebridge.repository.AudienceAccountRepository;
import lombok.RequiredArgsConstructor; import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class AudienceDetailsService implements UserDetailsService {
 private final AudienceAccountRepository repository;
 @Override public UserDetails loadUserByUsername(String email) { return repository.findByEmail(email.toLowerCase()).map(AudiencePrincipal::new).orElseThrow(() -> new UsernameNotFoundException("Audience account not found")); }
}
