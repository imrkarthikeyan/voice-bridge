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

export const useWebRtcListener = (sendSignal, signalMessage, activeSpeaker) => {
  const peerConnectionRef = useRef(null);
  const audioElementRef = useRef(null);
  const audioContextRef = useRef(null);
  const analyserRef = useRef(null);
  const animationFrameRef = useRef(null);
  const pendingIceCandidatesRef = useRef([]);

  const [isReceivingAudio, setIsReceivingAudio] = useState(false);
  const [audioLevel, setAudioLevel] = useState(0);
  const [isAutoplayBlocked, setIsAutoplayBlocked] = useState(false);

  // Manual trigger to unlock browser autoplay restriction on user click
  const unmuteAudio = useCallback(async () => {
    if (audioElementRef.current) {
      try {
        audioElementRef.current.muted = false;
        await audioElementRef.current.play();
        setIsAutoplayBlocked(false);
      } catch (e) {
        console.warn('Manual audio play failed:', e);
      }
    }
    if (audioContextRef.current && audioContextRef.current.state === 'suspended') {
      try {
        await audioContextRef.current.resume();
      } catch (e) {
        console.warn('AudioContext resume failed:', e);
      }
    }
  }, []);

  // Initialize audio element in DOM & visualizer for incoming speaker stream
  const setupAudioStream = useCallback((remoteStream) => {
    if (!audioElementRef.current) {
      let audio = document.getElementById('voicebridge-organizer-audio');
      if (!audio) {
        audio = document.createElement('audio');
        audio.id = 'voicebridge-organizer-audio';
        audio.autoplay = true;
        audio.playsInline = true;
        audio.style.display = 'none';
        document.body.appendChild(audio);
      }
      audioElementRef.current = audio;
    }

    const audio = audioElementRef.current;
    audio.srcObject = remoteStream;
    audio.muted = false;

    audio.play()
      .then(() => {
        setIsAutoplayBlocked(false);
      })
      .catch((e) => {
        console.warn('Audio play auto-policy blocked:', e);
        setIsAutoplayBlocked(true);
      });

    try {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      const audioCtx = new AudioCtx();
      if (audioCtx.state === 'suspended') {
        audioCtx.resume();
      }
      const analyser = audioCtx.createAnalyser();
      analyser.fftSize = 64;
      analyser.smoothingTimeConstant = 0.5;

      const source = audioCtx.createMediaStreamSource(remoteStream);
      source.connect(analyser);

      audioContextRef.current = audioCtx;
      analyserRef.current = analyser;

      const dataArray = new Uint8Array(analyser.frequencyBinCount);

      const updateLevel = () => {
        if (audioCtx && audioCtx.state === 'suspended') {
          audioCtx.resume();
        }
        analyser.getByteFrequencyData(dataArray);
        let sum = 0;
        for (let i = 0; i < dataArray.length; i++) {
          sum += dataArray[i];
        }
        const average = sum / dataArray.length;
        const normalized = Math.min(100, Math.round((average / 128) * 100));
        setAudioLevel(normalized);

        animationFrameRef.current = requestAnimationFrame(updateLevel);
      };

      updateLevel();
    } catch (e) {
      console.warn('Could not connect visualizer to incoming audio stream:', e);
    }
  }, []);

  const closeConnection = useCallback(() => {
    if (animationFrameRef.current) {
      cancelAnimationFrame(animationFrameRef.current);
      animationFrameRef.current = null;
    }

    if (peerConnectionRef.current) {
      peerConnectionRef.current.close();
      peerConnectionRef.current = null;
    }
    pendingIceCandidatesRef.current = [];

    if (audioElementRef.current) {
      audioElementRef.current.pause();
      audioElementRef.current.srcObject = null;
    }

    if (audioContextRef.current) {
      audioContextRef.current.close();
      audioContextRef.current = null;
    }

    setIsReceivingAudio(false);
    setAudioLevel(0);
    setIsAutoplayBlocked(false);
  }, []);

  // Process incoming WebRTC signal messages from Speaker
  useEffect(() => {
    if (!signalMessage) return;

    const handleSignal = async () => {
      if (signalMessage.from === 'SPEAKER') {
        if (
          signalMessage.type === 'OFFER'
          && signalMessage.sdp
          && (!activeSpeaker || signalMessage.participantId === activeSpeaker)
        ) {
          try {
            closeConnection();

            const pc = new RTCPeerConnection(ICE_SERVERS);
            peerConnectionRef.current = pc;

            pc.ontrack = (event) => {
              const stream = (event.streams && event.streams[0]) ? event.streams[0] : new MediaStream([event.track]);
              setupAudioStream(stream);
              setIsReceivingAudio(true);
            };

            pc.onicecandidate = (event) => {
              if (event.candidate) {
                sendSignal({
                  type: 'ICE_CANDIDATE',
                  from: 'ORGANIZER',
                  participantId: signalMessage.participantId,
                  candidate: event.candidate,
                });
              }
            };

            await pc.setRemoteDescription(
              new RTCSessionDescription({ type: 'offer', sdp: signalMessage.sdp })
            );
            for (const candidate of pendingIceCandidatesRef.current) {
              await pc.addIceCandidate(candidate);
            }
            pendingIceCandidatesRef.current = [];

            const answer = await pc.createAnswer();
            await pc.setLocalDescription(answer);

            sendSignal({
              type: 'ANSWER',
              from: 'ORGANIZER',
              participantId: signalMessage.participantId,
              sdp: answer.sdp,
            });
          } catch (err) {
            console.error('Error handling WebRTC OFFER:', err);
          }
        } else if (
          signalMessage.type === 'ICE_CANDIDATE'
          && signalMessage.candidate
          && (!activeSpeaker || signalMessage.participantId === activeSpeaker)
        ) {
          const pc = peerConnectionRef.current;
          if (pc) {
            try {
              const candidate = new RTCIceCandidate(signalMessage.candidate);
              if (!pc.remoteDescription) {
                pendingIceCandidatesRef.current.push(candidate);
              } else {
                await pc.addIceCandidate(candidate);
              }
            } catch (err) {
              console.error('Error adding ICE candidate on listener:', err);
            }
          }
        }
      }
    };

    handleSignal();
  }, [signalMessage, sendSignal, setupAudioStream, closeConnection, activeSpeaker]);

  // Clean up when active speaker ends or changes
  useEffect(() => {
    if (!activeSpeaker) {
      closeConnection();
    }
  }, [activeSpeaker, closeConnection]);

  useEffect(() => {
    return () => {
      closeConnection();
    };
  }, [closeConnection]);

  return {
    isReceivingAudio,
    audioLevel,
    closeConnection,
    isAutoplayBlocked,
    unmuteAudio,
  };
};
