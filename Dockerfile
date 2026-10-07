# Container build so the API can move off Heroku (Azure App Service, university hosting) - Section 5.4.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 mms
COPY --from=build /src/target/mms-api.jar /app/mms-api.jar
USER mms
ENV PORT=8080 JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -Dserver.port=$PORT $JAVA_OPTS -jar /app/mms-api.jar"]
