# Runtime image for Camel Core Downstream Service
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:latest

# Set working directory
WORKDIR /app

# Copy the pre-built JAR from local target directory
COPY target/camel-core-downstream-service-*-with-dependencies.jar /app/app.jar

# Environment variables for runtime configuration
ENV REGISTRATION_URL="" \
    REGISTRATION_ANNOUNCE_ADDRESS="" \
    GRPC_PORT="9190" \
    SERVICE_NAME="" \
    ROUTES_PATH="" \
    ROUTES_RULES="" \
    TOKEN_ENDPOINT="" \
    CLIENT_ID="" \
    CLIENT_SECRET=""

# Expose the gRPC port
EXPOSE ${GRPC_PORT}

# Run the application with environment variables
ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar \
    ${REGISTRATION_URL:+--registration-url $REGISTRATION_URL} \
    ${REGISTRATION_ANNOUNCE_ADDRESS:+--registration-announce-address $REGISTRATION_ANNOUNCE_ADDRESS} \
    ${GRPC_PORT:+--grpc-port $GRPC_PORT} \
    ${SERVICE_NAME:+--name $SERVICE_NAME} \
    ${ROUTES_PATH:+--routes-path $ROUTES_PATH} \
    ${ROUTES_RULES:+--routes-rules $ROUTES_RULES} \
    ${TOKEN_ENDPOINT:+--token-endpoint $TOKEN_ENDPOINT} \
    ${CLIENT_ID:+--client-id $CLIENT_ID} \
    ${CLIENT_SECRET:+--client-secret $CLIENT_SECRET}"]
