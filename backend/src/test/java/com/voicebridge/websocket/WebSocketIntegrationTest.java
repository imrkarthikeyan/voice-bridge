package com.voicebridge.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebridge.dto.request.CreateMeetingRequest;
import com.voicebridge.dto.request.JoinMeetingRequest;
import com.voicebridge.dto.request.RegisterOrganizerRequest;
import com.voicebridge.webrtc.SignalMessage;
import com.voicebridge.webrtc.SignalRole;
import com.voicebridge.webrtc.SignalType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private org.springframework.boot.test.web.client.TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndLogin(String email) throws Exception {
        RegisterOrganizerRequest register = new RegisterOrganizerRequest();
        register.setName("WS Test Organizer");
        register.setEmail(email);
        register.setPassword("SecurePass123");

        org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:" + port + "/api/auth/register", register, String.class);
        return objectMapper.readTree(response.getBody()).get("token").asText();
    }

    private String createMeetingCode(String token) throws Exception {
        CreateMeetingRequest request = new CreateMeetingRequest();
        request.setTitle("WebSocket Test Meeting");

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        org.springframework.http.HttpEntity<CreateMeetingRequest> entity = new org.springframework.http.HttpEntity<>(request, headers);
        org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:" + port + "/api/meetings", entity, String.class);

        return objectMapper.readTree(response.getBody()).get("meetingCode").asText();
    }

    private WebSocketStompClient buildStompClient() {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        taskScheduler.initialize();
        stompClient.setTaskScheduler(taskScheduler);
        return stompClient;
    }

    @Test
    void participantJoin_broadcastsEventToSubscribers() throws Exception {
        String token = registerAndLogin("ws-owner1@voicebridge.test");
        String meetingCode = createMeetingCode(token);

        WebSocketStompClient stompClient = buildStompClient();
        BlockingQueue<MeetingEvent> receivedEvents = new LinkedBlockingQueue<>();

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
                })
                .get(5, TimeUnit.SECONDS);

        session.setAutoReceipt(true);
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/topic/meetings/" + meetingCode);
        headers.setReceipt("sub-join-receipt");

        session.subscribe(headers, new org.springframework.messaging.simp.stomp.StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MeetingEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                receivedEvents.add((MeetingEvent) payload);
            }
        });

        Thread.sleep(500);

        JoinMeetingRequest join = new JoinMeetingRequest();
        join.setName("WsParticipant");

        org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:" + port + "/api/participants/join/" + meetingCode, join, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        MeetingEvent event = receivedEvents.poll(10, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.getType()).isEqualTo(MeetingEventType.PARTICIPANT_JOINED);

        session.disconnect();
    }

    @Test
    void webRtcSignal_relayedToOtherSubscriber() throws Exception {
        String token = registerAndLogin("ws-owner2@voicebridge.test");
        String meetingCode = createMeetingCode(token);

        WebSocketStompClient organizerClient = buildStompClient();
        WebSocketStompClient speakerClient = buildStompClient();

        BlockingQueue<SignalMessage> organizerReceived = new LinkedBlockingQueue<>();

        StompHeaders organizerConnectHeaders = new StompHeaders();
        organizerConnectHeaders.add("Authorization", "Bearer " + token);
        StompSession organizerSession = organizerClient
                .connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), organizerConnectHeaders, new StompSessionHandlerAdapter() {
                })
                .get(5, TimeUnit.SECONDS);

        organizerSession.setAutoReceipt(true);
        StompHeaders signalHeaders = new StompHeaders();
        signalHeaders.setDestination("/topic/meetings/" + meetingCode + "/signal");
        signalHeaders.setReceipt("signal-receipt");

        organizerSession.subscribe(signalHeaders,
                new org.springframework.messaging.simp.stomp.StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return SignalMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                organizerReceived.add((SignalMessage) payload);
            }
        });

        Thread.sleep(500);

        StompSession speakerSession = speakerClient
                .connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), participantConnectHeaders(meetingCode), new StompSessionHandlerAdapter() {
                })
                .get(5, TimeUnit.SECONDS);

        SignalMessage offer = new SignalMessage();
        offer.setType(SignalType.OFFER);
        offer.setFrom(SignalRole.SPEAKER);
        offer.setSdp("v=0 fake-sdp-offer");

        StompHeaders sendHeaders = new StompHeaders();
        sendHeaders.setDestination("/app/meetings/" + meetingCode + "/signal");
        sendHeaders.add("content-type", "application/json");
        speakerSession.send(sendHeaders, offer);

        SignalMessage received = organizerReceived.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getType()).isEqualTo(SignalType.OFFER);
        assertThat(received.getFrom()).isEqualTo(SignalRole.SPEAKER);
        assertThat(received.getSdp()).isEqualTo("v=0 fake-sdp-offer");

        organizerSession.disconnect();
        speakerSession.disconnect();
    }

    private StompHeaders participantConnectHeaders(String meetingCode) throws Exception {
        JoinMeetingRequest request = new JoinMeetingRequest();
        request.setName("WebRTC Speaker");
        org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/participants/join/" + meetingCode, request, String.class);
        StompHeaders headers = new StompHeaders();
        headers.add("X-Participant-Token",
                objectMapper.readTree(response.getBody()).get("sessionToken").asText());
        return headers;
    }
}
