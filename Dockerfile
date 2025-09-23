FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -q -e -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -e -B -DskipTests clean package

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN useradd -ms /bin/bash appuser
WORKDIR /app

COPY --from=build /workspace/target/file-storage-0.0.1-SNAPSHOT.jar /app/app.jar

RUN mkdir -p /app/logs && chown -R appuser:appuser /app
USER appuser

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
