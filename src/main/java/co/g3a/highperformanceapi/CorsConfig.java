package co.g3a.highperformanceapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // Permitir solicitudes desde cualquier origen
        config.addAllowedOrigin("http://localhost:63342"); // Tu origen específico
        config.addAllowedOrigin("http://127.0.0.1:63342");
        config.addAllowedOrigin("*"); // O cualquier origen (menos seguro)

        // Permitir todos los encabezados y métodos
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        
        // Permitir SSE
        config.addExposedHeader("Content-Type");
        
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}