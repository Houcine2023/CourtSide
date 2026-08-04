# ---------- build stage ----------
# The JDK and Maven exist only here; none of it reaches the final image.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy the build descriptor FIRST and resolve dependencies, so this layer is cached
# and re-downloading only happens when pom.xml actually changes. Copying sources
# first would invalidate the cache on every single code edit.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
# Tests are skipped HERE on purpose: CI already ran them before this job starts.
# Running them again would double the pipeline time to learn nothing new.
RUN ./mvnw -B -ntp clean package -DskipTests

# ---------- runtime stage ----------
# JRE, not JDK: no compiler, no build tools — a smaller image and a smaller attack surface.
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Never run as root. If the process is compromised, it holds an unprivileged account.
RUN addgroup -S courtside && adduser -S courtside -G courtside
USER courtside

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Container-aware JVM flags: without MaxRAMPercentage the JVM sizes its heap from the
# HOST's memory and gets killed by the container limit.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

# The orchestrator needs to know the difference between "started" and "ready".
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# exec form (not shell form) so the JVM is PID 1 and receives SIGTERM directly —
# otherwise Docker kills the shell and the app never shuts down gracefully.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
