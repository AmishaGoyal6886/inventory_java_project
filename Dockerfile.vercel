FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mkdir -p src/main/java/com/stockroom src/main/resources/public && \
    if [ -f StockroomApplication.java ]; then cp StockroomApplication.java src/main/java/com/stockroom/StockroomApplication.java; fi && \
    if [ -f index.html ]; then cp index.html src/main/resources/public/index.html; fi
RUN mvn -B -q dependency:go-offline
RUN mvn -B -q package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/stockroom-1.0.0.jar /app/stockroom.jar
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/stockroom.jar"]
