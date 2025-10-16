# Use official OpenJDK 17 image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy Maven wrapper and project files
COPY . .

# Build the project
RUN ./mvnw clean package -DskipTests

# Expose port (Render uses $PORT)
ENV PORT 10000
EXPOSE $PORT

# Start the Spring Boot app
CMD ["java", "-jar", "target/aetheris-0.0.1-SNAPSHOT.jar"]
