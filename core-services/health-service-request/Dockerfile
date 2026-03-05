# Build stage
FROM openjdk:8-alpine AS build

# Install Maven
RUN apk add --no-cache maven

ARG WORK_DIR
WORKDIR /app

# Copy the project files
COPY ${WORK_DIR}/pom.xml ./pom.xml
COPY start.sh ./start.sh

# not useful for stateless builds
# RUN mvn -B dependency:go-offline
COPY ${WORK_DIR}/src ./src
RUN mvn -B -f /app/pom.xml package -DskipTests

# Runtime image
FROM openjdk:8-alpine

WORKDIR /opt/egov

COPY --from=build /app/target/*.jar /app/start.sh /opt/egov/

RUN chmod +x /opt/egov/start.sh

CMD ["/opt/egov/start.sh"]
