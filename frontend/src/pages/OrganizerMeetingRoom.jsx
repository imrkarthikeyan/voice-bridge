import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Navbar } from '../components/Navbar';
import { Footer } from '../components/Footer';
import { QrCodeModal } from '../components/QrCodeModal';
import { PresentationUploadModal } from '../components/presentation/PresentationUploadModal';
import { PresentationSelector } from '../components/presentation/PresentationSelector';
import { PresentationViewer } from '../components/presentation/PresentationViewer';
import { SlideInspector } from '../components/presentation/SlideInspector';
import { MeetingControlSidebar } from '../components/presentation/MeetingControlSidebar';
import { MeetingIntelligencePanel } from '../components/MeetingIntelligencePanel';
import { useStompWebSocket } from '../hooks/useStompWebSocket';
import { useWebRtcListener } from '../hooks/useWebRtcListener';
import { getMeetingByCode, closeMeeting } from '../api/meetings';
import { listParticipants } from '../api/participants';
import {
  getSpeakingQueue,
  approveSpeaker,
  rejectSpeaker,
  endCurrentSpeaker,
  reorderQueue,
} from '../api/speaking';
import {
  getPresentations,
  getMeetingPresentationSession,
  startPresentation,
} from '../api/presentations';
import {
  downloadMeetingDocument,
  generateMeetingDocument,
  getMeetingDocument,
  publishMeetingDocument,
  reviewMeetingDocument,
} from '../api/documents';
import {
  QrCode,
  Power,
  Upload,
  AlertTriangle,
  Presentation as PresentationIcon,
  ChevronDown,
  ChevronUp,
  Volume2,
  VolumeX,
} from 'lucide-react';

export const OrganizerMeetingRoom = () => {
  const { meetingCode } = useParams();
  const navigate = useNavigate();

  const [meeting, setMeeting] = useState(null);
  const [participants, setParticipants] = useState([]);
  const [queue, setQueue] = useState([]);
  const [presentations, setPresentations] = useState([]);
  const [presentationSession, setPresentationSession] = useState(null);
  const [activePresentation, setActivePresentation] = useState(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [transcript, setTranscript] = useState('');
  const [transcriptTranslation, setTranscriptTranslation] = useState(null);
  const [transcriptSpeaker, setTranscriptSpeaker] = useState('');
  const [transcriptSlideNumber, setTranscriptSlideNumber] = useState(null);
  const [transcriptHistory, setTranscriptHistory] = useState([]);
  const [meetingDocument, setMeetingDocument] = useState(null);
  const [documentBusy, setDocumentBusy] = useState(false);

  const [isQrOpen, setIsQrOpen] = useState(false);
  const [isUploadOpen, setIsUploadOpen] = useState(false);
  const [isClosingModal, setIsClosingModal] = useState(false);
  const [showPresentationList, setShowPresentationList] = useState(false);
  const [slideAnalysisRefreshKey, setSlideAnalysisRefreshKey] = useState(0);

  // Real-time STOMP connection
  const { isConnected, lastEvent, signalMessage, subtitleMessage, sendSignal } = useStompWebSocket(meetingCode);

  // Active Speaker calculation
  const activeSpeakerRequest = queue.find((q) => q.status === 'SPEAKING' || q.status === 'APPROVED');
  const activeSpeakerName = activeSpeakerRequest ? activeSpeakerRequest.participantName : null;

  // WebRTC Listener for audio stream
  const { isReceivingAudio, audioLevel, closeConnection, isAutoplayBlocked, unmuteAudio } = useWebRtcListener(
    sendSignal,
    signalMessage,
    activeSpeakerRequest?.participantId
  );

  // Listen for Live Subtitles (TRANSCRIPT) signals
  useEffect(() => {
    if (!subtitleMessage?.payload) return;
    const payload = subtitleMessage.payload;
    if (payload.text) {
      setTranscript(payload.text);
      setTranscriptTranslation(payload.translation || null);
      if (payload.speakerName) setTranscriptSpeaker(payload.speakerName);
      setTranscriptSlideNumber(payload.slideNumber || null);
      if (!payload.partial) {
        setTranscriptHistory((current) => [
          ...current.slice(-49),
          { id: payload.id || `${Date.now()}-${current.length}`, slideNumber: payload.slideNumber, text: payload.text },
        ]);
      }
    }
  }, [subtitleMessage]);

  useEffect(() => {
    if (!signalMessage) return;
    if (signalMessage.type === 'TRANSCRIPT' && signalMessage.text) {
      setTranscript(signalMessage.text);
      if (signalMessage.speakerName) {
        setTranscriptSpeaker(signalMessage.speakerName);
      }
      setTranscriptSlideNumber(signalMessage.slideNumber || null);
      setTranscriptTranslation(signalMessage.translation || null);
      if (signalMessage.isFinal && signalMessage.text) {
        setTranscriptHistory((current) => [
          ...current.slice(-49),
          { id: signalMessage.segmentId || `${Date.now()}-${current.length}`, slideNumber: signalMessage.slideNumber, text: signalMessage.text },
        ]);
      }
    }
  }, [signalMessage]);

  useEffect(() => {
    if (!activeSpeakerName) {
      setTranscript('');
      setTranscriptSpeaker('');
      setTranscriptSlideNumber(null);
      setTranscriptTranslation(null);
    }
  }, [activeSpeakerName]);

  const fetchRoomData = useCallback(async () => {
    try {
      const mData = await getMeetingByCode(meetingCode);
      setMeeting(mData);

      if (mData.id) {
        const [pData, qData, presData] = await Promise.all([
          listParticipants(mData.id),
          getSpeakingQueue(mData.id),
          getPresentations(mData.id),
        ]);
        setParticipants(pData);
        setQueue(qData);
        setPresentations(presData);

        // Fetch active presentation session if available
        let hasActiveSession = false;
        try {
          const sessionData = await getMeetingPresentationSession(mData.id);
          setPresentationSession(sessionData);

          if (sessionData && sessionData.presentationId) {
            hasActiveSession = true;
            const active = presData.find((p) => p.id === sessionData.presentationId);
            if (active) {
              setActivePresentation(active);
            }
          }
        } catch {
          // No active session yet
          setPresentationSession(null);
        }

        // If no session active but presentations exist, default active presentation to latest
        if (!hasActiveSession && presData.length > 0) {
          setActivePresentation((prev) => prev || presData[0]);
        }
      }
    } catch (err) {
      console.error('Failed to load meeting room:', err);
      if (err.response?.status === 401) {
        navigate('/login');
      } else {
        setError(err.response?.data?.message || 'Meeting not found or access denied');
      }
    } finally {
      setLoading(false);
    }
  }, [meetingCode, navigate]);

  useEffect(() => {
    fetchRoomData();
  }, [fetchRoomData]);

  useEffect(() => {
    getMeetingDocument(meetingCode).then(setMeetingDocument).catch(() => setMeetingDocument(null));
  }, [meetingCode]);

  const generateDocument = async () => {
    setDocumentBusy(true);
    try {
      setMeetingDocument(await generateMeetingDocument(meetingCode));
    } finally {
      setDocumentBusy(false);
    }
  };

  const reviewDocument = async () => setMeetingDocument(await reviewMeetingDocument(meetingCode));
  const publishDocument = async () => setMeetingDocument(await publishMeetingDocument(meetingCode));

  // Handle STOMP events (queue, participants, presentations)
  useEffect(() => {
    if (!lastEvent) return;

    const type = lastEvent.type;
    if (
      type === 'PARTICIPANT_JOINED' ||
      type === 'PARTICIPANT_LEFT' ||
      type === 'HAND_RAISED' ||
      type === 'SPEAKER_APPROVED' ||
      type === 'SPEAKER_REJECTED' ||
      type === 'SPEAKER_STARTED' ||
      type === 'SPEAKER_ENDED' ||
      type === 'QUEUE_UPDATED'
    ) {
      fetchRoomData();
    } else if (type === 'PRESENTATION_STARTED' || type === 'PRESENTATION_STOPPED' || type === 'SLIDE_CHANGED') {
      if (lastEvent.payload) {
        setPresentationSession(lastEvent.payload);
        if (lastEvent.payload.presentationId) {
          setPresentations((currentList) => {
            const match = currentList.find((p) => p.id === lastEvent.payload.presentationId);
            if (match) {
              setActivePresentation(match);
            }
            return currentList;
          });
        }
      } else {
        fetchRoomData();
      }
    } else if (type === 'SLIDE_ANALYSIS_UPDATED') {
      setSlideAnalysisRefreshKey((key) => key + 1);
    } else if (type === 'MEETING_CLOSED') {
      navigate('/dashboard');
    }
  }, [lastEvent, fetchRoomData, navigate]);

  // Handlers for Queue Actions
  const handleApprove = async (requestId) => {
    try {
      await approveSpeaker(requestId);
      fetchRoomData();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to approve speaker');
    }
  };

  const handleReject = async (requestId) => {
    try {
      await rejectSpeaker(requestId);
      fetchRoomData();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to reject request');
    }
  };

  const handleEndSpeaker = async () => {
    if (!meeting?.id) return;
    try {
      await endCurrentSpeaker(meeting.id);
      closeConnection();
      fetchRoomData();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to end speaker');
    }
  };

  const handleReorder = async (index, direction) => {
    const waitingRequests = queue.filter((q) => q.status === 'WAITING');
    if (index < 0 || index >= waitingRequests.length) return;
    const targetIndex = direction === 'up' ? index - 1 : index + 1;
    if (targetIndex < 0 || targetIndex >= waitingRequests.length) return;

    const newWaiting = [...waitingRequests];
    const temp = newWaiting[index];
    newWaiting[index] = newWaiting[targetIndex];
    newWaiting[targetIndex] = temp;

    const newOrderIds = newWaiting.map((q) => q.id);
    try {
      await reorderQueue(meeting.id, newOrderIds);
      fetchRoomData();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to reorder queue');
    }
  };

  const handleCloseMeeting = async () => {
    try {
      await closeMeeting(meetingCode);
      navigate('/dashboard');
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to close meeting');
    }
  };

  // Presentation Handlers
  const handleSelectPresentation = (pres) => {
    setActivePresentation(pres);
  };

  const handleStartPresentation = async (presId) => {
    try {
      const updatedSession = await startPresentation(presId);
      setPresentationSession(updatedSession);
      const match = presentations.find((p) => p.id === presId);
      if (match) setActivePresentation(match);
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to start presentation');
    }
  };

  const handleUploadSuccess = (newPres) => {
    setPresentations((prev) => [newPres, ...prev]);
    setActivePresentation(newPres);
    fetchRoomData();
  };

  if (loading) {
    return (
      <div className="min-h-screen flex flex-col bg-slate-950 text-slate-100">
        <Navbar isConnected={isConnected} />
        <div className="flex-1 flex items-center justify-center">
          <div className="w-8 h-8 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
        </div>
      </div>
    );
  }

  if (error || !meeting) {
    return (
      <div className="min-h-screen flex flex-col bg-slate-950 text-slate-100">
        <Navbar isConnected={isConnected} />
        <div className="flex-1 flex flex-col items-center justify-center p-4 text-center">
          <AlertTriangle className="w-12 h-12 text-amber-400 mb-3" />
          <h2 className="text-xl font-bold text-white font-heading">{error || 'Session Not Found'}</h2>
          <button
            onClick={() => navigate('/dashboard')}
            className="mt-4 px-4 py-2 rounded-xl bg-indigo-600 text-white text-xs font-semibold"
          >
            Back to Dashboard
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col bg-slate-950 text-slate-100">
      <Navbar isConnected={isConnected} />

      {/* QR Code Modal */}
      <QrCodeModal
        isOpen={isQrOpen}
        onClose={() => setIsQrOpen(false)}
        meetingCode={meeting.meetingCode}
        title={meeting.title}
        joinUrl={meeting.joinUrl}
      />

      {/* Presentation Upload Modal */}
      <PresentationUploadModal
        meetingId={meeting.id}
        isOpen={isUploadOpen}
        onClose={() => setIsUploadOpen(false)}
        onUploadSuccess={handleUploadSuccess}
      />

      {/* Browser Autoplay Blocked Warning Banner */}
      {isAutoplayBlocked && (
        <div className="bg-amber-500/15 border-b border-amber-500/30 px-4 py-2.5 flex items-center justify-between text-amber-200 text-xs">
          <div className="flex items-center gap-2">
            <VolumeX className="w-4 h-4 text-amber-400 animate-bounce" />
            <span>Browser restricted automatic audio output. Click to enable live audience speaker sound.</span>
          </div>
          <button
            onClick={unmuteAudio}
            className="px-3.5 py-1 bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold rounded-lg text-xs transition-colors flex items-center gap-1.5 cursor-pointer shadow-md shadow-amber-500/20"
          >
            <Volume2 className="w-3.5 h-3.5" />
            <span>Enable Speaker Audio</span>
          </button>
        </div>
      )}

      {/* Top Header Control Bar */}
      <div className="bg-slate-900/90 border-b border-slate-800 px-4 sm:px-8 py-3">
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center space-x-3">
            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 flex items-center space-x-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
              <span>LIVE WORKSPACE</span>
            </span>
            <span className="font-mono text-xs font-bold text-indigo-400 bg-indigo-500/10 px-2.5 py-0.5 rounded-md border border-indigo-500/20">
              CODE: {meeting.meetingCode}
            </span>
            <h1 className="text-lg font-bold text-white font-heading truncate hidden sm:inline">{meeting.title}</h1>
          </div>

          <div className="flex items-center space-x-2.5">
            {/* Speaker Audio Manual Unmute Button if receiving audio */}
            {isReceivingAudio && (
              <button
                type="button"
                onClick={unmuteAudio}
                className="flex items-center space-x-1.5 px-3 py-1.5 rounded-xl bg-emerald-500/20 text-emerald-300 text-xs font-semibold border border-emerald-500/30 hover:bg-emerald-500/30 transition-all"
                title="Ensure audio output is active"
              >
                <Volume2 className="w-3.5 h-3.5 text-emerald-400 animate-pulse" />
                <span>Audio Output Active</span>
              </button>
            )}

            <button
              type="button"
              onClick={() => setIsUploadOpen(true)}
              className="flex items-center space-x-1.5 px-3.5 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold transition-all shadow-md shadow-indigo-600/20"
            >
              <Upload className="w-3.5 h-3.5" />
              <span>Upload Presentation</span>
            </button>

            <button
              type="button"
              onClick={() => setIsQrOpen(true)}
              className="flex items-center space-x-1.5 px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold border border-slate-700 transition-all"
            >
              <QrCode className="w-3.5 h-3.5 text-indigo-400" />
              <span>QR Code</span>
            </button>

            <button
              type="button"
              onClick={() => setIsClosingModal(true)}
              className="flex items-center space-x-1.5 px-3 py-1.5 rounded-xl bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 text-xs font-semibold border border-rose-500/30 transition-all"
            >
              <Power className="w-3.5 h-3.5" />
              <span>Close Meeting</span>
            </button>
          </div>
        </div>
      </div>

      {/* Main Integrated Workspace Layout */}
      <main className="flex-1 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 w-full">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Left Column (8 Cols): Presentation Workspace */}
          <div className="lg:col-span-8 flex flex-col space-y-4">
            {activePresentation ? (
              <PresentationViewer
                presentation={activePresentation}
                session={presentationSession}
                isOrganizer={true}
                onSessionChange={(newSession) => setPresentationSession(newSession)}
                activeSpeakerName={activeSpeakerName || transcriptSpeaker}
                transcript={transcript}
                transcriptTranslation={transcriptTranslation}
                transcriptSlideNumber={transcriptSlideNumber}
                isLiveSpeaker={isReceivingAudio || !!activeSpeakerName}
              />
            ) : (
              <div className="flex flex-col items-center justify-center p-12 bg-slate-900/80 border border-slate-800 rounded-2xl text-center space-y-4 shadow-xl">
                <div className="p-4 bg-indigo-500/10 text-indigo-400 rounded-2xl">
                  <PresentationIcon className="w-12 h-12" />
                </div>
                <div>
                  <h3 className="text-xl font-bold text-white">No Presentation Active</h3>
                  <p className="text-sm text-slate-400 max-w-md mt-1">
                    Upload a PPTX or PDF file to start presenting slides side-by-side with VoiceBridge audience microphone controls.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setIsUploadOpen(true)}
                  className="flex items-center space-x-2 px-5 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-sm rounded-xl shadow-lg shadow-indigo-600/20 transition-all"
                >
                  <Upload className="w-4 h-4" />
                  <span>Upload Presentation</span>
                </button>
              </div>
            )}
            {activePresentation && presentationSession && (
              <SlideInspector
                presentationId={activePresentation.id}
                slideNumber={presentationSession.currentSlide}
                refreshKey={slideAnalysisRefreshKey}
              />
            )}

            {/* Accordion / Drawer for Presentation Selector */}
            {presentations.length > 0 && (
              <div className="bg-slate-900/60 border border-slate-800 rounded-2xl overflow-hidden">
                <button
                  type="button"
                  onClick={() => setShowPresentationList((prev) => !prev)}
                  className="w-full flex items-center justify-between px-5 py-3 text-xs font-semibold text-slate-300 hover:text-white transition-colors"
                >
                  <span className="flex items-center space-x-2">
                    <PresentationIcon className="w-4 h-4 text-indigo-400" />
                    <span>Manage Uploaded Presentations ({presentations.length})</span>
                  </span>
                  {showPresentationList ? (
                    <ChevronUp className="w-4 h-4 text-slate-400" />
                  ) : (
                    <ChevronDown className="w-4 h-4 text-slate-400" />
                  )}
                </button>

                {showPresentationList && (
                  <div className="p-4 border-t border-slate-800 animate-in fade-in duration-150">
                    <PresentationSelector
                      presentations={presentations}
                      activePresentationId={activePresentation?.id}
                      isPresenting={presentationSession?.presenting}
                      onSelectPresentation={handleSelectPresentation}
                      onStartPresentation={handleStartPresentation}
                      onOpenUpload={() => setIsUploadOpen(true)}
                      onRefresh={fetchRoomData}
                    />
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Right Column (4 Cols): VoiceBridge Control Sidebar */}
          <div className="lg:col-span-4">
            <MeetingControlSidebar
              meeting={meeting}
              participants={participants}
              queue={queue}
              activeSpeakerRequest={activeSpeakerRequest}
              isReceivingAudio={isReceivingAudio}
              audioLevel={audioLevel}
              onApprove={handleApprove}
              onReject={handleReject}
              onEndSpeaker={handleEndSpeaker}
              onReorder={handleReorder}
              onOpenQrModal={() => setIsQrOpen(true)}
            />
            <MeetingIntelligencePanel meetingCode={meeting.meetingCode} />
            <section className="mt-4 bg-slate-900/80 border border-slate-800 rounded-2xl p-4">
              <h3 className="text-sm font-semibold text-white">Meeting document (PDF)</h3>
              <p className="mt-1 text-xs text-slate-400">
                Assemble slides, aligned transcript, questions, grounded summaries, and citations.
              </p>
              <div className="mt-3 flex flex-wrap gap-2">
                <button type="button" disabled={documentBusy} onClick={generateDocument}
                  className="rounded-lg bg-indigo-600 px-3 py-2 text-xs font-semibold text-white disabled:opacity-50">
                  {documentBusy ? 'Generating…' : 'Generate draft'}
                </button>
                {meetingDocument?.status === 'DRAFT' && (
                  <button type="button" onClick={reviewDocument}
                    className="rounded-lg bg-amber-600 px-3 py-2 text-xs font-semibold text-white">
                    Mark reviewed
                  </button>
                )}
                {meetingDocument?.status === 'REVIEWED' && (
                  <button type="button" onClick={publishDocument}
                    className="rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white">
                    Publish
                  </button>
                )}
                {meetingDocument && (
                  <button type="button" onClick={() => downloadMeetingDocument(meetingCode)}
                    className="rounded-lg bg-slate-700 px-3 py-2 text-xs font-semibold text-white">
                    Download PDF
                  </button>
                )}
              </div>
              {meetingDocument && <p className="mt-2 text-xs text-slate-400">Status: {meetingDocument.status}</p>}
            </section>
            {transcriptHistory.length > 0 && (
              <section className="mt-4 bg-slate-900/80 border border-slate-800 rounded-2xl p-4">
                <h3 className="text-sm font-semibold text-white mb-3">Transcript by slide</h3>
                <div className="space-y-3 max-h-64 overflow-y-auto">
                  {Object.entries(transcriptHistory.reduce((groups, item) => {
                    const key = item.slideNumber || 'Unaligned';
                    return { ...groups, [key]: [...(groups[key] || []), item] };
                  }, {})).map(([slide, items]) => (
                    <div key={slide}>
                      <p className="text-xs font-semibold text-indigo-300 mb-1">
                        {slide === 'Unaligned' ? slide : `Slide ${slide}`}
                      </p>
                      {items.map((item) => <p key={item.id} className="text-xs text-slate-300">{item.text}</p>)}
                    </div>
                  ))}
                </div>
              </section>
            )}
          </div>
        </div>
      </main>

      {/* Close Meeting Confirmation Modal */}
      {isClosingModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-in fade-in">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-md w-full p-6 shadow-2xl relative text-center">
            <Power className="w-12 h-12 text-rose-400 mx-auto mb-3" />
            <h2 className="text-xl font-bold font-heading text-white">Close Meeting Session?</h2>
            <p className="text-slate-400 text-xs mt-2 mb-6">
              This will disconnect all audience members, close presentation sessions, and end live audio streams.
            </p>

            <div className="flex items-center justify-center space-x-3">
              <button
                type="button"
                onClick={() => setIsClosingModal(false)}
                className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleCloseMeeting}
                className="px-5 py-2 rounded-xl bg-rose-600 hover:bg-rose-500 text-white text-xs font-semibold shadow-md shadow-rose-600/20 transition-all"
              >
                Confirm Close
              </button>
            </div>
          </div>
        </div>
      )}

      <Footer />
    </div>
  );
};
