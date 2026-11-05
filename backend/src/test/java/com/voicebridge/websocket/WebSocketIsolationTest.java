package com.voicebridge.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebridge.dto.request.CreateMeetingRequest;
import com.voicebridge.dto.request.JoinMeetingRequest;
import com.voicebridge.dto.request.RegisterOrganizerRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebSocketIsolationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndLogin(String email) throws Exception {
        RegisterOrganizerRequest register = new RegisterOrganizerRequest();
        register.setName("Isolation Test Organizer");
        register.setEmail(email);
        register.setPassword("SecurePass123!");

        ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:" + port + "/api/auth/register", register, String.class);
        return objectMapper.readTree(response.getBody()).get("token").asText();
    }

    private String createMeeting(String token, String title) throws Exception {
        CreateMeetingRequest request = new CreateMeetingRequest();
        request.setTitle(title);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<CreateMeetingRequest> entity = new HttpEntity<>(request, headers);
        ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:" + port + "/api/meetings", entity, String.class);

        return objectMapper.readTree(response.getBody()).get("meetingCode").asText();
    }

    private WebSocketStompClient buildStompClient() {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.initialize();
        stompClient.setTaskScheduler(taskScheduler);
        return stompClient;
    }

    @Test
    @DisplayName("WEBSOCKET ISOLATION: Events in Meeting A must NOT be broadcast to Meeting B subscribers")
    void multiMeetingIsolation_EventNotReceivedByOtherMeeting() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String token = registerAndLogin("iso_" + unique + "@test.com");

        String codeA = createMeeting(token, "Meeting A");
        String codeB = createMeeting(token, "Meeting B");

        WebSocketStompClient clientA = buildStompClient();
        WebSocketStompClient clientB = buildStompClient();

        BlockingQueue<MeetingEvent> eventsA = new LinkedBlockingQueue<>();
        BlockingQueue<MeetingEvent> eventsB = new LinkedBlockingQueue<>();

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        StompSession sessionA = clientA.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
        }).get(5, TimeUnit.SECONDS);
        StompSession sessionB = clientB.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {
        }).get(5, TimeUnit.SECONDS);

        sessionA.setAutoReceipt(true);
        sessionB.setAutoReceipt(true);

        StompHeaders headersA = new StompHeaders();
        headersA.setDestination("/topic/meetings/" + codeA);
        sessionA.subscribe(headersA, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MeetingEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                eventsA.add((MeetingEvent) payload);
            }
        });

        StompHeaders headersB = new StompHeaders();
        headersB.setDestination("/topic/meetings/" + codeB);
        sessionB.subscribe(headersB, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MeetingEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                eventsB.add((MeetingEvent) payload);
            }
        });

        Thread.sleep(500);

        // Trigger Event in Meeting A
        JoinMeetingRequest joinA = new JoinMeetingRequest();
        joinA.setName("Participant A");
        restTemplate.postForEntity("http://localhost:" + port + "/api/participants/join/" + codeA, joinA, String.class);

        // Verify Meeting A receives event
        MeetingEvent eventA = eventsA.poll(5, TimeUnit.SECONDS);
        assertThat(eventA).isNotNull();
        assertThat(eventA.getType()).isEqualTo(MeetingEventType.PARTICIPANT_JOINED);

        // Verify Meeting B receives NOTHING
        MeetingEvent eventB = eventsB.poll(2, TimeUnit.SECONDS);
        assertThat(eventB).isNull();

        sessionA.disconnect();
        sessionB.disconnect();
    }
}
