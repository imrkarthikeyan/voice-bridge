import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { LandingPage } from './pages/LandingPage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { OrganizerDashboard } from './pages/OrganizerDashboard';
import { OrganizerMeetingRoom } from './pages/OrganizerMeetingRoom';
import { AudienceJoinPage } from './pages/AudienceJoinPage';
import { AudienceWaitingPage } from './pages/AudienceWaitingPage';
import { AudienceSpeakerPage } from './pages/AudienceSpeakerPage';
import { MeetingClosedPage } from './pages/MeetingClosedPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { AudienceAccountPage, AudienceOAuthCallbackPage } from './pages/AudienceAccountPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <Router>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/audience/account" element={<AudienceAccountPage />} />
          <Route path="/audience/oauth/callback" element={<AudienceOAuthCallbackPage />} />
          <Route path="/dashboard" element={<OrganizerDashboard />} />
          <Route path="/room/:meetingCode" element={<OrganizerMeetingRoom />} />
          <Route path="/join/:meetingCode" element={<AudienceJoinPage />} />
          <Route path="/waiting/:meetingCode" element={<AudienceWaitingPage />} />
          <Route path="/speak/:meetingCode" element={<AudienceSpeakerPage />} />
          <Route path="/closed/:meetingCode" element={<MeetingClosedPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </Router>
    </QueryClientProvider>
  );
}

export default App;
