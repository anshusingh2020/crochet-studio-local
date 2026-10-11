FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B verify
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 studio && mkdir /app /app/uploads && chown -R studio /app
WORKDIR /app
COPY --from=build /build/target/crochet-studio-1.0.0.jar app.jar
USER studio
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
