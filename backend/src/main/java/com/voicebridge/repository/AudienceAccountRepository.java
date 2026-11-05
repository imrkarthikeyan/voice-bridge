package com.voicebridge.repository;
import com.voicebridge.entity.AudienceAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface AudienceAccountRepository extends JpaRepository<AudienceAccount, Long> {
    Optional<AudienceAccount> findByEmail(String email);
    Optional<AudienceAccount> findByGithubSubject(String githubSubject);
    boolean existsByEmail(String email);
}
