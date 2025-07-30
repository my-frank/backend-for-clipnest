# Use an official OpenJDK runtime as a parent image for building the application
FROM eclipse-temurin:17-jdk-jammy as builder

# Set the working directory in the container
WORKDIR /app

# Copy the Maven wrapper and the POM file to leverage Docker's build cache
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Download dependencies
RUN ./mvnw dependency:go-offline

# Copy the project source code
COPY src ./src

# Package the application, skipping tests
RUN ./mvnw package -DskipTests

# --- Second Stage: Create the final, smaller image ---

# Use a smaller JRE image for the final application image
FROM eclipse-temurin:17-jre-jammy

# Set the working directory
WORKDIR /app

# Copy the packaged JAR from the builder stage
COPY --from=builder /app/target/ClipNest-0.0.1-SNAPSHOT.jar app.jar

# Expose the port the app runs on
EXPOSE 8080

# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
