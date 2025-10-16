# Use OpenJDK 17
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy all project files
COPY . .

# Build the project using Maven wrapper
RUN mvn clean package -DskipTests

# Expose port (Render assigns one)
ENV PORT 10000
EXPOSE $PORT

# Start Spring Boot app (replace with your actual JAR name)
CMD ["java", "-jar", "target/aetheris-0.0.1-SNAPSHOT.jar"]
