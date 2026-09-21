# ---- Build ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Cacheia as dependencias do Maven antes de copiar o codigo
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B package -DskipTests

# ---- Runtime ----
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd --system --no-create-home spring
USER spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
