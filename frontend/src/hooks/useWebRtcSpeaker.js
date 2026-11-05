import { useEffect, useRef, useState, useCallback } from 'react';

const ICE_SERVERS = {
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' },
    { urls: 'stun:stun2.l.google.com:19302' },
    { urls: 'stun:stun3.l.google.com:19302' },
    { urls: 'stun:stun4.l.google.com:19302' },
    { urls: 'stun:stun.services.mozilla.com' },
    ...(import.meta.env.VITE_TURN_URL
      ? [{
        urls: import.meta.env.VITE_TURN_URL,
        username: import.meta.env.VITE_TURN_USERNAME,
        credential: import.meta.env.VITE_TURN_PASSWORD,
      }]
      : []),
  ],
};

export const useWebRtcSpeaker = (sendSignal, signalMessage, participantId) => {
  const peerConnectionRef = useRef(null);
  const localStreamRef = useRef(null);
  const pendingIceCandidatesRef = useRef([]);
  const audioContextRef = useRef(null);
  const analyserRef = useRef(null);

  const [isLive, setIsLive] = useState(false);
  const [localStream, setLocalStream] = useState(null);
  const [audioLevel, setAudioLevel] = useState(0);
  const [error, setError] = useState(null);

  // Clean up streaming & mic tracks
  const stopStreaming = useCallback(() => {
    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((track) => track.stop());
      localStreamRef.current = null;
    }
    setLocalStream(null);

    if (peerConnectionRef.current) {
      peerConnectionRef.current.close();
      peerConnectionRef.current = null;
    }
    pendingIceCandidatesRef.current = [];

    if (audioContextRef.current) {
      audioContextRef.current.close();
      audioContextRef.current = null;
    }

    setIsLive(false);
    setAudioLevel(0);
  }, []);

  // Initialize Audio Visualizer
  const setupAudioVisualizer = (stream) => {
    try {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      const audioCtx = new AudioCtx();
      const analyser = audioCtx.createAnalyser();
      analyser.fftSize = 64;

      const source = audioCtx.createMediaStreamSource(stream);
      source.connect(analyser);

      audioContextRef.current = audioCtx;
      analyserRef.current = analyser;

      const dataArray = new Uint8Array(analyser.frequencyBinCount);
      let animationFrameId;

      const updateLevel = () => {
        analyser.getByteFrequencyData(dataArray);
        let sum = 0;
        for (let i = 0; i < dataArray.length; i++) {
          sum += dataArray[i];
        }
        const average = sum / dataArray.length;
        const normalized = Math.min(100, Math.round((average / 128) * 100));
        setAudioLevel(normalized);

        animationFrameId = requestAnimationFrame(updateLevel);
      };

      updateLevel();
      return () => cancelAnimationFrame(animationFrameId);
    } catch (e) {
      console.warn('AudioContext not supported or failed to start visualizer:', e);
    }
  };

  // Start Mic & WebRTC Peer Connection
  const startStreaming = useCallback(async () => {
    try {
      setError(null);
      // Clean up previous peer connection if any
      stopStreaming();

      // Acquire mic stream with audio enhancements & safety check for insecure context (http://<ip>)
      const getUserMedia =
        navigator.mediaDevices?.getUserMedia?.bind(navigator.mediaDevices) ||
        (navigator.getUserMedia
          ? (constraints) => new Promise((resolve, reject) => navigator.getUserMedia(constraints, resolve, reject))
          : null);

      if (!getUserMedia) {
        throw new Error(
          'Microphone access (getUserMedia) is blocked by your browser because this site is accessed over HTTP (non-secure context). Modern mobile browsers require HTTPS or localhost to access the microphone.'
        );
      }

      const stream = await getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
        video: false,
      });

      localStreamRef.current = stream;
      setLocalStream(stream);
      setupAudioVisualizer(stream);

      // Create WebRTC peer connection
      const pc = new RTCPeerConnection(ICE_SERVERS);
      peerConnectionRef.current = pc;

      // Add local audio tracks to peer connection
      stream.getTracks().forEach((track) => pc.addTrack(track, stream));

      // Handle ICE Candidates
      pc.onicecandidate = (event) => {
        if (event.candidate) {
          sendSignal({
            type: 'ICE_CANDIDATE',
            from: 'SPEAKER',
            participantId,
            candidate: event.candidate,
          });
        }
      };

      pc.onconnectionstatechange = () => {
        if (pc.connectionState === 'connected') {
          setIsLive(true);
        } else if (pc.connectionState === 'disconnected' || pc.connectionState === 'failed') {
          setIsLive(false);
        }
      };

      // Create SDP Offer
      const offer = await pc.createOffer({
        offerToReceiveAudio: false,
        offerToReceiveVideo: false,
      });
      await pc.setLocalDescription(offer);

      // Send SDP Offer to Organizer via WebSocket
      sendSignal({
        type: 'OFFER',
        from: 'SPEAKER',
        participantId,
        sdp: offer.sdp,
      });

      setIsLive(true);
    } catch (err) {
      console.error('Failed to start microphone or WebRTC:', err);
      setError(err.message || 'Could not access microphone');
      setIsLive(false);
    }
  }, [sendSignal, stopStreaming, participantId]);

  // Handle incoming signals from Organizer (SDP Answer & ICE Candidates)
  useEffect(() => {
    if (!signalMessage) return;

    const handleSignal = async () => {
      const pc = peerConnectionRef.current;
      if (!pc) return;

      if (
        signalMessage.from === 'ORGANIZER'
        && (!signalMessage.participantId || signalMessage.participantId === participantId)
      ) {
        if (signalMessage.type === 'ANSWER' && signalMessage.sdp) {
          try {
            const remoteDesc = new RTCSessionDescription({
              type: 'answer',
              sdp: signalMessage.sdp,
            });
            await pc.setRemoteDescription(remoteDesc);
            for (const candidate of pendingIceCandidatesRef.current) {
              await pc.addIceCandidate(candidate);
            }
            pendingIceCandidatesRef.current = [];
          } catch (e) {
            console.error('Failed to set remote description answer:', e);
          }
        } else if (signalMessage.type === 'ICE_CANDIDATE' && signalMessage.candidate) {
          try {
            const candidate = new RTCIceCandidate(signalMessage.candidate);
            if (pc.remoteDescription) {
              await pc.addIceCandidate(candidate);
            } else {
              pendingIceCandidatesRef.current.push(candidate);
            }
          } catch (e) {
            console.error('Failed to add ICE candidate:', e);
          }
        }
      }
    };

    handleSignal();
  }, [signalMessage, participantId]);

  useEffect(() => {
    return () => {
      stopStreaming();
    };
  }, [stopStreaming]);

  return {
    isLive,
    audioLevel,
    error,
    localStream,
    startStreaming,
    stopStreaming,
  };
};
