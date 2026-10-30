# ==============================================================
# Multi-stage Dockerfile for Supplexio Procurement System
# ==============================================================

# Stage 1: Build the Java Web Application
FROM eclipse-temurin:8-jdk AS builder

WORKDIR /build

# Copy project source and dependencies
COPY AxaltaWebApp /build/AxaltaWebApp

# Create build output directory and compile all Java classes
RUN mkdir -p /build/out/WEB-INF/classes /build/out/WEB-INF/lib && \
    find AxaltaWebApp/src/java -name "*.java" > /build/sources.txt && \
    javac -encoding UTF-8 \
      -cp "AxaltaWebApp/lib/gson-2.10.1.jar:AxaltaWebApp/lib/itextpdf-5.5.13.2.jar:AxaltaWebApp/lib/javax.servlet-api-3.1.0.jar:AxaltaWebApp/lib/json-simple-1.1.1.jar:AxaltaWebApp/lib/jstl-1.2.jar:AxaltaWebApp/lib/mysql-connector-j-9.2.0.jar" \
      -d /build/out/WEB-INF/classes \
      @/build/sources.txt

# Copy web assets, JSPs, and runtime libraries to create deployable directory
RUN cp -r AxaltaWebApp/web/* /build/out/ && \
    cp AxaltaWebApp/lib/gson-2.10.1.jar /build/out/WEB-INF/lib/ && \
    cp AxaltaWebApp/lib/itextpdf-5.5.13.2.jar /build/out/WEB-INF/lib/ && \
    cp AxaltaWebApp/lib/json-simple-1.1.1.jar /build/out/WEB-INF/lib/ && \
    cp AxaltaWebApp/lib/jstl-1.2.jar /build/out/WEB-INF/lib/ && \
    cp AxaltaWebApp/lib/mysql-connector-j-9.2.0.jar /build/out/WEB-INF/lib/

# Copy Python scraper script if needed into WEB-INF data
RUN if [ -f "AxaltaWebApp/scrape_copper_prices.py" ]; then \
      cp AxaltaWebApp/scrape_copper_prices.py /build/out/WEB-INF/data/; \
    fi

# Stage 2: Runtime Tomcat Server
FROM tomcat:9.0-jre8-temurin

LABEL maintainer="Supplexio Team"
LABEL description="Supplexio Reverse Auction & Procurement System"

# Remove default Tomcat webapps
RUN rm -rf /usr/local/tomcat/webapps/*

# Deploy Supplexio as ROOT application so it's accessible at domain root (/)
COPY --from=builder /build/out /usr/local/tomcat/webapps/ROOT

# Place MySQL driver into Tomcat global library
COPY --from=builder /build/AxaltaWebApp/lib/mysql-connector-j-9.2.0.jar /usr/local/tomcat/lib/

# Set default environment variables
ENV DB_HOST=mysql \
    DB_PORT=3306 \
    DB_NAME=axalta \
    DB_USER=root \
    DB_PASSWORD=root \
    JAVA_OPTS="-Dfile.encoding=UTF-8 -Xms256m -Xmx1024m"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/ || exit 1

CMD ["catalina.sh", "run"]

