import { useEffect, useRef, useState, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export const useStompWebSocket = (meetingCode) => {
  const stompClientRef = useRef(null);
  const pendingMessagesRef = useRef([]);
  const [isConnected, setIsConnected] = useState(false);
  const [lastEvent, setLastEvent] = useState(null);
  const [signalMessage, setSignalMessage] = useState(null);
  const [subtitleMessage, setSubtitleMessage] = useState(null);

  useEffect(() => {
    if (!meetingCode) return;

    // Create STOMP client over SockJS
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      connectHeaders: (() => {
        const organizerToken = localStorage.getItem('vb_token');
        const participantToken = localStorage.getItem('vb_session_token');
        return organizerToken
          ? { Authorization: `Bearer ${organizerToken}` }
          : participantToken
            ? { 'X-Participant-Token': participantToken }
            : {};
      })(),
      reconnectDelay: 3000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (_str) => {
        if (import.meta.env.DEV) {
          // console.log('[STOMP]', _str);
        }
      },
      onConnect: () => {
        setIsConnected(true);

        // Subscribe to meeting events (join, leave, raise hand, approved, speaker start, speaker end, closed)
        client.subscribe(`/topic/meetings/${meetingCode}`, (message) => {
          try {
            const body = JSON.parse(message.body);
            setLastEvent(body);
          } catch (e) {
            console.error('Failed to parse meeting event:', e);
          }
        });

        // Subscribe to WebRTC signaling messages
        client.subscribe(`/topic/meetings/${meetingCode}/signal`, (message) => {
          try {
            const body = JSON.parse(message.body);
            setSignalMessage(body);
          } catch (e) {
            console.error('Failed to parse signaling message:', e);
          }
        });

        client.subscribe(`/topic/meetings/${meetingCode}/subtitles`, (message) => {
          try {
            setSubtitleMessage(JSON.parse(message.body));
          } catch (e) {
            console.error('Failed to parse subtitle message:', e);
          }
        });

        pendingMessagesRef.current.forEach((signalData) => {
          client.publish({
            destination: `/app/meetings/${meetingCode}/signal`,
            body: JSON.stringify(signalData),
          });
        });
        pendingMessagesRef.current = [];
      },
      onDisconnect: () => {
        setIsConnected(false);
      },
      onStompError: (frame) => {
        console.error('STOMP Error:', frame.headers['message']);
      },
    });

    client.activate();
    stompClientRef.current = client;

    return () => {
      pendingMessagesRef.current = [];
      if (stompClientRef.current) {
        stompClientRef.current.deactivate();
      }
    };
  }, [meetingCode]);

  const sendSignal = useCallback((signalData) => {
    if (!meetingCode) return;

    if (stompClientRef.current?.connected) {
      stompClientRef.current.publish({
        destination: `/app/meetings/${meetingCode}/signal`,
        body: JSON.stringify(signalData),
      });
      return;
    }

    pendingMessagesRef.current.push(signalData);
  }, [meetingCode]);

  return {
    isConnected,
    lastEvent,
    signalMessage,
    subtitleMessage,
    sendSignal,
  };
};
