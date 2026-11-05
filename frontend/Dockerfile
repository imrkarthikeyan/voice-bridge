# Build stage
FROM node:20-alpine AS build
WORKDIR /app
ARG VITE_AI_WS_URL
ARG VITE_TURN_URL
ARG VITE_TURN_USERNAME
ARG VITE_TURN_PASSWORD
ENV VITE_AI_WS_URL=$VITE_AI_WS_URL \
    VITE_TURN_URL=$VITE_TURN_URL \
    VITE_TURN_USERNAME=$VITE_TURN_USERNAME \
    VITE_TURN_PASSWORD=$VITE_TURN_PASSWORD
COPY package*.json ./
RUN npm install --no-audit --no-fund
COPY . .
RUN npm run build

# Production Nginx stage
FROM nginx:1.25-alpine
RUN apk add --no-cache openssl
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY 90-generate-cert.sh /docker-entrypoint.d/90-generate-cert.sh
RUN sed -i 's/\r$//' /docker-entrypoint.d/90-generate-cert.sh \
    && chmod +x /docker-entrypoint.d/90-generate-cert.sh

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
