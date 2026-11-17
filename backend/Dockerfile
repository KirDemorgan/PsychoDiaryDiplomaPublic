FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml lombok.config ./
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 diary
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
USER diary
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]
