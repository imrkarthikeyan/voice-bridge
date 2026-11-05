package com.voicebridge.service.impl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebridge.dto.request.*; import com.voicebridge.dto.response.*;
import com.voicebridge.entity.*; import com.voicebridge.exception.*;
import com.voicebridge.repository.*; import com.voicebridge.security.*;
import com.voicebridge.service.AudienceAccountService; import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
@Service
public class AudienceAccountServiceImpl implements AudienceAccountService {
 private final AudienceAccountRepository accounts; private final ParticipantRepository participants;
 private final ParticipantAccountLinkRepository links; private final PasswordEncoder encoder; private final JwtService jwt;
 private final AudienceProfileRepository profiles; private final ObjectMapper mapper = new ObjectMapper();
 private final RestClient restClient;
 private final Map<String, Long> oauthStates = new ConcurrentHashMap<>();
 @Value("${app.audience.oauth.github.enabled:false}") private boolean githubEnabled;
 @Value("${app.audience.oauth.github.client-id:}") private String githubClientId;
 @Value("${app.audience.oauth.github.client-secret:}") private String githubClientSecret;
 @Value("${app.audience.oauth.github.callback-url:}") private String githubCallbackUrl;
 @Value("${app.audience.oauth.github.frontend-success-url:http://localhost:5173/audience/oauth/callback}") private String githubSuccessUrl;

 public AudienceAccountServiceImpl(AudienceAccountRepository accounts, ParticipantRepository participants,
  ParticipantAccountLinkRepository links, PasswordEncoder encoder, JwtService jwt,
  AudienceProfileRepository profiles, RestClient.Builder restClientBuilder) {
  this.accounts=accounts; this.participants=participants; this.links=links; this.encoder=encoder; this.jwt=jwt;
  this.profiles=profiles; this.restClient=restClientBuilder.build();
 }
 @Override @Transactional public AudienceAuthResponse register(RegisterAudienceRequest r) {
  String email=r.getEmail().trim().toLowerCase(); if(accounts.existsByEmail(email)) throw new DuplicateResourceException("An audience account with this email already exists");
  AudienceAccount a=accounts.save(AudienceAccount.builder().email(email).displayName(r.getDisplayName().trim()).passwordHash(encoder.encode(r.getPassword())).build());
  return auth(a);
 }
 @Override @Transactional(readOnly=true) public AudienceAuthResponse login(LoginRequest r) {
  AudienceAccount a=accounts.findByEmail(r.getEmail().trim().toLowerCase()).orElseThrow(()->new InvalidCredentialsException("Invalid email or password"));
  if(!encoder.matches(r.getPassword(),a.getPasswordHash())) throw new InvalidCredentialsException("Invalid email or password"); return auth(a);
 }
 private AudienceAuthResponse auth(AudienceAccount a) { String token=jwt.generateToken(new AudiencePrincipal(a)); return AudienceAuthResponse.builder().token(token).tokenType("Bearer").expiresAt(jwt.extractExpiration(token)).audience(toResponse(a)).build(); }
 @Override @Transactional(readOnly=true) public AudienceAccountResponse me(Long id) { return toResponse(accounts.findById(id).orElseThrow(()->new ResourceNotFoundException("Audience account not found"))); }
 @Override @Transactional public void linkParticipant(Long id,String token) {
  AudienceAccount a=accounts.findById(id).orElseThrow(()->new ResourceNotFoundException("Audience account not found"));
  Participant p=participants.findWithMeetingBySessionToken(token).orElseThrow(()->new ResourceNotFoundException("Participant session not found"));
  links.findById(p.getId()).ifPresent(existing->{if(!existing.getAccount().getId().equals(id)) throw new DuplicateResourceException("Participant is already linked");});
  links.save(ParticipantAccountLink.builder().participantId(p.getId()).account(a).build());
 }
 @Override @Transactional(readOnly=true) public Object exportProfile(Long id) {
  return profiles.findByParticipantId(links.findAll().stream().filter(l->l.getAccount().getId().equals(id)).findFirst().map(ParticipantAccountLink::getParticipantId).orElse(-1L))
    .map(p->Map.of("account",me(id),"profile",p)).orElse(Map.of("account",me(id)));
 }
 @Override @Transactional public void deleteAccount(Long id) { AudienceAccount a=accounts.findById(id).orElseThrow(()->new ResourceNotFoundException("Audience account not found")); links.deleteAll(links.findAll().stream().filter(l->l.getAccount().getId().equals(id)).toList()); accounts.delete(a); }
 @Override @Transactional public void deleteProfile(Long id) {
  links.findAll().stream().filter(l -> l.getAccount().getId().equals(id))
      .forEach(l -> profiles.deleteByParticipantId(l.getParticipantId()));
 }
 private AudienceAccountResponse toResponse(AudienceAccount a){return AudienceAccountResponse.builder().id(a.getId()).email(a.getEmail()).displayName(a.getDisplayName()).createdAt(a.getCreatedAt()).build();}
 @Override public String githubAuthorizationUrl() {
  if (!githubEnabled || githubClientId.isBlank() || githubClientSecret.isBlank()) throw new IllegalStateException("GitHub OAuth is disabled");
  String state=UUID.randomUUID().toString(); oauthStates.put(state, System.currentTimeMillis()+600_000);
  return "https://github.com/login/oauth/authorize?client_id=" + enc(githubClientId) +
      "&redirect_uri=" + enc(githubCallbackUrl) + "&scope=user:email&state=" + enc(state);
 }
 @Override @Transactional public String completeGithubLogin(String code, String state) {
  Long expires=oauthStates.remove(state);
  if (expires == null || expires < System.currentTimeMillis()) throw new InvalidCredentialsException("Invalid or expired OAuth state");
  if (!githubEnabled || githubClientSecret.isBlank()) throw new InvalidCredentialsException("GitHub OAuth is disabled");
  Map<?,?> token=restClient.post().uri("https://github.com/login/oauth/access_token")
      .header("Accept","application/json").body(Map.of("client_id",githubClientId,"client_secret",githubClientSecret,"code",code))
      .retrieve().body(Map.class);
  String accessToken=(String) token.get("access_token");
  if (accessToken == null) throw new InvalidCredentialsException("GitHub authorization failed");
  String body=restClient.get().uri("https://api.github.com/user").header("Authorization","Bearer "+accessToken)
      .header("Accept","application/vnd.github+json").retrieve().body(String.class);
  try {
   var user=mapper.readTree(body); String subject=user.path("id").asText(); String email=user.path("email").asText(null);
   if (email == null || email.isBlank()) {
    var emails=mapper.readTree(restClient.get().uri("https://api.github.com/user/emails")
        .header("Authorization","Bearer "+accessToken).header("Accept","application/vnd.github+json")
        .retrieve().body(String.class));
    for (var entry : emails) {
     if (entry.path("verified").asBoolean() && entry.path("primary").asBoolean()) { email=entry.path("email").asText(); break; }
    }
   }
   if (email == null || email.isBlank()) throw new InvalidCredentialsException("A public GitHub email is required");
   String resolvedEmail = email.toLowerCase();
   AudienceAccount account=accounts.findByGithubSubject(subject).orElseGet(() -> accounts.findByEmail(resolvedEmail)
       .orElseGet(() -> AudienceAccount.builder().email(resolvedEmail).displayName(user.path("name").asText(user.path("login").asText("Audience"))).passwordHash(encoder.encode(UUID.randomUUID().toString())).build()));
   account.setGithubSubject(subject); if (account.getDisplayName()==null || account.getDisplayName().isBlank()) account.setDisplayName(user.path("login").asText("Audience"));
   account=accounts.save(account); return githubSuccessUrl+"#token="+enc(jwt.generateToken(new AudiencePrincipal(account)));
  } catch (Exception ex) { if (ex instanceof InvalidCredentialsException ice) throw ice; throw new InvalidCredentialsException("Invalid GitHub profile response"); }
 }
 private static String enc(String value) { return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8); }
}
