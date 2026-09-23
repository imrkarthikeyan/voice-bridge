# VoiceBridge — Complete Project Guide (A-to-Z Analysis)

---

## 1. PROJECT IDENTITY

- **Project Name:** VoiceBridge (voicebridge-backend / frontend)
- **What the Project Is:** VoiceBridge is a QR-code based smart audience microphone and interactive live presentation platform. It allows event organizers to present slides (PPTX/PDF) while enabling audience members to scan a QR code on their smartphones, join without creating an account, request permission to speak via a digital queue, and stream live audio directly from their phone's microphone to the venue system over WebRTC.
- **One-line Description:** A QR-based smart audience microphone system and presentation workspace enabling instant WebRTC voice interaction without hardware passed around.
- **Short Elevator Pitch:** "VoiceBridge eliminates the physical catch-box microphone at conferences and classrooms. Organizers project slides and a QR code; audience members scan with their smartphones to join anonymously, raise their hands, and stream crystal-clear WebRTC audio directly to the venue speakers when approved by the host."
- **Main Purpose:** To bridge the gap between presenters and large audiences by turning every attendee's smartphone into a managed, single-active-speaker wireless microphone paired with synchronized presentation slides.
- **Main Problem It Solves:** Eliminates the chaos, acoustic feedback, delay, hygiene concerns, and physical effort of passing physical catch-boxes or runner-held microphones in large halls.
- **Why This Problem Matters:** Handheld microphones are slow to pass around, expensive to rent, hygienically unappealing, prone to multi-speaker cross-talk, and create awkward dead air in Q&A sessions.
- **What Makes This Project Useful:** Attendees need no app installation or account creation. They simply scan a QR code or visit a short 6-character meeting link on their web browser, tap "Raise Hand", and speak when approved. Presenters retain full control over speaker approval, queue ordering, slide navigation, and live speech-to-text subtitling.
- **What Makes This Project Different From a Basic CRUD Application:**
  - Real-time peer-to-peer audio streaming via **WebRTC** (`RTCPeerConnection`).
  - Real-time STOMP WebSocket event broadcasting (`/topic/meetings/{code}`).
  - Server-side PPTX/PDF slide processing into PNG image slides using **Apache POI** & **Apache PDFBox**.
  - Single-active-speaker concurrency invariants strictly enforced at database, service, and API levels.
  - Browser Speech-to-Text (`Web Speech API`) integration for automated live subtitling over presentation slides.
  - Zero-friction token-based anonymous audience authorization paired with JWT-secured organizer authentication.

---

## 2. REAL-WORLD PROBLEM

- **What problem existed before this application?**
  In traditional physical conferences, town halls, lectures, and corporate meetings, audience Q&A sessions rely on passing physical wireless microphones or throw-boxes (like Catchbox) around the room.
- **Who experiences this problem?**
  Event organizers, keynote presenters, conference hosts, university professors, and audience attendees.
- **How do they currently solve it?**
  They employ event runners to physically sprint with heavy wireless microphones to audience members, or pass throw-cubes across seats.
- **What is inefficient about the existing process?**
  - **Time Waste:** Each question wastes 30–60 seconds while the runner walks across the auditorium.
  - **Disruption:** People sitting in middle seats struggle to grab the mic.
  - **Hygiene & Safety:** Dozens of people hold the exact same device.
  - **Chaos:** Multiple attendees speak over one another without host moderation.
  - **Audio Hardware Cost:** Purchasing or renting multiple wireless RF channels is expensive.
- **How does this application improve the process?**
  Everyone brings their own smartphone. By scanning a QR code projected on the screen, attendees enter a managed queue. The organizer approves one speaker at a time from their dashboard. The attendee's phone microphone streams audio over WebRTC directly to the organizer's computer (which is connected to the venue PA speakers). No hardware is passed around, 0 seconds are lost, and only 1 approved person can speak at a time.

---

## 3. TARGET USERS

Based on actual project implementation (`Organizer` entity and `Participant` entity):

### 1. Organizer (Authenticated Host)
- **Who they are:** Presenters, conference hosts, professors, webinar hosts.
- **What they need:** Create meetings, project presentation slides, share QR join links, moderate speaking requests, reorder queue, force-end speakers, and project live speech subtitles.
- **What they can do:**
  - Register & Login (JWT Auth).
  - Create and manage meetings (6-character code & QR token generation).
  - Upload PPTX / PDF presentation files.
  - Navigate slides (Next, Previous, Jump to Slide).
  - Moderate audience queue (Approve speaker, Reject speaker, Reorder queue, End speaker).
  - Stream WebRTC audio from speaker to venue speakers.
  - Enable live speech-to-text overlay.
  - Close meeting (blocks all future joins & requests).
- **What pages they use:** `/login`, `/register`, `/dashboard`, `/room/:meetingCode`.
- **What permissions they have:** Full administrative authority over their owned meetings and presentations. Secured via JWT `Bearer` token.
- **Value obtained:** Seamless, high-tech presentation and Q&A management without microphone hardware overhead.

### 2. Audience Participant (Anonymous Attendee)
- **Who they are:** Attendees sitting in the conference hall, classroom, or auditorium.
- **What they need:** Instant access to join the session without sign-up friction, ask questions using their own phone mic, and see their queue position.
- **What they can do:**
  - Join meeting via 6-character code or QR scan with a nickname.
  - View real-time meeting status and active speaker info.
  - Raise hand to request permission to speak.
  - View current queue position.
  - Speak live into smartphone mic via WebRTC when approved by host.
  - Stop speaking at any time.
  - Leave meeting.
- **What pages they use:** `/join/:meetingCode`, `/waiting/:meetingCode`, `/speak/:meetingCode`, `/closed/:meetingCode`.
- **What permissions they have:** Anonymous audience access tied to a unique UUID `sessionToken` stored in `localStorage` and sent via `X-Session-Token` / `X-Participant-Token`. Can only control their own speaking status.
- **Value obtained:** Equal opportunity to ask questions from any seat without waiting for physical mic runners.

---

## 4. WHAT USERS CAN DO

### 1. Registration & Authentication (Organizer Only)
- User enters Name, Email, Password.
- Backend hashes password with BCrypt and returns JWT token.
- User logs in with Email/Password to receive JWT token stored in `localStorage`.

### 2. Meeting Creation & QR Code Generation (Organizer)
- Organizer creates a meeting with title and optional description.
- System generates a unique 6-character upper-case meeting code (e.g. `X7K9P2`) and an 80-char QR join token.
- System renders a PNG QR code image (`/api/meetings/{code}/qr`) encoding the public join URL.

### 3. Presentation Upload & Processing (Organizer)
- Organizer uploads `.pptx` or `.pdf` files (up to 50MB).
- Apache POI / Apache PDFBox converts each slide/page into high-resolution PNG slide images stored on disk (`uploads/presentations/{uuid}`).
- Slide thumbnails are listed; presenter can switch active presentation and control slide navigation.

### 4. Audience Join (No Account Required)
- Attendee scans QR code or navigates to `/join/X7K9P2`.
- Enters nickname (unique per active meeting).
- Receives server-generated UUID `sessionToken` stored in browser `localStorage`.
- Automatically redirects to `/waiting/X7K9P2`.

### 5. Speaking Queue Management (Raise Hand & Moderation)
- Attendee taps **"Raise Hand"** button.
- Server assigns next queue position and broadcasts STOMP event `RAISE_HAND` to all connected clients.
- Organizer sees live queue in sidebar; can reorder candidates, approve a speaker, or reject a speaker.
- **Single-Active-Speaker Rule:** Server strictly enforces that `APPROVED` count cannot exceed 1.

### 6. Live WebRTC Audio Streaming
- When approved, attendee screen switches to `/speak/X7K9P2`.
- Attendee taps "Start Speaking", browser captures mic via `getUserMedia()`.
- WebRTC peer connection established: Speaker browser generates SDP Offer and sends over STOMP WS; Organizer browser accepts, responds with SDP Answer and exchanges ICE Candidates.
- Speaker audio streams peer-to-peer into Organizer browser and plays via HTML5 `<audio>` element (routed to venue speakers).
- Audio visualizers (Canvas waveform) display real-time volume meters on both host and speaker screens.

### 7. Live Subtitles & Presentation Overlay
- Organizer can toggle **Live Subtitles**.
- Web Speech API (`useSpeechRecognition`) transcribes speaker's voice in real-time.
- Captions overlay dynamically at the bottom of the presentation viewer canvas.

### 8. Meeting Closure & Cleanup
- Organizer clicks **"Close Meeting"**.
- Meeting status transitions to `CLOSED`.
- All active WebSocket subscribers receive `MEETING_CLOSED` event.
- Audience browsers automatically redirect to `/closed/:meetingCode`.
- Closed meetings reject all subsequent join attempts, queue requests, and WebRTC signals.

---

## 5. COMPLETE USER JOURNEY

```
                  ORGANIZER JOURNEY                                    AUDIENCE JOURNEY
       
                1. Visit Landing Page                                1. Scan QR Code on Screen
                         ↓                                                   ↓
                2. Register / Login                                  2. Open /join/:meetingCode
                         ↓                                                   ↓
           3. Dashboard: Create Meeting                             3. Enter Nickname & Join
                         ↓                                                   ↓
             4. Upload PPTX/PDF Presentation                         4. Redirect to /waiting/:code
                         ↓                                                   ↓
         5. Enter Room: Project Slides & QR                          5. Tap "Raise Hand" (Queue Pos)
                         ↓                                                   ↓
             6. See Audience Join Live                               6. Wait for Host Approval
                         ↓                                                   ↓
           7. Click "Approve" on Speaker                            7. Auto Redirect to /speak/:code
                         ↓                                                   ↓
       8. Hear Live WebRTC Audio + Subtitles                         8. Tap "Start Mic" & Speak
                         ↓                                                   ↓
          9. Click "End Speaker" / Slide Next                        9. Tap "Stop Speaking" / End
                         ↓                                                   ↓
             10. Close Meeting Session                              10. Redirect to /closed/:code
```

---

## 6. COMPLETE FEATURE LIST

### Authentication & Authorization
- **Organizer Registration & Login:** JWT-based stateless authentication (`/api/auth/register`, `/api/auth/login`).
- **Password Hashing:** BCrypt password encoding with strength factor 10.
- **Anonymous Audience Authorization:** UUID `sessionToken` passed via custom headers `X-Session-Token` or `X-Participant-Token`.
- **Resource Ownership & IDOR Protection:** Meeting operations verify organizer ID matching; throwing 404/403 on mismatched access.

### Meeting Management
- **Meeting Creation:** Unique 6-character alphanumeric code generator (`MeetingCodeGenerator`).
- **QR Code Generation:** Dynamic ZXing PNG QR code rendering (`/api/meetings/{code}/qr`).
- **Meeting Lifecycle States:** `CREATED` → `IN_PROGRESS` → `CLOSED`.
- **Join Info Pre-validation:** Endpoint `/api/meetings/join/{code}` checking title, status, and active attendee count before join.

### Presentation System
- **Multi-Format Processing:** PPTX parser via Apache POI 5.3.0 (`XSLFSlide`) and PDF renderer via Apache PDFBox 3.0.3 (`PDFRenderer`).
- **Slide Cache & CDN Serving:** Image slide retrieval (`/api/presentations/{id}/slides/{number}`) with HTTP `Cache-Control: public, max-age=86400`.
- **Slide Navigation & Synchronization:** Synchronized current slide state across host and audience screens via REST & STOMP WS (`SLIDE_CHANGED`).

### Speaking Queue & Moderation
- **Hand Raising:** Audience queue assignment with FIFO ordering (`queue_position`).
- **Single Speaker Invariant:** Mutual exclusion in service layer ensuring maximum of 1 active speaker across parallel threads (`SpeakingRequestConcurrencyTest`).
- **Organizer Queue Moderation:** Approve, Reject, Reorder, and Force-End active speakers.
- **Speaker History Audit:** Detailed logging of speaker durations (`duration_seconds`) and termination reasons (`HOST_ENDED`, `SPEAKER_STOPPED`, `MEETING_CLOSED`).

### WebRTC Audio & Speech Processing
- **P2P Audio Streaming:** WebRTC `RTCPeerConnection` with STOMP signaling broker (`/topic/meetings/{code}/signal`).
- **STUN Server Configuration:** Built-in fallback STUN array (Google & Mozilla STUN servers).
- **Live Audio Visualizer:** Web Audio API (`AudioContext`, `AnalyserNode`) rendering HTML5 canvas frequency waveforms.
- **Live Speech-to-Text Subtitles:** Web Speech API integration producing live caption overlays on presentation slides.

---

## 7. COMPLETE PAGE INVENTORY

| Page Name | Route | Access Control | Main Purpose & Description |
| :--- | :--- | :--- | :--- |
| **Landing Page** | `/` | Public | Hero introduction, feature pitch, CTA for Organizer Login / Register and quick audience join code input. |
| **Login Page** | `/login` | Public | Form for organizers to log in with email and password. Redirects to `/dashboard` on success. |
| **Register Page** | `/register` | Public | Form for new organizers to create an account. |
| **Organizer Dashboard** | `/dashboard` | Organizer (JWT) | List created meetings, view status badges, create new meetings, launch meeting room workspace. |
| **Organizer Meeting Room** | `/room/:meetingCode` | Organizer (JWT) | Primary presenter workspace: slide viewer, presentation upload, QR modal toggle, live queue sidebar, audio visualizer, WebRTC listener, subtitle overlay. |
| **Audience Join Page** | `/join/:meetingCode` | Public (Anonymous) | Display meeting title/organizer name, nickname input form, duplicate name check, join submission. |
| **Audience Waiting Page** | `/waiting/:meetingCode` | Audience Token | Waiting lobby: display current meeting status, active speaker card, "Raise Hand" button, queue position tracker, leave button. |
| **Audience Speaker Page** | `/speak/:meetingCode` | Approved Speaker Token | Live microphone interface: "Start Speaking", live audio waveform bar, volume meter, "Stop Speaking" button. |
| **Meeting Closed Page** | `/closed/:meetingCode` | Public | Display meeting closed confirmation notice and button to return home. |
| **Not Found Page** | `*` | Public | 404 page for invalid or non-existent URLs. |

---

## 8. PAGE-BY-PAGE UI EXPLANATION

### 1. Landing Page (`/`)
- **Header:** Logo, "Features", "Login", "Register" buttons.
- **Hero Section:** High-contrast gradient background, headline "Turn Every Smartphone Into a Wireless Mic", subtitle, "Get Started as Host" primary button, and "Join as Audience" code input form.
- **Features Grid:** Cards showcasing WebRTC P2P Audio, Zero-Install QR Join, Presentation Sync, and Live Subtitles.

### 2. Login & Register Pages (`/login`, `/register`)
- Clean glassmorphism cards centered on dark gradient backdrop. Form inputs with email, password, and name validation.

### 3. Organizer Dashboard (`/dashboard`)
- **Header:** Navigation bar with user profile, "Create Meeting" button, Logout button.
- **Stats Row:** Active Meetings count, Total Participants count, Completed Sessions count.
- **Meeting Cards Grid:** Display meeting title, 6-character code badge, status tag (`CREATED`, `IN_PROGRESS`, `CLOSED`), created date, and "Open Workspace" / "Close Meeting" actions.

### 4. Organizer Meeting Room (`/room/:meetingCode`)
- **Center Canvas:** Large presentation viewer displaying current slide image, slide index counter, previous/next controls, thumbnail strip, and subtitle overlay bar.
- **Top Toolbar:** Meeting title, 6-character code badge, QR Code toggle button, Subtitles toggle switch, Close Meeting action.
- **Right Sidebar (MeetingControlSidebar):**
  - **Active Speaker Section:** Shows active speaker nickname, duration timer, volume meter audio waveform, and "End Speaker" button.
  - **Speaking Queue List:** Ordered list of waiting candidates with position badges, "Approve", "Reject", and drag/reorder controls.
  - **Audience List:** Total joined attendees with online connection indicators.

### 5. Audience Join & Waiting Pages (`/join/:code`, `/waiting/:code`)
- **Join Page:** Card with meeting title, input box for participant name, and "Join Session" primary button.
- **Waiting Page:** Status banner ("Meeting Live"), active speaker indicator card, prominent pulse-animated "Raise Hand" button showing position (e.g., "Queue Position: #2"), and "Cancel Request" / "Leave Meeting" options.

### 6. Audience Speaker Page (`/speak/:meetingCode`)
- **Live Mic Workspace:** Green live banner, large glowing "Microphone Active" button, live audio frequency level meter (`AudioWaveform.jsx`), microphone gain indicator, and "Stop Speaking" button.

---

## 9. UI/UX DESIGN SYSTEM

- **Color Palette:**
  - **Background:** Dark slate Navy / Zinc (`#0f172a`, `#18181b`).
  - **Primary Brand Color:** Electric Indigo / Violet (`#6366f1`, `#4f46e5`).
  - **Secondary Brand Color:** Emerald Green (`#10b981`) for live audio/active mic indicators.
  - **Accent / Destructive:** Crimson Red (`#ef4444`) for end speaker / close meeting actions.
  - **Text Colors:** Primary White (`#f8fafc`), Muted Gray (`#94a3b8`).
- **Typography:** Modern sans-serif stack (`Inter`, `system-ui`). Clear hierarchy with bold header text.
- **Card Aesthetics:** Glassmorphism styled containers with `backdrop-blur-md`, subtle border glow (`border-slate-800`), and rounded corners (`rounded-2xl`).
- **Visual Feedback & Animations:**
  - Dynamic pulse rings on active microphone and raise-hand buttons.
  - Interactive canvas audio waveform meter (`AudioWaveform.jsx`).
  - Confetti burst effect (`canvas-confetti`) when speaker request gets approved.
  - Responsive web design adapting cleanly to mobile screens (audience join) and desktop widescreen monitors (organizer room).

---

## 10. FRONTEND ARCHITECTURE

- **Framework:** React 19 (`react` 19.2.8, `react-dom` 19.2.8).
- **Build System:** Vite 8.2 (`vite`, `@vitejs/plugin-react`).
- **Routing:** React Router DOM 7.18 (`BrowserRouter`, `Routes`, `Route`).
- **State Management & Data Fetching:** TanStack React Query 5.101 (`QueryClientProvider`, `useQuery`, `useMutation`).
- **Real-Time Layer:** `@stomp/stompjs` + `sockjs-client` connecting to `/ws`.
- **CSS Engine:** TailwindCSS 4 (`@tailwindcss/vite`).
- **Testing & Quality:** Vitest 3.0 + React Testing Library + Oxlint linter.

### Directory Structure:
```
frontend/src/
 ├── api/                # Axios API services (auth, meetings, participants, presentations, speaking)
 ├── assets/             # Static graphics and icons
 ├── components/         # Reusable UI components (Navbar, Footer, QrCodeModal, AudioWaveform)
 │    └── presentation/  # PresentationViewer, MeetingControlSidebar, PresentationControls, SubtitlesOverlay
 ├── hooks/              # Custom React hooks (useStompWebSocket, useWebRtcSpeaker, useWebRtcListener, useSpeechRecognition)
 ├── pages/              # Route pages (Landing, Login, Register, Dashboard, MeetingRoom, AudienceJoin, Waiting, Speak, Closed)
 ├── App.jsx             # React Router and QueryClient setup
 ├── main.jsx            # React root DOM renderer
 └── setupTests.js       # Vitest setup & DOM mocks
```

---

## 11. BACKEND ARCHITECTURE

- **Framework & Runtime:** Java 21 LTS, Spring Boot 3.3.4.
- **Security:** Spring Security 6 with custom JWT filter (`JwtAuthenticationFilter`) and BCrypt password encoder.
- **ORM & Data Layer:** Spring Data JPA / Hibernate 6, Flyway migration (`V1__init_schema.sql`), PostgreSQL (Production) / H2 (Testing).
- **API Documentation:** SpringDoc OpenAPI 2.6.0 (`/swagger-ui.html`, `/v3/api-docs`).
- **Rate Limiting:** Bucket4j 8.14 in-memory IP rate limiter (`RateLimitInterceptor`).
- **Slide Converters:** Apache POI 5.3 (PPTX to PNG) & Apache PDFBox 3.0 (PDF to PNG).
- **Code Mapping & Boilerplate:** MapStruct 1.6 + Lombok.

### Request Lifecycle:
```
Client Request (HTTP / REST)
       ↓
RateLimitInterceptor (IP check)
       ↓
JwtAuthenticationFilter (Validates Authorization: Bearer <token>)
       ↓
Spring Controller (@RestController)
       ↓
Service Interface & Impl (@Service transactional logic & invariant checks)
       ↓
Spring Data JPA Repository (@Repository)
       ↓
Database (PostgreSQL / H2)
       ↓
Response DTO (Mapped via MapStruct / Builder)
```

---

## 12. API DOCUMENTATION

### Authentication APIs
- `POST /api/auth/register` — Register a new organizer account.
- `POST /api/auth/login` — Login as organizer and receive JWT.

### Meeting APIs
- `POST /api/meetings` — Create new meeting (Auth required).
- `GET /api/meetings` — List organizer's meetings (Auth required).
- `GET /api/meetings/{identifier}` — Get meeting details by ID or code (Auth required).
- `PATCH/POST /api/meetings/{identifier}/close` — Close meeting session (Auth required).
- `GET /api/meetings/{code}/qr` — Download PNG QR code image (Public).
- `GET /api/meetings/join/{code}` — Get join info before audience registration (Public).

### Participant APIs
- `POST /api/participants/join/{code}` — Audience member joins with nickname (Public).
- `GET /api/participants/me` — Get participant session info via `X-Session-Token` header (Public).
- `POST /api/participants/leave` — Leave meeting session (Public).
- `GET /api/meetings/{meetingId}/participants` — List meeting attendees (Organizer Auth).

### Speaking Request APIs
- `POST /api/speaking-requests/raise-hand` — Raise hand to enter speaking queue (Participant Token).
- `GET /api/speaking-requests/me` — Get current hand-raise status & queue position (Participant Token).
- `POST /api/speaking-requests/start` — Signal live mic start after approval (Participant Token).
- `POST /api/speaking-requests/stop` — Stop speaking and release mic (Participant Token).
- `GET /api/meetings/{meetingId}/speaking-requests` — List queue (Organizer Auth).
- `PATCH /api/meetings/{meetingId}/speaking-requests/{requestId}/approve` — Approve speaker (Organizer Auth).
- `PATCH /api/meetings/{meetingId}/speaking-requests/{requestId}/reject` — Reject request (Organizer Auth).
- `PATCH /api/meetings/{meetingId}/speaking-requests/{requestId}/end` — Force-end speaker (Organizer Auth).
- `POST /api/meetings/{meetingId}/speaking-requests/reorder` — Reorder waiting queue (Organizer Auth).

### Presentation APIs
- `POST /api/meetings/{meetingId}/presentations` — Upload PPTX/PDF presentation (Organizer Auth).
- `GET /api/meetings/{meetingId}/presentations` — List uploaded presentations.
- `POST /api/presentations/{id}/start` — Start presenting selected file (Organizer Auth).
- `PUT /api/presentations/{id}/slide` — Update active slide index (Organizer Auth).
- `GET /api/presentations/{id}/slides/{slideNumber}` — Get PNG image byte stream for specific slide.

---

## 13. DATABASE

### Database Technology
PostgreSQL 16 (Production) / H2 In-Memory (Test execution), managed via Flyway migrations (`V1__init_schema.sql`).

### Entity Relationship Model

```
 ┌────────────────┐         1:N         ┌────────────────┐
 │   organizers   │ ───────────────────>│    meetings    │
 └────────────────┘                     └───────┬────────┘
                                                │ 1:N
                        ┌───────────────────────┼───────────────────────┐
                        │ 1:N                   │ 1:N                   │ 1:N
               ┌────────▼───────┐      ┌────────▼───────┐      ┌────────▼───────┐
               │  participants  │      │ presentations  │      │speaking_requests│
               └────────┬───────┘      └────────┬───────┘      └────────────────┘
                        │ 1:N                   │ 1:1
               ┌────────▼───────┐      ┌────────▼──────────────┐
               │ speaker_history│      │ presentation_sessions │
               └────────────────┘      └───────────────────────┘
```

### Key Tables & Constraints
1. `organizers`: `id` (PK), `email` (UNIQUE), `password_hash`, `created_at`.
2. `meetings`: `id` (PK), `meeting_code` (UNIQUE 6-char), `qr_token` (UNIQUE 80-char), `organizer_id` (FK), `status` (`CREATED`, `IN_PROGRESS`, `CLOSED`).
3. `participants`: `id` (PK), `meeting_id` (FK), `name`, `session_token` (UNIQUE UUID), `status` (`WAITING`, `APPROVED`, `SPEAKING`, `REJECTED`, `FINISHED`), UNIQUE constraint on `(meeting_id, name)`.
4. `speaking_requests`: `id` (PK), `meeting_id` (FK), `participant_id` (FK), `status`, `queue_position`.
5. `speaker_history`: `id` (PK), `meeting_id` (FK), `participant_id` (FK), `duration_seconds`, `end_reason`.
6. `presentations`: `id` (PK), `meeting_id` (FK), `storage_path`, `total_slides`, `current_slide`.
7. `presentation_sessions`: `id` (PK), `meeting_id` (FK, UNIQUE), `presentation_id` (FK), `current_slide`, `is_presenting`.

---

## 14. AUTHENTICATION & AUTHORIZATION

- **Organizer Security:** JWT Tokens signed with HMAC-SHA256 (`app.jwt.secret`). Minimum key length requirement of 256 bits (32 bytes). Validated on each request in `JwtAuthenticationFilter`. Passwords encoded via `BCryptPasswordEncoder`.
- **Audience Security:** Anonymous token authentication. Server generates a cryptographically random UUID `sessionToken` upon joining. Audience requests include `X-Session-Token` or `X-Participant-Token` header.
- **Service Ownership Enforcement:** Presenter actions verify `meeting.getOrganizer().getId().equals(authenticatedOrganizerId)`. Mismatches trigger `ResourceNotFoundException` (404) to prevent IDOR scanning.
- **Closed Meeting Rules:** Closed meetings immediately reject joins, hand raises, and WebRTC signal relays.

---

## 15. SECURITY

### Implemented Security Features
- **Stateless JWT Authentication:** Enforces HS256 algorithm and 256-bit key length.
- **BCrypt Password Hashing:** Prevents plain-text password leakage.
- **IDOR Protection:** Strict service layer ownership checks on all organizer endpoints.
- **WebRTC Signaling Validation:** Whitelists allowed signal types (`OFFER`, `ANSWER`, `ICE_CANDIDATE`) and roles (`ORGANIZER`, `SPEAKER`); drops unauthorized signals.
- **Path Traversal Protection:** Filename sanitization (`Paths.get(...).getFileName()`) and target directory containment verification on uploaded files.
- **File Upload Safeguards:** Maximum 50MB file size limit and strict extension checks (`.pptx`, `.pdf`).
- **IP Rate Limiting:** Bucket4j sliding window rate limiter (`RateLimitInterceptor`): 15 req/min on Auth, 30 req/min on Join, 40 req/min on Hand-Raise.
- **HTTP Security Headers:** `X-Frame-Options: SAMEORIGIN`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`.
- **CORS Restrictions:** Whitelisted cross-origin domains via `CORS_ALLOWED_ORIGINS`.

---

## 16. COMPLETE BUSINESS WORKFLOW

```
   [Organizer Uploads Presentation] ──> Server splits PPTX/PDF into PNG slides
                 │
   [Organizer Projects Screen] ─────> Displays Slide 1 + QR Code
                 │
   [Audience Scans QR Code] ────────> Opens /join/:code, enters Nickname
                 │
   [Audience Taps "Raise Hand"] ───> Enters queue; STOMP broadcasts position
                 │
   [Organizer Clicks "Approve"] ───> Single-speaker check passes; STOMP notifies audience
                 │
   [Audience Taps "Start Mic"] ────> WebRTC SDP Offer sent over STOMP WS
                 │
   [Organizer Browser Accepts] ───> WebRTC SDP Answer returned; P2P audio stream live
                 │
   [Audio Plays to Venue + Subtitles overlay on presentation slides]
                 │
   [Organizer Clicks "End Speaker"] -> WebRTC stream closes; history logged; queue advances
```

---

## 17. TECHNOLOGY STACK

### Frontend
- **Framework:** React 19.2.8
- **Build Tool:** Vite 8.2.0
- **Routing:** React Router DOM 7.18.2
- **State & Data Fetching:** TanStack React Query 5.101.4
- **Real-Time Client:** `@stomp/stompjs` 7.3.0 + `sockjs-client` 1.6.1
- **Styling:** TailwindCSS 4.3.3
- **Icons:** Lucide React 1.28.0
- **QR Code Rendering:** `qrcode.react` 4.2.0
- **Testing:** Vitest 3.0.7 + `@testing-library/react` 16.2.0 + Oxlint 1.75.0

### Backend
- **Language & Runtime:** Java 21 LTS
- **Framework:** Spring Boot 3.3.4 (Web, Security, Data JPA, WebSocket)
- **Database Driver:** PostgreSQL Driver / H2
- **DB Migration:** Flyway 10.x
- **JWT Library:** JJWT 0.12.6
- **QR Code Engine:** ZXing 3.5.3
- **Rate Limiter:** Bucket4j 8.14.0
- **Presentation Processing:** Apache POI 5.3.0 (PPTX), Apache PDFBox 3.0.3 (PDF)
- **API Specification:** SpringDoc OpenAPI 2.6.0
- **Code Mapping:** MapStruct 1.6.2 + Lombok

---

## 18. PROJECT ARCHITECTURE

```
                                  [AUDIENCE BROWSER]
                                          │
                    ┌─────────────────────┴─────────────────────┐
                    │ HTTP REST (Join / Raise Hand)             │ WebRTC Peer-to-Peer
                    │ STOMP WebSocket (Events & Signaling)      │ Audio Stream
                    ▼                                           ▼
          ┌───────────────────┐                       ┌───────────────────┐
          │ Spring Boot Backend│                       │ Organizer Browser │
          │   (API & Broker)  │                       │  (Venue Speakers) │
          └─────────┬─────────┘                       └───────────────────┘
                    │
                    ▼
          ┌───────────────────┐
          │ PostgreSQL DB     │
          └───────────────────┘
```

---

## 19. DATA FLOW

1. **Join Flow:** Audience browser sends `POST /api/participants/join/{code}` → Backend validates code & name uniqueness → Creates `Participant` record with UUID `sessionToken` → Returns DTO → Frontend stores token in `localStorage`.
2. **Raise Hand Flow:** Audience sends `POST /api/speaking-requests/raise-hand` with token → Backend assigns `queue_position` → Saves `SpeakingRequest` → Triggers `MeetingEventPublisher` → STOMP sends `RAISE_HAND` event to `/topic/meetings/{code}` → Host UI updates queue list instantly.
3. **Audio Flow:** Host approves speaker → Approved speaker browser initiates `RTCPeerConnection` → Sends SDP OFFER over `/app/meetings/{code}/signal` → Host backend relays to `/topic/meetings/{code}/signal` → Host browser processes OFFER, creates ANSWER, returns via signal destination → P2P audio track connects directly browser-to-browser.

---

## 20. ERROR HANDLING

- **Global Exception Handler:** Spring `@RestControllerAdvice` mapping domain exceptions to structured `ErrorResponse` objects with HTTP status codes:
  - `ResourceNotFoundException` → `404 Not Found`
  - `BusinessRuleViolationException` → `400 Bad Request`
  - `DuplicateParticipantException` → `409 Conflict`
  - `InvalidCredentialsException` → `401 Unauthorized`
  - `RateLimitExceededException` → `429 Too Many Requests`
- **Frontend Error Boundaries:** React Query error states display user-friendly error banners with auto-retry options.

---

## 21. LOADING & EMPTY STATES

- **Presentation Viewer:** Displays sleek loading skeleton spinner while slides are converted or fetched; shows clean fallback card if no presentation is uploaded.
- **Speaking Queue Sidebar:** Renders empty state graphics ("No waiting speakers in queue") when queue is clear.
- **Audience Waiting Lobby:** Shows pulse animations during pending queue placement and clean state changes upon approval.

---

## 22. ADMIN / MANAGEMENT FEATURES

(The Organizer acts as the session Administrator):
- Close active meetings permanently.
- Drag-and-drop or manual button reordering of audience speaking queue.
- Emergency force-end active speaker mic stream.
- Delete or replace uploaded presentation files.

---

## 23. SEARCH / FILTER / SORT

- **Organizer Dashboard:** Client-side and backend filtering of meetings by status (`CREATED`, `IN_PROGRESS`, `CLOSED`).
- **Speaking Queue Sorting:** Ordered strictly by `queue_position ASC` with host reorder capability (`ReorderQueueRequest`).

---

## 24. REPORTS / ANALYTICS

- **Speaker History Audit Log:** Tracks every speaking session in `speaker_history` table: participant name, start timestamp, end timestamp, total duration in seconds (`duration_seconds`), and termination reason (`SPEAKER_STOPPED`, `HOST_ENDED`, `MEETING_CLOSED`).

---

## 25. FILES / MEDIA

- **Upload Formats:** Microsoft PowerPoint (`.pptx`) and Portable Document Format (`.pdf`).
- **Storage Strategy:** Files saved to `uploads/presentations/{uuid}/original_file`.
- **Slide Rendering Engine:** Apache POI extracts `XSLFSlide` shapes, drawing them to Java `BufferedImage` graphics; PDFBox renders pages via `PDFRenderer.renderImageWithDPI(page, 150)`. Output saved as PNG files and served via stream endpoint.

---

## 26. NOTIFICATIONS

- **Real-Time Event Notifications:** Delivered via STOMP WebSocket topics (`/topic/meetings/{code}`):
  - `PARTICIPANT_JOINED`, `PARTICIPANT_LEFT`
  - `RAISE_HAND`, `SPEAKER_APPROVED`, `SPEAKER_REJECTED`, `SPEAKER_STARTED`, `SPEAKER_ENDED`
  - `SLIDE_CHANGED`, `MEETING_CLOSED`

---

## 27. TESTING

- **Backend Test Coverage:** 13 test suites, 65 automated tests passing with 0 failures (`backend/src/test/java/com/voicebridge`). Includes `SpeakingRequestConcurrencyTest` verifying single-active-speaker invariant under multi-threaded load (10 parallel threads).
- **Frontend Test Coverage:** 4 test suites, 8 Vitest + React Testing Library tests passing (`frontend/src/**/*.test.jsx`).
- **Code Quality:** Oxlint verified across 37 JS/JSX files with 0 errors.

---

## 28. DEPLOYMENT

- **Containerization:** Multi-stage Dockerfiles for backend (OpenJDK 21 Maven build) and frontend (Vite static build served via Nginx).
- **Orchestration:** `docker-compose.yml` orchestrating PostgreSQL 16 Alpine, Spring Boot backend, and Nginx frontend.

---

## 29. PROJECT SETUP

### Prerequisites
- JDK 21 LTS
- Node.js 18+ & npm
- Maven 3.9+
- Docker & Docker Compose

### Step-by-Step Commands

```bash
# 1. Clone repository & enter project directory
cd VoiceBridge

# 2. Launch Docker services (PostgreSQL, Backend, Frontend)
docker compose up -d --build

# 3. Alternative: Run Backend locally
cd backend
mvn clean spring-boot:run

# 4. Alternative: Run Frontend locally
cd frontend
npm install
npm run dev
```

---

## 30. USER GUIDE

### Step 1: Host Sign Up & Login
Go to `http://localhost:5173`, click **Register**, fill in details, and log in.

### Step 2: Create a Meeting & Upload Presentation
In the Dashboard, click **Create Meeting**, enter a title, and click **Open Workspace**. Click **Upload Presentation** and choose a `.pptx` or `.pdf` file.

### Step 3: Project Slides & Share QR Code
Click the **QR Code** button on the toolbar to project the join QR code on the venue screen.

### Step 4: Audience Joins
Audience members point their smartphone cameras at the screen, tap the link, enter their nickname, and tap **Join Session**.

### Step 5: Audience Raises Hand & Host Approves
When an attendee has a question, they tap **Raise Hand**. The host sees their name in the queue sidebar and clicks **Approve**.

### Step 6: Live WebRTC Audio Stream
The approved attendee taps **Start Mic** and speaks into their smartphone. Their voice plays out of the host's computer into the venue speakers, accompanied by live presentation subtitles.

---

## 31. REAL-WORLD EXAMPLES

### Scenario 1: University Lecture Q&A
- **Problem:** 300 students in an auditorium; microphone cannot reach back rows.
- **Action:** Professor displays QR code on projector. Student in back row scans code, taps "Raise Hand", gets approved, and asks question through their phone mic.
- **Result:** Whole class hears question clearly through room speakers without passing a microphone.

### Scenario 2: Corporate Town Hall
- **Problem:** Hybrid meeting with executive presentation where Q&A gets chaotic.
- **Action:** Executive uploads PPTX, projects slides. Employees join via QR, enter queue. Executive approves one question at a time.
- **Result:** Organized single-speaker Q&A with real-time speech subtitles displayed on the presentation slides.

### Scenario 3: Conference Panel Discussion
- **Problem:** Short 10-minute Q&A window; passing mic wastes 4 minutes.
- **Action:** Attendees raise hands digitally prior to Q&A start. Moderator reorders top questions and approves them sequentially.
- **Result:** Saves 4 minutes, allowing 3 additional questions to be answered.

---

## 32. VALUE TO USERS

- **Saves Time:** Eliminates mic-passing dead air, saving 30–60 seconds per question.
- **Reduces Hardware Cost:** Saves thousands of dollars in wireless mic rental fees.
- **Improves Hygiene:** Attendees speak into their personal smartphones.
- **Improves Order:** Host retains total control; single-speaker invariant prevents crosstalk.
- **Enhances Accessibility:** Live speech-to-text subtitles assist hard-of-hearing attendees.

---

## 33. WHAT MAKES THIS MORE THAN CRUD

1. **Peer-to-Peer WebRTC Audio Integration:** Browser-native real-time low-latency audio transmission without central audio processing servers.
2. **STOMP WebSocket Signaling & Event Bus:** Dynamic event delivery linking host controls and audience client states in real-time.
3. **On-the-Fly Document Processing:** Server-side rendering of PPTX/PDF slide decks into high-definition web assets using Java POI & PDFBox.
4. **Multi-Threaded Concurrency Guarantees:** Synchronized queue state and single-speaker invariant execution tested under heavy parallel stress.

---

## 34. SCALABILITY

- **Current Implementation:** Single-instance Spring Boot app with in-memory WebSocket broker and local disk storage for presentation slides.
- **Scalability Improvements:**
  - **Distributed WebSockets:** Integrate Redis Pub/Sub or RabbitMQ Relay for multi-node WebSocket scalability.
  - **Cloud Object Storage:** Transition presentation slide storage from local filesystem to AWS S3 / Google Cloud Storage.
  - **TURN Server Infrastructure:** Deploy Coturn servers for WebRTC traversal across complex corporate NATs and strict firewalls.

---

## 35. LIMITATIONS

- **Current Limitations:**
  - WebRTC requires HTTPS in production for browser microphone permission (`getUserMedia`).
  - Slide conversion performance depends on server CPU for complex PPTX files with heavy graphics.
  - Web Speech API subtitle accuracy depends on browser engine support (Chrome/Edge recommended).

---

## 36. FUTURE FEATURES

- **HIGH VALUE:** Speech-to-Text translation into multiple audience languages in real-time.
- **MEDIUM VALUE:** Audience upvoting on queued questions before host approval.
- **OPTIONAL:** Session audio recording and automated AI meeting summary generation.

---

## 37. INTERVIEW EXPLANATION

### 30-Second Summary (Elevator Pitch)
"VoiceBridge is a full-stack real-time platform that turns audience smartphones into wireless microphones for presentations and conferences. Attendees scan a projected QR code to join without an account, raise their hands, and stream crystal-clear audio directly to the venue speakers over WebRTC when approved by the host."

### 1-Minute Summary (Technical Overview)
"VoiceBridge combines a React 19 frontend with a Spring Boot 3 backend and PostgreSQL database. It features server-side PPTX and PDF slide processing, turning presentations into synchronized web slides. Audience members join anonymously via QR code tokens and enter a real-time STOMP WebSocket queue. When the host approves a speaker, the system orchestrates a WebRTC peer-to-peer audio connection between the attendee's phone mic and the host's presentation computer, enforcing a strict single-active-speaker rule backed by concurrency tests."

### 3-Minute Summary (Deep-Dive Engineering)
"VoiceBridge solves the physical delay, hygiene, and hardware costs of passing microphones in large auditoriums. On the backend, Spring Boot manages meeting lifecycles, JWT authentication for hosts, anonymous token security for attendees, and slide processing using Apache POI and PDFBox. STOMP WebSockets provide real-time state synchronization for queue movements, slide changes, and WebRTC SDP/ICE signaling. Audio is streamed strictly peer-to-peer over WebRTC, keeping backend overhead minimal. Security is enforced through IDOR verification, Bucket4j rate limiting, and path traversal protection on uploads. The system is verified with 65 backend unit/integration/concurrency tests and 8 Vitest frontend tests."

---

## 38. RESUME DESCRIPTION

### One-Line Resume Description
Built a real-time QR-based smart audience microphone & presentation workspace using React 19, WebRTC, Spring Boot 3, and WebSockets.

### Two-Line Resume Description
Engineered VoiceBridge, a full-stack interactive presentation platform enabling zero-install audience participation via WebRTC peer-to-peer phone microphone streaming, real-time STOMP WebSocket queue moderation, and automated PPTX/PDF slide processing.

### Bullet Points for Resume
- **Developed real-time WebRTC audio streaming** system turning audience smartphones into host-moderated wireless microphones, backed by STOMP WebSocket signaling and Web Speech API live subtitling.
- **Implemented server-side document rendering engine** using Spring Boot 3, Apache POI, and PDFBox to convert uploaded PPTX/PDF presentations into cached PNG slide streams.
- **Hardened application security and concurrency** with JWT auth, anonymous UUID session tokens, Bucket4j rate limiting, and multi-threaded synchronization enforcing single-active-speaker invariants.
- **Achieved 100% test pass rate** across 65 backend JUnit/Spring Boot integration tests and Vitest frontend component suites, orchestrated with Docker Compose and PostgreSQL.

---

## 39. PROJECT TITLE SUGGESTIONS

1. **VoiceBridge Pro** — Highlights professional venue voice bridging. *(Recommended)*
2. **SmartMic Live** — Emphasizes smart smartphone microphone capability. *(Recommended)*
3. **AudienceVoice Hub** — Focuses on audience voice aggregation. *(Recommended)*
4. **QR-Mic Presentation Suite**
5. **AirMic Live**
6. **EchoBridge Realtime**
7. **StageVoice Pro**
8. **PresenterConnect**
9. **OmniMic WebRTC**
10. **VoxBridge Studio**

---

## 40. FINAL PROJECT SUMMARY

- **PROJECT:** VoiceBridge
- **WHAT IT DOES:** Turns audience smartphones into wireless microphones and synchronizes presentation slides via WebRTC and WebSockets.
- **MAIN USERS:** Event Organizers (Hosts) and Audience Attendees.
- **MAIN PROBLEM:** Slow, unhygienic, expensive physical microphone passing during Q&A sessions.
- **CORE FEATURES:** QR Join, WebRTC P2P Audio Streaming, Queue Moderation, PPTX/PDF Processing, Live Speech Subtitles.
- **FRONTEND:** React 19, Vite 8, TailwindCSS 4, TanStack Query, SockJS/STOMP.
- **BACKEND:** Java 21, Spring Boot 3.3, Spring Security (JWT), Apache POI/PDFBox, Bucket4j.
- **DATABASE:** PostgreSQL 16 / H2, Flyway migrations.
- **SECURITY:** JWT, Anonymous UUID Session Tokens, IDOR checks, Bucket4j Rate Limiting, Path Traversal Sanitization.
- **KEY TECHNOLOGIES:** WebRTC, STOMP WebSockets, Java 21, Spring Boot, React 19, Docker.
- **MAIN WORKFLOW:** Host Projects QR → Audience Scans & Joins → Audience Raises Hand → Host Approves → WebRTC Mic Streams Audio & Subtitles.
- **MAIN VALUE:** Instant zero-hardware Q&A, 0 seconds wasted mic passing, 100% hygienic personal device usage.
- **WHAT MAKES IT INTERESTING:** P2P WebRTC audio integration with WebSocket signaling, real-time slide conversion, and multi-threaded concurrency safety.
- **BEST RESUME TITLE:** VoiceBridge — QR-Based WebRTC Audience Microphone & Live Presentation System
- **BEST ONE-LINE DESCRIPTION:** A QR-based smart audience microphone system and presentation workspace enabling instant WebRTC voice interaction without hardware passed around.
