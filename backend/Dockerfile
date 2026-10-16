# ---- build ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# IPv4 stack: some sandboxed/proxied networks break the JVM's IPv6 CONNECT tunneling
ENV MAVEN_OPTS="-Djava.net.preferIPv4Stack=true"
RUN apt-get update -qq && apt-get install -y -qq maven > /dev/null \
    && mvn -q -B package -DskipTests \
    && apt-get purge -y -qq maven && rm -rf /var/lib/apt/lists/*

# ---- run ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/careslot-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-jar", "app.jar"]
