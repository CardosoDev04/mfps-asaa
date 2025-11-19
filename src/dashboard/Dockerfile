# ---- Build stage ----
  FROM node:22-alpine AS build
  WORKDIR /app
  
  # Install dependencies first (caching)
  COPY package*.json ./
  RUN npm ci
  
  # Copy source and build
  COPY . .
  RUN npm run build
  
  # ---- Run stage ----
  FROM nginx:1.27-alpine
  # Copy build output to nginx's web root
  COPY --from=build /app/dist /usr/share/nginx/html
  
  # Optional: custom nginx config (e.g., for SPA routing)
  # COPY nginx.conf /etc/nginx/conf.d/default.conf
  
  EXPOSE 5173
  CMD ["nginx", "-g", "daemon off;"]
  