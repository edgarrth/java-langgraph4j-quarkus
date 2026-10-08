FROM axiz-frontend-base AS build
COPY . .
RUN npm run build
FROM nginxinc/nginx-unprivileged:alpine
COPY --from=build /opt/frontend/dist/frontend/browser/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
