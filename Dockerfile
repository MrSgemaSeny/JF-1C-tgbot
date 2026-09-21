FROM gradle:8.10-jdk17-alpine AS build
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon || true
COPY --chown=gradle:gradle src ./src
RUN gradle build --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar
ENV JAVA_OPTS= -XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC -Xss256k -Dfile .encoding=UTF-8
ENTRYPOINT [sh, -c, java -jar app.jar]
