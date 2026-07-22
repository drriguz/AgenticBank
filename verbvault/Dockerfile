FROM eclipse-temurin:21-jre-alpine
COPY target/atm-*.jar app.jar
VOLUME /atm-data
ENV SPRING_PROFILES_ACTIVE=docker
ENTRYPOINT ["java", "-jar", "/app.jar"]
