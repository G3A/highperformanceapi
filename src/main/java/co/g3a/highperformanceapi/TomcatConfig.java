package co.g3a.highperformanceapi;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> {
            // Configurar Tomcat para alto rendimiento
            factory.addConnectorCustomizers(connector -> {
                connector.setProperty("maxThreads", "2000");
                connector.setProperty("minSpareThreads", "1000");
                connector.setProperty("maxConnections", "100000");
                connector.setProperty("acceptCount", "65535");
                connector.setProperty("connectionTimeout", "30000");
                connector.setProperty("keepAliveTimeout", "30000");
                connector.setProperty("maxKeepAliveRequests", "100000");
                connector.setProperty("disableUploadTimeout", "true");
                connector.setProperty("socket.appReadBufSize", "65535");
                connector.setProperty("socket.appWriteBufSize", "65535");
                connector.setProperty("socket.performanceConnectionTime", "1");
                connector.setProperty("socket.performanceLatency", "0");
                connector.setProperty("socket.performanceBandwidth", "2");
            });
        };
    }
}