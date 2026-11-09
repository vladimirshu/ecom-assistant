# Build stage: compile the Vite SPA into static assets
FROM node:20-alpine AS build
WORKDIR /app

# Install dependencies first for better layer caching
COPY package.json package-lock.json ./
RUN npm ci

# Copy the rest of the source
COPY . .

RUN npm run build

# Serve stage: nginx serves the static SPA and proxies /api to the backend
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
