import { useEffect, useRef } from 'react';

const AI_WS_URL = import.meta.env.VITE_AI_WS_URL
  || `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ai-ws`;

export const useAiTranscription = (stream, enabled, onTranscript, targetLanguage = 'en') => {
  const socketRef = useRef(null);
  const audioContextRef = useRef(null);
  const processorRef = useRef(null);
  const sourceRef = useRef(null);

  useEffect(() => {
    if (!enabled || !stream || !window.AudioContext) return undefined;

    const sessionToken = localStorage.getItem('vb_session_token');
    if (!sessionToken) return undefined;
    const separator = AI_WS_URL.includes('?') ? '&' : '?';
    const socket = new WebSocket(
      `${AI_WS_URL}${separator}sessionToken=${encodeURIComponent(sessionToken)}`
    );
    socket.binaryType = 'arraybuffer';
    socket.onmessage = (event) => {
      try {
        const result = JSON.parse(event.data);
        if (result.text) {
          onTranscript({
            text: result.text,
            isFinal: result.is_final ?? true,
            language: result.language || 'und',
            confidence: result.confidence ?? result.language_probability,
            translation: result.translation || null,
            segmentId: result.segment_id,
            revision: result.revision,
          });
        }
      } catch {
        // Ignore malformed model responses; the connection remains available.
      }
    };
    socketRef.current = socket;

    const audioContext = new AudioContext();
    const source = audioContext.createMediaStreamSource(stream);
    const processor = audioContext.createScriptProcessor(4096, 1, 1);
    let samples = [];
    let sampleCount = 0;
    let chunkNumber = 0;
    const targetSampleRate = 16000;

    const downsample = (input, inputRate) => {
      if (inputRate === targetSampleRate) return input;
      const ratio = inputRate / targetSampleRate;
      const output = new Float32Array(Math.round(input.length / ratio));
      for (let i = 0; i < output.length; i += 1) {
        output[i] = input[Math.min(input.length - 1, Math.floor(i * ratio))];
      }
      return output;
    };

    const encodeWav = (input) => {
      const buffer = new ArrayBuffer(44 + input.length * 2);
      const view = new DataView(buffer);
      const write = (offset, value) => [...value].forEach((byte, index) => view.setUint8(offset + index, byte));
      write(0, 'RIFF'); view.setUint32(4, 36 + input.length * 2, true); write(8, 'WAVE');
      write(12, 'fmt '); view.setUint32(16, 16, true); view.setUint16(20, 1, true);
      view.setUint16(22, 1, true); view.setUint32(24, targetSampleRate, true);
      view.setUint32(28, targetSampleRate * 2, true); view.setUint16(32, 2, true);
      view.setUint16(34, 16, true); write(36, 'data'); view.setUint32(40, input.length * 2, true);
      input.forEach((sample, index) => view.setInt16(44 + index * 2, Math.max(-1, Math.min(1, sample)) * 0x7fff, true));
      return buffer;
    };

    processor.onaudioprocess = (event) => {
      if (socket.readyState !== WebSocket.OPEN) return;
      const chunk = downsample(event.inputBuffer.getChannelData(0), audioContext.sampleRate);
      samples.push(chunk);
      sampleCount += chunk.length;
      if (sampleCount < targetSampleRate) return;
      const merged = new Float32Array(sampleCount);
      let offset = 0;
      samples.forEach((part) => { merged.set(part, offset); offset += part.length; });
      socket.send(encodeWav(merged));
      chunkNumber += 1;
      socket.send(JSON.stringify({
        final: chunkNumber % 4 === 0,
        target_language: targetLanguage,
      }));
      samples = [];
      sampleCount = 0;
    };

    source.connect(processor);
    processor.connect(audioContext.destination);
    audioContextRef.current = audioContext;
    sourceRef.current = source;
    processorRef.current = processor;

    return () => {
      processor.disconnect();
      source.disconnect();
      audioContext.close();
      socket.close();
      processorRef.current = null;
      sourceRef.current = null;
      audioContextRef.current = null;
      socketRef.current = null;
    };
  }, [enabled, onTranscript, stream, targetLanguage]);
};
