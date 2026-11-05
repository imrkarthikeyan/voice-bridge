import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Navbar } from '../components/Navbar';
import { Footer } from '../components/Footer';
import { useStompWebSocket } from '../hooks/useStompWebSocket';
import { useWebRtcSpeaker } from '../hooks/useWebRtcSpeaker';
import { useAiTranscription } from '../hooks/useAiTranscription';
import { AudioWaveform } from '../components/AudioWaveform';
import { getMyRequestStatus, startSpeaking, stopSpeaking } from '../api/speaking';
import { Mic, MicOff, AlertTriangle, ShieldCheck, Sparkles } from 'lucide-react';

export const AudienceSpeakerPage = () => {
  const { meetingCode } = useParams();
  const navigate = useNavigate();

  const sessionToken = localStorage.getItem('vb_session_token');
  const participantName = localStorage.getItem('vb_participant_name') || 'Speaker';

  const [statusInfo, setStatusInfo] = useState(null);
  const [loading, setLoading] = useState(true);
  const [micPermissionGranted, setMicPermissionGranted] = useState(false);
  const [liveTranscript, setLiveTranscript] = useState('');
  const [targetLanguage, setTargetLanguage] = useState(
    () => localStorage.getItem('vb_target_language') || 'en'
  );
  const transcriptSegmentRef = React.useRef(`segment-${crypto.randomUUID()}`);
  const transcriptRevisionRef = React.useRef(0);

  const { isConnected, signalMessage, sendSignal, lastEvent } = useStompWebSocket(meetingCode);

  // WebRTC Audio Engine hook
  const { isLive, audioLevel, error: rtcError, localStream, startStreaming, stopStreaming } = useWebRtcSpeaker(
    sendSignal,
    signalMessage,
    statusInfo?.participantId
  );

  // Real-time Speech-to-Text Transcription
  const handleTranscript = useCallback(
    (data) => {
      setLiveTranscript(data.text);
      sendSignal({
        type: 'TRANSCRIPT',
        from: 'SPEAKER',
        speakerName: participantName,
        text: data.text,
        isFinal: data.isFinal,
        segmentId: data.segmentId || transcriptSegmentRef.current,
        revision: data.revision || ++transcriptRevisionRef.current,
        language: data.language || 'und',
        translation: data.translation,
        confidence: data.confidence,
      });
    },
    [sendSignal, participantName]
  );

  useAiTranscription(localStream, isLive, handleTranscript, targetLanguage);

  const handleTargetLanguageChange = (event) => {
    const language = event.target.value;
    setTargetLanguage(language);
    localStorage.setItem('vb_target_language', language);
  };

  const checkSpeakerStatus = useCallback(async () => {
    if (!sessionToken) {
      navigate(`/join/${meetingCode}`);
      return;
    }

    try {
      const data = await getMyRequestStatus(sessionToken);
      setStatusInfo(data);

      if (!data || (data.status !== 'APPROVED' && data.status !== 'SPEAKING')) {
        stopStreaming();
        navigate(`/waiting/${meetingCode}`);
      } else if (data.status === 'APPROVED') {
        // Automatically initiate speaking state on backend
        await startSpeaking(sessionToken);
      }
    } catch (err) {
      console.error('Failed to check speaker status:', err);
    } finally {
      setLoading(false);
    }
  }, [sessionToken, meetingCode, navigate, stopStreaming]);

  useEffect(() => {
    checkSpeakerStatus();
  }, [checkSpeakerStatus]);

  // STOMP real-time events (Organizer ended speaker, meeting closed)
  useEffect(() => {
    if (!lastEvent) return;

    if (lastEvent.type === 'SPEAKER_ENDED' || lastEvent.type === 'SPEAKER_STOPPED') {
      stopStreaming();
      navigate(`/waiting/${meetingCode}`);
    } else if (lastEvent.type === 'MEETING_CLOSED') {
      stopStreaming();
      navigate(`/closed/${meetingCode}`);
    }
  }, [lastEvent, stopStreaming, meetingCode, navigate]);

  // Request mic permissions & start WebRTC stream
  const handleEnableMicrophone = async () => {
    try {
      setMicPermissionGranted(true);
      await startStreaming();
    } catch (e) {
      console.error('Mic error:', e);
    }
  };

  const handleStopSpeaking = async () => {
    try {
      stopStreaming();
      if (sessionToken) {
        await stopSpeaking(sessionToken);
      }
    } catch (e) {
      console.error('Stop speaking error:', e);
    } finally {
      navigate(`/waiting/${meetingCode}`);
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

  return (
    <div className="min-h-screen flex flex-col bg-slate-950 text-slate-100">
      <Navbar isConnected={isConnected} />

      <main className="flex-1 flex items-center justify-center p-4 py-8">
        <div className="w-full max-w-md">
          <div className="p-8 rounded-2xl glass-panel border border-red-500/30 shadow-2xl glow-emerald text-center relative overflow-hidden">
            {/* Live Indicator Banner */}
            <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-red-500/20 border border-red-500/40 text-red-400 font-bold text-xs uppercase tracking-widest mb-6 animate-pulse">
              <span className="w-2.5 h-2.5 rounded-full bg-red-500" />
              <span>YOU ARE LIVE ON SPEAKERS</span>
            </div>

            <div className="mb-6">
              <h1 className="text-2xl font-bold font-heading text-white">{participantName}</h1>
              <p className="text-slate-400 text-xs mt-1">Speak clearly into your smartphone microphone</p>
            </div>

            <label className="mb-6 flex items-center justify-between gap-3 text-left text-xs text-slate-300">
              <span>Subtitle translation</span>
              <select
                value={targetLanguage}
                onChange={handleTargetLanguageChange}
                className="rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-xs text-white outline-none focus:border-indigo-400"
              >
                <option value="en">English</option>
                <option value="ta">Tamil</option>
              </select>
            </label>

            {rtcError && (
              <div className="mb-6 p-3 rounded-xl bg-amber-500/10 border border-amber-500/20 text-amber-300 text-xs flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0" />
                <span>{rtcError}</span>
              </div>
            )}

            {!isLive && (
              <div className="my-6 p-6 rounded-2xl bg-slate-900 border border-slate-800 flex flex-col items-center gap-4">
                <div className="w-14 h-14 rounded-full bg-blue-500/10 text-blue-400 flex items-center justify-center animate-pulse">
                  <Mic className="w-7 h-7" />
                </div>
                <div>
                  <h3 className="text-lg font-bold font-heading text-white">Microphone Access Required</h3>
                  <p className="text-slate-400 text-xs mt-1">
                    Your request was approved! Tap below to allow microphone access and start speaking.
                  </p>
                </div>
                <button
                  onClick={handleEnableMicrophone}
                  className="w-full py-3 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-sm shadow-lg shadow-emerald-600/30 transition-all cursor-pointer flex items-center justify-center gap-2"
                >
                  <Mic className="w-4 h-4" />
                  <span>{micPermissionGranted ? 'Re-enable Microphone' : 'Enable Microphone'}</span>
                </button>
              </div>
            )}

            {/* Live Mic equalizers */}
            <div className="my-6">
              <AudioWaveform isLive={isLive} audioLevel={audioLevel} label="Live Microphone Level" />
            </div>

            {/* Live Subtitle Transcript Card */}
            {isLive && (
              <div className="mb-6 p-4 rounded-xl bg-slate-900/90 border border-indigo-500/30 text-left">
                <div className="flex items-center gap-1.5 text-xs font-semibold text-indigo-400 mb-1.5">
                  <Sparkles className="w-3.5 h-3.5" />
                  <span>Live Subtitles Captions</span>
                </div>
                <p className="text-xs text-slate-200 font-mono bg-slate-950 p-2.5 rounded-lg border border-slate-800 min-h-[2.5rem]">
                  {liveTranscript ? `"${liveTranscript}"` : 'Speak into your microphone to generate live subtitles...'}
                </p>
              </div>
            )}

            {/* Stop Speaking Button */}
            <button
              onClick={handleStopSpeaking}
              className="w-full py-4 rounded-2xl bg-red-600 hover:bg-red-500 text-white font-extrabold text-base shadow-xl shadow-red-600/30 flex items-center justify-center gap-2 transition-all cursor-pointer"
            >
              <MicOff className="w-6 h-6" />
              <span>Stop Speaking</span>
            </button>

            <div className="mt-4 text-slate-500 text-[11px] flex items-center justify-center gap-1">
              <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
              <span>Noise cancellation & echo suppression active</span>
            </div>
          </div>
        </div>
      </main>

      <Footer />
    </div>
  );
};
