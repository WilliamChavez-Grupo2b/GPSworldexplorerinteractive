package com.gps.worldexplorerinteractive.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * RF-08: configuración central del sistema a partir de application.properties.
 * Expone la API key de Google Maps Platform para Places y Routes.
 */
@Configuration
public class ApiProperties {

    @Value("${Api_Key}")
    private String apiKey;

    @Value("${api.places.base-url:https://places.googleapis.com}")
    private String placesBaseUrl;

    @Value("${api.routes.base-url:https://routes.googleapis.com}")
    private String routesBaseUrl;

    public String getApiKey() {
        return apiKey;
    }

    public String getPlacesBaseUrl() {
        return placesBaseUrl;
    }

    public String getRoutesBaseUrl() {
        return routesBaseUrl;
    }

    /** Cliente autenticado para Places API (New). */
    @Bean
    public RestClient placesRestClient() {
        return RestClient.builder()
                .baseUrl(placesBaseUrl)
                .defaultHeader("X-Goog-Api-Key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /** Cliente autenticado para Routes API. */
    @Bean
    public RestClient routesRestClient() {
        return RestClient.builder()
                .baseUrl(routesBaseUrl)
                .defaultHeader("X-Goog-Api-Key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
