package com.gps.worldexplorerinteractive.service;

import com.gps.worldexplorerinteractive.config.ApiProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Servicio de geolocalización apoyado en la API configurada (RF-08).
 * - Geocoding: dirección -> coordenadas (lat, lon).
 * - Directions: coordenadas -> distancia, duración y geometría de la ruta.
 * Si la API externa no está disponible, degrada con valores simulados
 * para que el sistema siga operativo.
 */
@Service
public class GeoService {

    private final RestClient geoRestClient;
    private final ApiProperties apiProperties;
    private final Random random = new Random();

    public GeoService(RestClient geoRestClient, ApiProperties apiProperties) {
        this.geoRestClient = geoRestClient;
        this.apiProperties = apiProperties;
    }

    /** Punto geográfico simple. */
    public record GeoPoint(double lat, double lon, String label) {}

    /** Resultado de una ruta calculada por el proveedor. */
    public record GeoRoute(double distanceKm, double durationMin, List<List<Double>> geometry) {}

    /** Geocoding: "Lima" -> [-12.046, -77.042]. */
    @SuppressWarnings("unchecked")
    public GeoPoint geocode(String address) {
        try {
            Map<String, Object> response = geoRestClient.get()
                    .uri("/geocode/search?api_key={k}&text={t}&size=1",
                            apiProperties.getApiKey(), address)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            List<List<Double>> coords = (List<List<Double>>) response.get("features");
            if (coords != null && !coords.isEmpty()) {
                List<Double> c = (List<Double>) ((Map<String, Object>) coords.get(0)).get("coordinates");
                return new GeoPoint(c.get(1), c.get(0), address);
            }
        } catch (Exception e) {
            // API no disponible: fallback determinista simulado
        }
        return simulatedPoint(address);
    }

    /** Directions POST: devuelve distancia, duración y geometría real (o simulada). */
    @SuppressWarnings("unchecked")
    public GeoRoute directions(GeoPoint start, GeoPoint end) {
        try {
            Map<String, Object> body = Map.of(
                    "coordinates", List.of(
                            List.of(start.lon(), start.lat()),
                            List.of(end.lon(), end.lat())),
                    "instructions", false);
            Map<String, Object> response = geoRestClient.post()
                    .uri("/v2/directions/driving-car?api_key={k}", apiProperties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            List<Map<String, Object>> routes = (List<Map<String, Object>>) response.get("routes");
            if (routes != null && !routes.isEmpty()) {
                Map<String, Object> route = routes.get(0);
                Map<String, Object> summary = (Map<String, Object>) route.get("summary");
                List<List<Double>> geometry = (List<List<Double>>) route.get("geometry");
                double meters = ((Number) summary.get("distance")).doubleValue();
                double seconds = ((Number) summary.get("duration")).doubleValue();
                return new GeoRoute(meters / 1000.0, seconds / 60.0, geometry);
            }
        } catch (Exception e) {
            // Fallback: distancia haversine con factor de curvatura vial
        }
        double km = haversineKm(start, end) * 1.35;
        return new GeoRoute(km, km / 55.0 * 60.0, null);
    }

    /** Distancia entre dos puntos con la fórmula de Haversine. */
    public double haversineKm(GeoPoint a, GeoPoint b) {
        double R = 6371.0;
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.asin(Math.sqrt(h));
    }

    /** Interpola posición a lo largo de una geometría según avance 0..1. */
    public GeoPoint interpolate(List<List<Double>> geometry, double fraction) {
        if (geometry == null || geometry.size() < 2) {
            return null;
        }
        fraction = Math.max(0, Math.min(1, fraction));
        int seg = (int) Math.floor(fraction * (geometry.size() - 1));
        seg = Math.min(seg, geometry.size() - 2);
        double local = fraction * (geometry.size() - 1) - seg;
        List<Double> p1 = geometry.get(seg);
        List<Double> p2 = geometry.get(seg + 1);
        double lon = p1.get(0) + (p2.get(0) - p1.get(0)) * local;
        double lat = p1.get(1) + (p2.get(1) - p1.get(1)) * local;
        return new GeoPoint(lat, lon, "posición actual");
    }

    /** Fallback simulado pero estable por nombre de lugar. */
    private GeoPoint simulatedPoint(String address) {
        double seed = address.toLowerCase().chars().sum() % 360;
        return new GeoPoint(-12.0 + (seed % 10) - 5, -77.0 + (seed % 8) - 4, address + " (simulado)");
    }
}
