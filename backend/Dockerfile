FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system splitpay && useradd --system --gid splitpay splitpay
COPY build/libs/splitpay-backend-0.1.0.jar /app/app.jar
USER splitpay
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
