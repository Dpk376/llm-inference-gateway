FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace
RUN apk add --no-cache maven
COPY pom.xml .
COPY src src
RUN mvn package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/target/llm-inference-gateway-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
