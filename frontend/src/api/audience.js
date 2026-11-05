import api from './axios';

export const registerAudience = async (data) => (await api.post('/audience/register', data)).data;
export const loginAudience = async (data) => (await api.post('/audience/login', data)).data;
export const getAudienceAccount = async () => (await api.get('/audience/me')).data;
export const linkAudienceParticipant = async (participantToken) => (
  await api.post('/audience/participants/link', null, { headers: { 'X-Participant-Token': participantToken } })
).data;
export const exportAudienceProfile = async () => (await api.get('/audience/profile/export')).data;
export const deleteAudienceProfile = async () => api.delete('/audience/profile');
export const deleteAudienceAccount = async () => api.delete('/audience/account');
export const startGithubAudienceLogin = () => {
  window.location.assign('/api/audience/oauth/github/authorize');
};
