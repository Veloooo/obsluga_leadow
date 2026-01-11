# ====== BUILD STAGE ======
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# kopiujemy tylko pliki potrzebne do buildu (cache)
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

# kopiujemy źródła
COPY src src

# budujemy aplikację
RUN ./gradlew clean bootJar --no-daemon

# ====== RUNTIME STAGE ======
FROM eclipse-temurin:21-jre

WORKDIR /app

# kopiujemy tylko gotowy jar
COPY --from=builder /app/build/libs/*.jar app.jar

# Render używa PORT jako zmiennej środowiskowej
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

# Render automatycznie ustawia $PORT
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=$PORT -jar app.jar"]
