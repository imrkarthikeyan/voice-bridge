import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Navbar } from '../components/Navbar';
import { Footer } from '../components/Footer';
import { useStompWebSocket } from '../hooks/useStompWebSocket';
import { getParticipantBySession, leaveMeeting } from '../api/participants';
import { raiseHand, getMyRequestStatus } from '../api/speaking';
import {
  Hand,
  XCircle,
  AlertCircle,
  Radio,
  LogOut,
} from 'lucide-react';
import confetti from 'canvas-confetti';
import { QuestionComposer } from '../components/QuestionComposer';
import { getQuestions } from '../api/intelligence';
import { AudienceProfileForm } from '../components/AudienceProfileForm';

export const AudienceWaitingPage = () => {
  const { meetingCode } = useParams();
  const navigate = useNavigate();

  const sessionToken = localStorage.getItem('vb_session_token');
  const participantName = localStorage.getItem('vb_participant_name') || 'Audience Member';

  const [requestStatus, setRequestStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [raising, setRaising] = useState(false);
  const [error, setError] = useState('');
  const [questions, setQuestions] = useState([]);

  const { isConnected, lastEvent } = useStompWebSocket(meetingCode);

  const fetchStatus = useCallback(async () => {
    if (!sessionToken) {
      navigate(`/join/${meetingCode}`);
      return;
    }

    try {
      await getParticipantBySession(sessionToken);

      const rData = await getMyRequestStatus(sessionToken);
      setRequestStatus(rData);

      // If approved or currently speaking, trigger celebration and navigate to live speaker screen
      if (rData && (rData.status === 'APPROVED' || rData.status === 'SPEAKING')) {
        try {
          confetti({ particleCount: 80, spread: 60, origin: { y: 0.6 } });
        } catch {
          // ignore if canvas-confetti fails
        }
        navigate(`/speak/${meetingCode}`);
      }
    } catch (err) {
      console.error('Error fetching participant status:', err);
    } finally {
      setLoading(false);
    }
  }, [sessionToken, meetingCode, navigate]);

  useEffect(() => {
    fetchStatus();
  }, [fetchStatus]);

  useEffect(() => {
    if (!sessionToken) return undefined;
    const timer = window.setInterval(fetchStatus, 2000);
    return () => window.clearInterval(timer);
  }, [fetchStatus, sessionToken]);

  const refreshQuestions = useCallback(async () => {
    if (!sessionToken) return;
    try {
      const rows = await getQuestions(meetingCode);
      setQuestions(rows.filter((question) => question.participantName === participantName));
    } catch {
      // Question submission still works if history is temporarily unavailable.
    }
  }, [meetingCode, participantName, sessionToken]);

  useEffect(() => {
    refreshQuestions();
  }, [refreshQuestions]);

  // Handle STOMP WebSocket real-time events
  useEffect(() => {
    if (!lastEvent) return;

    if (
      lastEvent.type === 'SPEAKER_APPROVED' ||
      lastEvent.type === 'SPEAKER_REJECTED' ||
      lastEvent.type === 'SPEAKER_STARTED' ||
      (lastEvent.type === 'SPEAKER_ENDED' || lastEvent.type === 'SPEAKER_STOPPED')
    ) {
      fetchStatus();
    } else if (lastEvent.type === 'MEETING_CLOSED') {
      navigate(`/closed/${meetingCode}`);
    }
  }, [lastEvent, fetchStatus, meetingCode, navigate]);

  const handleRaiseHand = async () => {
    try {
      setRaising(true);
      setError('');
      await raiseHand(sessionToken);
      await fetchStatus();
    } catch (err) {
      console.error('Failed to raise hand:', err);
      setError(err.response?.data?.message || 'Could not raise hand');
    } finally {
      setRaising(false);
    }
  };

  const handleLeave = async () => {
    try {
      if (sessionToken) {
        await leaveMeeting(sessionToken);
      }
    } catch {
      // ignore
    } finally {
      localStorage.removeItem('vb_session_token');
      localStorage.removeItem('vb_participant_name');
      navigate('/');
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen flex flex-col bg-slate-950 text-slate-100">
        <Navbar isConnected={isConnected} />
        <div className="flex-1 flex items-center justify-center">
          <div className="w-8 h-8 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
        </div>
      </div>
    );
  }

  const isHandRaised = requestStatus && requestStatus.status === 'WAITING';

  return (
    <div className="min-h-screen flex flex-col bg-slate-950 text-slate-100">
      <Navbar isConnected={isConnected} />

      <main className="flex-1 flex items-center justify-center p-4 py-8">
        <div className="w-full max-w-md">
          <div className="p-8 rounded-2xl glass-panel border border-slate-800 shadow-2xl glow-blue text-center relative overflow-hidden">
            {/* Header info */}
            <div className="flex items-center justify-between mb-6 border-b border-slate-800/80 pb-4">
              <div className="text-left">
                <span className="text-[10px] uppercase tracking-wider font-bold text-blue-400">
                  Meeting Code: {meetingCode}
                </span>
                <h2 className="text-lg font-bold font-heading text-white">{participantName}</h2>
              </div>
              <button
                onClick={handleLeave}
                className="p-2 rounded-lg bg-slate-800 hover:bg-red-500/20 text-slate-400 hover:text-red-400 transition-colors text-xs font-semibold flex items-center gap-1"
                title="Leave Meeting"
              >
                <LogOut className="w-3.5 h-3.5" />
                <span>Leave</span>
              </button>
            </div>

            {error && (
              <div className="mb-6 p-3 rounded-xl bg-red-500/10 border border-red-500/20 flex items-center justify-center gap-2 text-red-400 text-xs">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            {/* Status Display Card */}
            {isHandRaised ? (
              <div className="my-6 p-6 rounded-2xl bg-amber-500/10 border border-amber-500/20 flex flex-col items-center gap-3">
                <div className="w-16 h-16 rounded-full bg-amber-500/20 text-amber-400 flex items-center justify-center animate-bounce">
                  <Hand className="w-8 h-8" />
                </div>
                <div>
                  <h3 className="text-xl font-bold font-heading text-amber-300">Hand Raised!</h3>
                  <p className="text-amber-200/70 text-xs mt-1">Waiting for organizer to approve your request</p>
                </div>
                {requestStatus?.queueOrder && (
                  <div className="mt-2 px-4 py-1.5 rounded-full bg-amber-500/20 text-amber-300 font-mono font-bold text-sm border border-amber-500/30">
                    Queue Position: #{requestStatus.queueOrder}
                  </div>
                )}
              </div>
            ) : requestStatus?.status === 'REJECTED' ? (
              <div className="my-6 p-6 rounded-2xl bg-red-500/10 border border-red-500/20 flex flex-col items-center gap-3">
                <div className="w-14 h-14 rounded-full bg-red-500/20 text-red-400 flex items-center justify-center">
                  <XCircle className="w-7 h-7" />
                </div>
                <div>
                  <h3 className="text-lg font-bold font-heading text-red-400">Request Passed</h3>
                  <p className="text-slate-400 text-xs mt-1">Organizer approved another speaker or rejected the request.</p>
                </div>
              </div>
            ) : (
              <div className="my-6 p-6 rounded-2xl bg-slate-900/80 border border-slate-800 flex flex-col items-center gap-3">
                <div className="w-16 h-16 rounded-full bg-blue-500/10 text-blue-400 flex items-center justify-center">
                  <Radio className="w-8 h-8 animate-pulse" />
                </div>
                <div>
                  <h3 className="text-lg font-bold font-heading text-white">Connected to Session</h3>
                  <p className="text-slate-400 text-xs mt-1">
                    When you want to ask a question, press the Raise Hand button below.
                  </p>
                </div>
              </div>
            )}
            <AudienceProfileForm />

            {/* Raise Hand Button */}
            {!isHandRaised && (
              <button
                onClick={handleRaiseHand}
                disabled={raising}
                className="w-full py-4 rounded-2xl bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white font-bold text-base shadow-xl shadow-blue-600/30 flex items-center justify-center gap-3 transition-all cursor-pointer disabled:opacity-50"
              >
                <Hand className="w-6 h-6" />
                <span>{raising ? 'Requesting...' : 'Raise Hand to Speak'}</span>
              </button>
            )}

            {isHandRaised && (
              <p className="text-xs text-slate-500 italic mt-2">
                Keep your screen on. Your phone will prompt for microphone access once approved.
              </p>
            )}

            <div className="mt-6 border-t border-slate-800 pt-5 text-left">
              <h3 className="mb-2 text-sm font-semibold text-white">Ask without taking the microphone</h3>
              <QuestionComposer meetingCode={meetingCode} onCreated={refreshQuestions} />

              {questions.length > 0 && (
                <div className="mt-6 border-t border-slate-800 pt-4 text-left">
                  <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400">My questions</h3>
                  <div className="mt-2 space-y-2">
                    {questions.map((question) => (
                      <div key={question.id} className="rounded-lg border border-slate-800 bg-slate-950/60 p-3">
                        <div className="flex items-center justify-between gap-2 text-[10px]">
                          <span className="text-indigo-300">{question.type}</span>
                          <span className="text-slate-500">{question.status}</span>
                        </div>
                        <p className="mt-1 text-xs text-slate-200">{question.text}</p>
                        {question.translatedText && (
                          <p className="mt-1 text-[11px] text-slate-400">English: {question.translatedText}</p>
                        )}
                        <p className="mt-1 text-[10px] text-slate-500">
                          {question.slideNumber ? `Slide ${question.slideNumber}` : 'Slide not assigned'} · {question.source === 'SPEECH' ? 'Spoken' : 'Typed'}
                        </p>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </main>

      <Footer />
    </div>
  );
};
