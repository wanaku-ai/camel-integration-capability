# Runtime image for Camel Core Downstream Service
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:latest

# Set working directory
WORKDIR /app

# Copy the pre-built JAR from main module target directory
COPY camel-integration-capability-runtimes/camel-integration-capability-main/target/camel-integration-capability-main-*-jar-with-dependencies.jar /app/app.jar

# Environment variables for runtime configuration
ENV REGISTRATION_URL="" \
    SERVICE_NAME="" \
    ROUTES_PATH="" \
    SERVICE_CATALOG="" \
    SERVICE_CATALOG_SYSTEM="" \
    DEPENDENCIES="" \
    INIT_FROM="" \
    REPOSITORIES="" \
    DATA_DIR="/data" \
    MCP_TAGS="" \
    MCP_PORT="9090"

# Create and declare volume for routes data
VOLUME /data

# Expose the MCP server port
EXPOSE ${MCP_PORT}

# Run the application with environment variables.
# This part uses shell parameter expansion to conditionally add command-line arguments to the Java application.
# The Syntax: ${VARIABLE:+value} means: "If VARIABLE is set and is not null (i.e., not empty), substitute this whole expression
# with value. Otherwise, substitute it with nothing (an empty string)."
ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar \
    ${REGISTRATION_URL:+--registration-url $REGISTRATION_URL} \
    ${SERVICE_NAME:+--name $SERVICE_NAME} \
    ${ROUTES_PATH:+--routes-ref $ROUTES_PATH} \
    ${SERVICE_CATALOG:+--service-catalog $SERVICE_CATALOG} \
    ${SERVICE_CATALOG_SYSTEM:+--service-catalog-system $SERVICE_CATALOG_SYSTEM} \
    ${DEPENDENCIES:+--dependencies $DEPENDENCIES} \
    ${INIT_FROM:+--init-from $INIT_FROM} \
    ${REPOSITORIES:+--repositories $REPOSITORIES} \
    ${DATA_DIR:+--data-dir $DATA_DIR} \
    ${MCP_TAGS:+--mcp-tags $MCP_TAGS} \
    ${MCP_PORT:+--mcp-port $MCP_PORT}"]
