# Aetheris - Part1 (skeleton)

## Dev run (requires Java 8 and Maven)
1. Build:
   mvn clean package
2. Run:
   java -jar target/aetheris-0.0.1-SNAPSHOT.jar
3. Open browser:
   - Landing: http://localhost:8080/
   - Register: http://localhost:8080/register.html
   - Login: http://localhost:8080/login.html
   - Dashboard (after login): http://localhost:8080/dashboard.html
4. H2 console (dev): http://localhost:8080/h2-console
   JDBC URL: jdbc:h2:./data/aetherisdb  User: sa  Password: (blank)

## API
- POST /api/auth/register  {username, email, password}
- POST /api/auth/login     {who, password}
- GET  /api/secure/dashboard  (header Authorization: Bearer <token>)
