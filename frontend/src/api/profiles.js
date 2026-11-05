import api from './axios';

const sessionHeaders = () => ({
  'X-Participant-Token': localStorage.getItem('vb_session_token'),
});

export const getMyProfile = async () => {
  const response = await api.get('/participants/profile', { headers: sessionHeaders() });
  return response.data;
};

export const updateMyProfile = async (profile) => {
  const response = await api.put('/participants/profile', profile, { headers: sessionHeaders() });
  return response.data;
};

export const getParticipantProfile = async (participantId) => {
  const response = await api.get(`/meetings/participants/${participantId}/profile`);
  return response.data;
};
