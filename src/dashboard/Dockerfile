# ---- build stage ----
  FROM node:20-alpine AS build
  WORKDIR /app
  
  COPY package*.json ./
  RUN npm ci
  
  COPY . .
  RUN npm run build
  
  # ---- run stage ----
  FROM nginx:1.27-alpine
  
  # SPA routing (optional but recommended for Vite + react-router)
  RUN rm -f /etc/nginx/conf.d/default.conf
  COPY nginx.conf /etc/nginx/conf.d/default.conf
  
  COPY --from=build /app/dist /usr/share/nginx/html
  
  EXPOSE 80
  CMD ["nginx", "-g", "daemon off;"]
  