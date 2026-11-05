package com.voicebridge.security;
import com.voicebridge.entity.AudienceAccount;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection; import java.util.List;
@Getter public class AudiencePrincipal implements UserDetails {
    private final AudienceAccount account;
    public AudiencePrincipal(AudienceAccount account) { this.account = account; }
    public Long getId() { return account.getId(); }
    public String getEmail() { return account.getEmail(); }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(() -> "ROLE_AUDIENCE"); }
    @Override public String getPassword() { return account.getPasswordHash(); }
    @Override public String getUsername() { return account.getEmail(); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
