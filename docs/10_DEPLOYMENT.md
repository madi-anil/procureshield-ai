# 10. DEPLOYMENT & DEVOPS SPECIFICATION

## 1. Cloud Topology & Subdomains
- **Frontend:** React 18 + Vite + TypeScript deployed to **Cloudflare Pages** (or Vercel).
- **Backend:** Java 21 + Spring Boot 3.x containerized and hosted on **Azure Container Apps** or **Render** (not Cloudflare Workers, which run JS/Wasm isolates rather than JVMs).
- **Database & Storage:** Managed **PostgreSQL 16** (with Flyway + `JSONB`) and **Cloudflare R2** (S3-compatible bucket for SLA PDFs, logos, and certificates).
- **Environments & Subdomains:** `dev` / `api.dev` (`develop`), `stage` / `api.stage` (`stage`), and root / `api` (`main`).

## 2. Dockerfiles (`backend/Dockerfile` & `frontend/Dockerfile`)
```dockerfile
# backend/Dockerfile (Multi-stage Java 21 + Maven)
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /app/target/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

```

```dockerfile
# frontend/Dockerfile (Multi-stage Node 22 + Nginx)
FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

```

## 3. Local Orchestration (`docker/docker-compose.yml`)

```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: procureshield_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: localpassword123
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d procureshield_db"]
      interval: 5s
      timeout: 5s
      retries: 5

  backend:
    build:
      context: ../backend
      dockerfile: Dockerfile
    depends_on:
      db:
        condition: service_healthy
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/procureshield_db
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: localpassword123
      JWT_SECRET: ${JWT_SECRET}
      CLOUDFLARE_R2_BUCKET: ${CLOUDFLARE_R2_BUCKET:-procureshield-docs}
      CLOUDFLARE_R2_ACCOUNT_ID: ${CLOUDFLARE_R2_ACCOUNT_ID}
      CLOUDFLARE_R2_ACCESS_KEY: ${CLOUDFLARE_R2_ACCESS_KEY}
      CLOUDFLARE_R2_SECRET_KEY: ${CLOUDFLARE_R2_SECRET_KEY}
      GEMINI_API_KEY: ${GEMINI_API_KEY}
      ADMIN_EMAIL: ${ADMIN_EMAIL}
      ADMIN_PASSWORD: ${ADMIN_PASSWORD}
      AI_MODEL: ${AI_MODEL}

  frontend:
    build:
      context: ../frontend
      dockerfile: Dockerfile
      args:
        VITE_API_BASE_URL: http://localhost:8080/api/v1
    depends_on:
      - backend
    ports:
      - "3000:80"

```

## 4. GitHub Actions CI/CD (`.github/workflows/deploy.yml`)

```yaml
name: ProcureShield AI CI/CD

on:
  push:
    branches: [develop, stage, main]
  pull_request:
    branches: [develop, stage, main]

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: "21"
          distribution: "temurin"
          cache: maven
      - working-directory: ./backend
        run: mvn clean test jacoco:report jacoco:check

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: "22"
          cache: "npm"
          cache-dependency-path: ./frontend/package-lock.json
      - working-directory: ./frontend
        run: |
          npm ci
          npm run build

  deploy:
    needs: [backend, frontend]
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKER_USERNAME }}
          password: ${{ secrets.DOCKER_PASSWORD }}
      - uses: docker/build-push-action@v5
        with:
          context: ./backend
          push: true
          tags: ${{ secrets.DOCKER_USERNAME }}/procureshield-backend:latest
      - uses: cloudflare/pages-action@v1
        with:
          apiToken: ${{ secrets.CLOUDFLARE_API_TOKEN }}
          accountId: ${{ secrets.CLOUDFLARE_ACCOUNT_ID }}
          projectName: procureshield
          directory: ./frontend/dist
          gitHubToken: ${{ secrets.GITHUB_TOKEN }}
      - name: Trigger backend deploy (Render deploy hook)
        run: curl --fail -X POST "${{ secrets.RENDER_DEPLOY_HOOK_URL }}"
      - name: Post-deploy health check
        run: |
          curl --fail --silent --show-error --retry 12 --retry-delay 15 --retry-connrefused \
            https://api.procureshield.com/actuator/health

```


**JaCoCo enforcement (`backend/pom.xml`).** `jacoco:check` enforces nothing without a rule, so add:

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <executions>
    <execution><goals><goal>prepare-agent</goal></goals></execution>
    <execution>
      <id>report</id><phase>test</phase><goals><goal>report</goal></goals>
    </execution>
    <execution>
      <id>check</id><phase>verify</phase><goals><goal>check</goal></goals>
      <configuration>
        <includes><include>com/procureshield/service/**</include></includes>
        <rules>
          <rule>
            <element>BUNDLE</element>
            <limits>
              <limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.80</minimum></limit>
            </limits>
          </rule>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

The CI step should be `mvn clean verify`. The backend is deployed to **Render** through a deploy hook (`RENDER_DEPLOY_HOOK_URL`); if you host on Azure Container Apps instead, replace that step with `az containerapp update --image ...`. The health check calls `/actuator/health` (TASK-03).

## 5. Required Secrets & Env Vars

* **Database & Auth:** `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`.
* **Cloudflare R2 & AI:** `CLOUDFLARE_R2_ACCOUNT_ID`, `CLOUDFLARE_R2_ACCESS_KEY`, `CLOUDFLARE_R2_SECRET_KEY`, `CLOUDFLARE_R2_BUCKET`, `GEMINI_API_KEY`, `AI_MODEL`.
* **CI/CD & Hosting:** `DOCKER_USERNAME`, `DOCKER_PASSWORD`, `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, `VITE_API_BASE_URL`, `RENDER_DEPLOY_HOOK_URL`.