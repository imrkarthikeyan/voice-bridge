package com.voicebridge.controller;
import com.voicebridge.dto.request.*; import com.voicebridge.dto.response.*;
import com.voicebridge.security.AudiencePrincipal; import com.voicebridge.service.AudienceAccountService;
import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
@RestController @RequestMapping("/api/audience") @RequiredArgsConstructor
public class AudienceAccountController {
 private final AudienceAccountService service;
 @Value("${app.audience.oauth.github.enabled:false}") private boolean githubOAuthEnabled;
 @PostMapping("/register") public ResponseEntity<AudienceAuthResponse> register(@Valid @RequestBody RegisterAudienceRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(service.register(r));}
 @PostMapping("/login") public AudienceAuthResponse login(@Valid @RequestBody LoginRequest r){return service.login(r);}
 @GetMapping("/me") public AudienceAccountResponse me(@AuthenticationPrincipal AudiencePrincipal p){return service.me(p.getId());}
 @PostMapping("/participants/link") @ResponseStatus(HttpStatus.NO_CONTENT) public void link(@AuthenticationPrincipal AudiencePrincipal p,@RequestHeader("X-Participant-Token") String token){service.linkParticipant(p.getId(),token);}
 @GetMapping("/profile/export") public Object export(@AuthenticationPrincipal AudiencePrincipal p){return service.exportProfile(p.getId());}
 @DeleteMapping("/profile") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteProfile(@AuthenticationPrincipal AudiencePrincipal p){service.deleteProfile(p.getId());}
 @DeleteMapping("/account") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@AuthenticationPrincipal AudiencePrincipal p){service.deleteAccount(p.getId());}
 @GetMapping("/oauth/github/authorize")
 public ResponseEntity<Void> githubAuthorize() {
  if (!githubOAuthEnabled) return ResponseEntity.notFound().build();
  return ResponseEntity.status(HttpStatus.FOUND).location(java.net.URI.create(service.githubAuthorizationUrl())).build();
 }
 @GetMapping("/oauth/github/callback")
 public ResponseEntity<Void> githubCallback(@RequestParam String code, @RequestParam String state) {
  if (!githubOAuthEnabled) return ResponseEntity.notFound().build();
  return ResponseEntity.status(HttpStatus.FOUND).location(java.net.URI.create(service.completeGithubLogin(code, state))).build();
 }
}
