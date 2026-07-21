FROM maven:3.9-eclipse-temurin-17

WORKDIR /cantor

COPY . .

RUN mvn clean install -DskipTests -Dgpg.skip=true -Dmaven.javadoc.skip=true -Dsource.skip=true

ENV CANTOR_SERVER_HOST=""
ENV CANTOR_SERVER_PORT="7443"

# Wrapper script to run tests
COPY --chmod=755 <<'EOF' /entrypoint.sh
#!/bin/sh
mvn -q -pl cantor-integration test-compile exec:java \
  -Dexec.mainClass=com.salesforce.cantor.integration.IntegrationTestRunner \
  -Dexec.args="$*" \
  -Dexec.classpathScope=test \
  -Dgpg.skip=true
EOF

ENTRYPOINT ["/entrypoint.sh"]
