FROM bellsoft/liberica-openjdk-alpine-musl:21

# Optimizaciones de la JVM para contenedor con recursos limitados
ENV JAVA_OPTS="-XX:+UseG1GC -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=35 -Xms1024m -Xmx1024m -XX:+UseStringDeduplication -XX:+ExplicitGCInvokesConcurrent -XX:MaxMetaspaceSize=256m -XX:G1NewSizePercent=5 -XX:G1MaxNewSizePercent=10 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp -XX:ConcGCThreads=1 -XX:ParallelGCThreads=1 "

COPY target/*.jar /usr/local/lib/app.jar


EXPOSE 8080
ENTRYPOINT ["java","-jar","/usr/local/lib/app.jar"]