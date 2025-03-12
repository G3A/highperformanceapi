# Stage 2: Run the application
FROM bellsoft/liberica-openjdk-alpine-musl:21
# Configuración de la JVM para entornos con memoria limitada
ENV JAVA_OPTS="-Xms256m -Xmx1024m -XX:+UseG1GC -XX:+UseStringDeduplication -XX:MaxGCPauseMillis=100"

COPY target/*.jar /usr/local/lib/app.jar


EXPOSE 8080
ENTRYPOINT ["java","-Xmx1490m","-jar","/usr/local/lib/app.jar"]