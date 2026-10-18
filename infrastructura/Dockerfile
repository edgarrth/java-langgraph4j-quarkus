FROM axiz-base AS build
WORKDIR /opt/build
COPY src ./src
COPY datasets ./datasets
RUN mvn -B -ntp package -DskipTests
FROM axiz-runtime
WORKDIR /opt/app
COPY --from=build --chown=10001:10001 /opt/build/target/quarkus-app/ ./
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
