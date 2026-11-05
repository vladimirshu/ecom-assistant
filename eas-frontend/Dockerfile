# Build stage: compile the Vite SPA into static assets
FROM node:20-alpine AS build
WORKDIR /app

# Install dependencies first for better layer caching
COPY package.json package-lock.json ./
RUN npm ci

# Copy the rest of the source
COPY . .

# Deployment schedule configuration. The production build validates and inlines
# these values (see vite.config.ts), so they must be available at build time.
ARG VITE_OPEN_TZ=Europe/Berlin
ARG VITE_OPEN_DAYS=mon,tue,wed,thu,fri
ARG VITE_OPEN_FROM=08:00
ARG VITE_OPEN_TO=22:00
ENV VITE_OPEN_TZ=$VITE_OPEN_TZ \
    VITE_OPEN_DAYS=$VITE_OPEN_DAYS \
    VITE_OPEN_FROM=$VITE_OPEN_FROM \
    VITE_OPEN_TO=$VITE_OPEN_TO

RUN npm run build

# Serve stage: nginx serves the static SPA and proxies /api to the backend
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
