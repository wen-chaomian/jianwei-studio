FROM node:24-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build/backend
COPY backend/pom.xml ./
RUN mvn -q dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /build/frontend/dist ./src/main/resources/static
RUN mvn -q verify
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system studio && useradd --system --gid studio studio
COPY --from=backend /build/backend/target/studio-0.1.0.jar app.jar
USER studio
ENV TZ=Asia/Shanghai
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
