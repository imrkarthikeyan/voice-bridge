# VoiceBridge — System Architecture & Design Specification

---

## 1. HIGH-LEVEL SYSTEM ARCHITECTURE

```mermaid
flowchart TB
    subgraph Clients["Client Tier"]
        Audience["Audience Mobile Browser\n(Anonymous Session Token)"]
        Organizer["Organizer Desktop Browser\n(JWT Authenticated)"]
    end

    subgraph Edge["Gateway / Proxy Tier"]
        Nginx["Nginx Web Server\n(Frontend Port 80 / Static Assets)"]
    end

    subgraph Application Tier["Spring Boot Backend Application (Port 8080)"]
        Security["Spring Security\n(JwtAuthenticationFilter & CORS)"]
        RateLimiter["Rate Limiting Interceptor\n(Bucket4j Sliding Window)"]
        
        subgraph Controllers["REST & WebSocket Controllers"]
            AuthController["AuthController"]
            MeetingController["MeetingController"]
            ParticipantController["ParticipantController"]
            SpeakingController["SpeakingRequestController"]
            PresentationController["PresentationController"]
            SignalingController["WebRtcSignalingController"]
        end
        
        subgraph BusinessServices["Domain Service Layer"]
            AuthService["AuthServiceImpl"]
            MeetingService["MeetingServiceImpl"]
            ParticipantService["ParticipantServiceImpl"]
            SpeakingService["SpeakingRequestServiceImpl"]
            PresentationService["PresentationServiceImpl"]
            SlideProcessorService["SlideProcessorService\n(Apache POI & PDFBox Engine)"]
            EventPublisher["MeetingEventPublisher\n(STOMP Broker Channel)"]
        end
    end

    subgraph Data Tier["Persistence & Storage Tier"]
        PostgreSQL[("PostgreSQL 16 Database\n(Flyway Schema Migrations)")]
        DiskStorage["Local File System Storage\n(uploads/presentations/{uuid})"]
    end

    %% Network & Protocols
    Audience -->|HTTP REST APIs & STOMP /ws| Security
    Organizer -->|HTTP REST APIs & STOMP /ws| Security
    Audience <==>|Peer-to-Peer WebRTC Audio Stream| Organizer

    Security --> RateLimiter
    RateLimiter --> Controllers
    Controllers --> BusinessServices

    BusinessServices -->|Spring Data JPA| PostgreSQL
    SlideProcessorService -->|Store / Load PNG Slides| DiskStorage
    EventPublisher -->|Broadcast STOMP Events| Audience
    EventPublisher -->|Broadcast STOMP Events| Organizer
```

---

## 2. REAL-TIME WEBRTC SIGNALING & AUDIO FLOW

```mermaid
sequenceDiagram
    autonumber
    participant A as Audience (Speaker Browser)
    participant WS as Backend STOMP WS Broker
    participant O as Organizer (Presenter Browser)

    Note over O, WS: Host approves speaker via REST PATCH /speaking-requests/{id}/approve
    WS-->>A: Broadcast STOMP Event: SPEAKER_APPROVED
    WS-->>O: Broadcast STOMP Event: SPEAKER_APPROVED
    
    A->>A: User taps "Start Mic" -> navigator.mediaDevices.getUserMedia()
    A->>A: Create RTCPeerConnection & Add Audio Track
    A->>A: Create WebRTC SDP Offer
    
    A->>WS: SEND /app/meetings/{code}/signal (Type: OFFER, Role: SPEAKER, SDP)
    WS->>O: RELAY /topic/meetings/{code}/signal (Type: OFFER, Role: SPEAKER, SDP)
    
    O->>O: Receive OFFER -> Set Remote Description
    O->>O: Create WebRTC SDP Answer -> Set Local Description
    O->>WS: SEND /app/meetings/{code}/signal (Type: ANSWER, Role: ORGANIZER, SDP)
    WS->>A: RELAY /topic/meetings/{code}/signal (Type: ANSWER, Role: ORGANIZER, SDP)
    
    A->>A: Receive ANSWER -> Set Remote Description
    
    par ICE Candidate Exchange
        A->>WS: SEND Signal (Type: ICE_CANDIDATE, Role: SPEAKER)
        WS->>O: RELAY Signal (Type: ICE_CANDIDATE)
        O->>WS: SEND Signal (Type: ICE_CANDIDATE, Role: ORGANIZER)
        WS->>A: RELAY Signal (Type: ICE_CANDIDATE)
    end

    Note over A, O: Direct Peer-to-Peer Audio Stream Established (WebRTC P2P)
    A->>O: Media Stream (Live Mic Audio)
    O->>O: Play audio via HTML5 <audio> element -> Output to Venue Speakers
```

---

## 3. DATABASE RELATIONSHIP DIAGRAM (ERD)

```mermaid
erDiagram
    ORGANIZERS ||--o{ MEETINGS : "creates"
    MEETINGS ||--o{ PARTICIPANTS : "contains"
    MEETINGS ||--o{ SPEAKING_REQUESTS : "manages"
    MEETINGS ||--o{ PRESENTATIONS : "hosts"
    MEETINGS ||--o| PRESENTATION_SESSIONS : "active_session"
    MEETINGS ||--o{ SPEAKER_HISTORY : "audits"
    PARTICIPANTS ||--o{ SPEAKING_REQUESTS : "raises"
    PARTICIPANTS ||--o{ SPEAKER_HISTORY : "records"
    PRESENTATIONS ||--o| PRESENTATION_SESSIONS : "currently_presenting"

    ORGANIZERS {
        bigint id PK
        varchar email UK
        varchar name
        varchar password_hash
        timestamp created_at
    }

    MEETINGS {
        bigint id PK
        varchar meeting_code UK
        varchar qr_token UK
        bigint organizer_id FK
        varchar status
        timestamp created_at
    }

    PARTICIPANTS {
        bigint id PK
        bigint meeting_id FK
        varchar name
        varchar session_token UK
        varchar status
        boolean active
    }

    SPEAKING_REQUESTS {
        bigint id PK
        bigint meeting_id FK
        bigint participant_id FK
        varchar status
        integer queue_position
        timestamp requested_at
    }

    SPEAKER_HISTORY {
        bigint id PK
        bigint meeting_id FK
        bigint participant_id FK
        bigint duration_seconds
        varchar end_reason
    }

    PRESENTATIONS {
        bigint id PK
        bigint meeting_id FK
        varchar file_name
        varchar storage_path
        integer total_slides
        integer current_slide
    }

    PRESENTATION_SESSIONS {
        bigint id PK
        bigint meeting_id FK, UK
        bigint presentation_id FK
        integer current_slide
        boolean is_presenting
    }
```

---

## 4. PRESENTATION PROCESSING & SLIDE CONVERSION WORKFLOW

```mermaid
flowchart TD
    A["Organizer Uploads .pptx or .pdf File\n(POST /api/meetings/{id}/presentations)"] --> B{"Validate File & Size"}
    B -->|File > 50MB or Invalid Extension| C["Throw BusinessRuleViolationException\n(HTTP 400)"]
    B -->|Valid File| D["Sanitize Filename & Create Target Dir\n(uploads/presentations/{uuid})"]
    D --> E{"Detect File Extension"}
    
    E -->|.pptx| F["Apache POI Engine\n(XMLSlideShow & XSLFSlide)"]
    E -->|.pdf| G["Apache PDFBox Engine\n(PDDocument & PDFRenderer)"]
    
    F --> H["Render Each Slide to Java BufferedImage"]
    G --> H
    
    H --> I["Write Slide Images to Disk\n(slide_1.png, slide_2.png...)"]
    I --> J["Save Presentation Record to DB"]
    J --> K["Return PresentationResponse DTO\n(total_slides, current_slide)"]
```

---

## 5. SINGLE-ACTIVE-SPEAKER INVARIANT & CONCURRENCY CONTROL

```mermaid
flowchart TD
    Start["Organizer Clicks Approve Speaker\n(PATCH /speaking-requests/{id}/approve)"] --> AuthCheck{"Verify Organizer Token & Ownership"}
    AuthCheck -->|Failed| Error401["HTTP 401 / 404"]
    AuthCheck -->|Passed| Lock["Acquire Service Synchronization Lock"]
    
    Lock --> CheckActive{"Query DB: Count SPEAKING / APPROVED Requests in Meeting"}
    CheckActive -->|> 0 Active Speakers| Reject["Throw BusinessRuleViolationException\n'Another participant is currently speaking'"]
    
    CheckActive -->|0 Active Speakers| Approve["Update Request Status -> APPROVED"]
    Approve --> SetHist["Log Speaker History Entry"]
    SetHist --> PubEvent["Broadcast STOMP Event SPEAKER_APPROVED"]
    PubEvent --> Unlock["Release Synchronization Lock"]
    Unlock --> ReturnDTO["Return 200 OK Response"]
```

---

## 6. END-TO-END DATA FLOW SUMMARY

1. **Host Setup:** Host authenticates with JWT (`/api/auth/login`) → Creates meeting (`/api/meetings`) → Uploads slides (`/api/meetings/{id}/presentations`) → Apache POI/PDFBox renders slides to PNGs.
2. **Audience Join:** Attendee scans QR code → Joins meeting (`/api/participants/join/{code}`) → Receives UUID `sessionToken` → Subscribes to STOMP WebSocket topic `/topic/meetings/{code}`.
3. **Queue Moderation:** Attendee raises hand → `SpeakingRequest` saved with FIFO `queue_position` → Host approves request → Synchronized lock verifies single-speaker invariant.
4. **P2P Audio & Subtitles:** Approved attendee browser sends WebRTC Offer over WebSocket → Host browser returns WebRTC Answer → P2P WebRTC audio stream connects → Host browser plays audio and passes stream to `Web Speech API` for live slide caption overlay.

### Slide intelligence

After the presentation is rendered, `SlideAnalysisService` extracts one structured record per
slide from PPTX shapes or PDF text. Each record includes editable title and concepts,
extracted text, notes, code/table flags, and `structure_json`. The record starts in `QUEUED`
state and `SlideEnrichmentService` creates an `SLIDE_ENRICH` entry in `ai_jobs`, calls the
local AI service at `/v1/slides/enrich`, and publishes `SLIDE_ANALYSIS_UPDATED` on the
meeting topic. If the optional embedding model is unavailable, deterministic local analysis
is retained and the slide remains usable.

Organizers can review and correct the active slide through `SlideInspector`; updates use
`PUT /api/presentations/{presentationId}/analysis/{slideId}`. Analysis data is never exposed
through the audience presentation endpoints.
5. **Closure:** Host closes session → Status transitions to `CLOSED` → WebSocket notifies all audience clients → Audience browsers redirect to `/closed/:code`.
