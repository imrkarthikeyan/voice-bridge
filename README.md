# VoiceBridge

VoiceBridge is a QR-based smart audience microphone and presentation workspace for seminars, placement talks, classrooms, meetings, and conferences.

The target product lets an organizer create a meeting, show a QR code beside slides, manage an audience speaking queue, approve exactly one live speaker at a time, and receive the approved participant's browser microphone over WebRTC.

## Phase Status

Current requested phase: **Phase 1 - repository setup**.

This repository already contained backend and frontend code from later phases. Phase 1 work keeps that code intact, but the verified scope is the project foundation: monorepo layout, backend and frontend build setup, PostgreSQL/Docker configuration, environment variables, and documentation skeleton.

## Monorepo Structure

```text
VoiceBridge/
??? backend/
??? frontend/
??? docs/
??? .env.example
??? docker-compose.yml
??? AGENTS.md
??? README.md
```

## Technology Foundation

Backend:
- Java 21
- Spring Boot 3
- Maven
- Spring Web, Security, Data JPA, WebSocket, Validation, Actuator
- PostgreSQL
- Flyway
- Lombok and MapStruct
- SpringDoc OpenAPI

Frontend:
- React 19
- Vite
- Tailwind CSS
- React Router
- Axios
- TanStack React Query
- STOMP/SockJS/WebRTC libraries

Infrastructure:
- Docker Compose
- PostgreSQL 16
- Backend Dockerfile
- Frontend Dockerfile with Nginx

## Environment

Copy `.env.example` to `.env` for local Docker usage and replace every secret value before production deployment.

Important variables:
- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `JWT_SECRET`
- `CORS_ALLOWED_ORIGINS`
- `QR_BASE_URL`
- `VITE_API_URL`
- `VITE_WS_URL`

## Local Backend

```bash
cd backend
mvn clean test
```

## Local Frontend

```bash
cd frontend
npm install
npm run lint
npm run build
```

For same-network microphone testing, start Vite on the LAN interface:

```bash
npm run dev -- --host 0.0.0.0
```

Open `https://192.168.31.9:5173` on the phone, accept the development certificate warning,
and allow microphone access. The phone and organizer computer must be on the same Wi-Fi
network. Use HTTPS; mobile browsers block microphone access on plain HTTP LAN addresses.

## Docker

```bash
docker compose up --build
```

Services:
- PostgreSQL: `localhost:5432`
- Backend: `localhost:8080`
- Frontend: `localhost`

## Documentation

- [Architecture](docs/architecture.md)
- [WebRTC](docs/webrtc.md)
- [WebSocket](docs/websocket.md)
- [Database](docs/database.md)
- [Deployment](docs/deployment.md)
- [API](docs/api.md)

## Phase 1 Definition

Phase 1 is complete only when the backend compiles/tests, the frontend lint/build commands run, Docker/PostgreSQL configuration is present, and the repository rules/docs are in place. Product features such as authentication, meetings, presentation handling, and WebRTC are intentionally outside the Phase 1 scope for this pass.
