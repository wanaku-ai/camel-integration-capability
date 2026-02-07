# Runtime image for Camel Core Downstream Service
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:latest

# Set working directory
WORKDIR /app

# Copy the pre-built JAR from main module target directory
COPY camel-integration-capability-runtimes/camel-integration-capability-main/target/camel-integration-capability-main-*-jar-with-dependencies.jar /app/app.jar

# Environment variables for runtime configuration
ENV REGISTRATION_URL="" \
    REGISTRATION_ANNOUNCE_ADDRESS="" \
    GRPC_PORT="9190" \
    SERVICE_NAME="" \
    ROUTES_PATH="" \
    ROUTES_RULES="" \
    TOKEN_ENDPOINT="" \
    CLIENT_ID="" \
    CLIENT_SECRET="" \
    DEPENDENCIES="" \
    INIT_FROM="" \
    REPOSITORIES="" \
    DATA_DIR="/data"

# Create and declare volume for routes data
VOLUME /data

# Expose the gRPC port
EXPOSE ${GRPC_PORT}

# Run the application with environment variables.
# This part uses shell parameter expansion to conditionally add command-line arguments to the Java application.
# The Syntax: ${VARIABLE:+value} means: "If VARIABLE is set and is not null (i.e., not empty), substitute this whole expression
# with value. Otherwise, substitute it with nothing (an empty string)."
ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar \
    ${REGISTRATION_URL:+--registration-url $REGISTRATION_URL} \
    ${REGISTRATION_ANNOUNCE_ADDRESS:+--registration-announce-address $REGISTRATION_ANNOUNCE_ADDRESS} \
    ${GRPC_PORT:+--grpc-port $GRPC_PORT} \
    ${SERVICE_NAME:+--name $SERVICE_NAME} \
    ${ROUTES_PATH:+--routes-ref $ROUTES_PATH} \
    ${ROUTES_RULES:+--rules-ref $ROUTES_RULES} \
    ${TOKEN_ENDPOINT:+--token-endpoint $TOKEN_ENDPOINT} \
    ${CLIENT_ID:+--client-id $CLIENT_ID} \
    ${CLIENT_SECRET:+--client-secret $CLIENT_SECRET} \
    ${DEPENDENCIES:+--dependencies $DEPENDENCIES} \
    ${INIT_FROM:+--init-from $INIT_FROM} \
    ${REPOSITORIES:+--repositories $REPOSITORIES} \
    ${DATA_DIR:+--data-dir $DATA_DIR}"]
