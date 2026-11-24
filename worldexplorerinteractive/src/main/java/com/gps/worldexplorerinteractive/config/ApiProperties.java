package com.gps.worldexplorerinteractive.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * RF-08: configuración central del sistema a partir de application.properties.
 * Expone la API key (OpenRouteService) y un cliente HTTP reutilizable
 * para servicios de geolocalización (geocoding, routing, matriz de tráfico).
 */
@Configuration
public class ApiProperties {

    @Value("${Api_Key}")
    private String apiKey;

    @Value("${api.geo.base-url:https://api.openrouteservice.org}")
    private String geoBaseUrl;

    public String getApiKey() {
        return apiKey;
    }

    public String getGeoBaseUrl() {
        return geoBaseUrl;
    }

    /** Cliente HTTP autenticado contra el proveedor de geolocalización. */
    @Bean
    public RestClient geoRestClient() {
        return RestClient.builder()
                .baseUrl(geoBaseUrl)
                .defaultHeader("Authorization", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
