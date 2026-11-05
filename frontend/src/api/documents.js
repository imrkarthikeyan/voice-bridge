import api from './axios';

export const generateMeetingDocument = async (meetingCode) => {
  const response = await api.post(`/meetings/${meetingCode}/document/generate`);
  return response.data;
};

export const getMeetingDocument = async (meetingCode) => {
  const response = await api.get(`/meetings/${meetingCode}/document`);
  return response.data;
};

export const reviewMeetingDocument = async (meetingCode) => {
  const response = await api.post(`/meetings/${meetingCode}/document/review`);
  return response.data;
};

export const publishMeetingDocument = async (meetingCode) => {
  const response = await api.post(`/meetings/${meetingCode}/document/publish`);
  return response.data;
};

export const downloadMeetingDocument = async (meetingCode) => {
  const response = await api.get(`/meetings/${meetingCode}/document/download`, { responseType: 'blob' });
  const url = URL.createObjectURL(response.data);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `voicebridge-${meetingCode}-meeting-document.pdf`;
  anchor.rel = 'noopener';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
};
