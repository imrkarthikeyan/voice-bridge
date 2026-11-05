package com.voicebridge.service;
import com.voicebridge.dto.request.*;
import com.voicebridge.dto.response.AudienceAuthResponse;
import com.voicebridge.dto.response.AudienceAccountResponse;
public interface AudienceAccountService {
 AudienceAuthResponse register(RegisterAudienceRequest request);
 AudienceAuthResponse login(LoginRequest request);
 AudienceAccountResponse me(Long id);
 void linkParticipant(Long accountId, String participantToken);
 Object exportProfile(Long accountId);
 void deleteAccount(Long accountId);
 void deleteProfile(Long accountId);
 String githubAuthorizationUrl();
 String completeGithubLogin(String code, String state);
}
