import api from './axios';

export const getSlideAnalysis = async (presentationId) => {
  const response = await api.get(`/presentations/${presentationId}/analysis`);
  return response.data;
};

export const updateSlideAnalysis = async (presentationId, slideId, data) => {
  const response = await api.put(`/presentations/${presentationId}/analysis/${slideId}`, data);
  return response.data;
};

export const createQuestion = async (meetingCode, text, originalText = '', language = '') => {
  const response = await api.post(`/meetings/${meetingCode}/questions`, { text, originalText, language }, {
    headers: { 'X-Participant-Token': localStorage.getItem('vb_session_token') },
  });
  return response.data;
};

export const getQuestions = async (meetingCode) => {
  const response = await api.get(`/meetings/${meetingCode}/questions`, {
    headers: { 'X-Participant-Token': localStorage.getItem('vb_session_token') || undefined },
  });
  return response.data;
};

export const getTimeline = async (meetingCode) => {
  const response = await api.get(`/meetings/${meetingCode}/timeline`);
  return response.data;
};

export const getMeetingContext = async (meetingCode) => {
  const response = await api.get(`/meetings/${meetingCode}/intelligence/context`);
  return response.data;
};

export const getQuestionAnalytics = async (meetingCode) => {
  const response = await api.get(`/meetings/${meetingCode}/intelligence/questions/analytics`);
  return response.data;
};

export const reviewQuestion = async (meetingCode, questionId, status) => {
  const response = await api.patch(`/meetings/${meetingCode}/intelligence/questions/${questionId}`, { status });
  return response.data;
};
