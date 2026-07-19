# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

# ---- Run stage ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Groovy verification rules are loaded at runtime, resolved relative to the working dir
COPY rules rules

# local profile = in-memory H2, no external database needed
ENV SPRING_PROFILES_ACTIVE=local
# Fit the JVM into a 512 MB free-tier container
ENV JAVA_TOOL_OPTIONS="-Xmx320m -XX:MaxMetaspaceSize=128m"

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
