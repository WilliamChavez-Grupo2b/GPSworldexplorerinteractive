package com.gps.worldexplorerinteractive.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Place search and route calculations through Google Maps Platform. */
@Service
public class GeoService {

    private static final String PLACE_AUTOCOMPLETE_FIELD_MASK =
            "suggestions.placePrediction.placeId,suggestions.placePrediction.text.text";
    private static final String PLACE_DETAILS_FIELD_MASK =
            "id,displayName,formattedAddress,location";
    private static final String ROUTE_FIELD_MASK =
            "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline";

    private final RestClient placesRestClient;
    private final RestClient routesRestClient;

    public GeoService(@Qualifier("placesRestClient") RestClient placesRestClient,
                      @Qualifier("routesRestClient") RestClient routesRestClient) {
        this.placesRestClient = placesRestClient;
        this.routesRestClient = routesRestClient;
    }

    public record PlaceSuggestion(String placeId, String label) {}

    public record GeoPoint(double lat, double lon, String label) {}

    public record GeoRoute(double distanceKm, double durationMin, List<List<Double>> geometry) {}

    private record DecodedValue(int value, int nextIndex) {}

    /**
     * Searches place names and optionally biases results toward the selected origin.
     * The location bias is not a geographic restriction.
     */
    public List<PlaceSuggestion> searchPlaces(String query, Double focusLat, Double focusLon,
                                               String sessionToken) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        Map<String, Object> request = new HashMap<>();
        request.put("input", query);
        request.put("languageCode", "es");
        request.put("sessionToken", sessionToken == null || sessionToken.isBlank()
                ? UUID.randomUUID().toString()
                : sessionToken);
        if (focusLat != null && focusLon != null) {
            request.put("locationBias", Map.of(
                    "circle", Map.of(
                            "center", Map.of("latitude", focusLat, "longitude", focusLon),
                            "radius", 50000.0)));
        }

        Map<?, ?> response;
        try {
            response = placesRestClient.post()
                    .uri("/v1/places:autocomplete")
                    .header("X-Goog-FieldMask", PLACE_AUTOCOMPLETE_FIELD_MASK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            throw providerFailure("Places Autocomplete", e);
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo conectar con Google Places.", e);
        }

        if (response == null || !(response.get("suggestions") instanceof List<?> suggestions)) {
            return List.of();
        }

        List<PlaceSuggestion> results = new ArrayList<>();
        for (Object item : suggestions) {
            if (!(item instanceof Map<?, ?> suggestion)
                    || !(suggestion.get("placePrediction") instanceof Map<?, ?> prediction)
                    || !(prediction.get("placeId") instanceof String placeId)
                    || !(prediction.get("text") instanceof Map<?, ?> text)
                    || !(text.get("text") instanceof String label)
                    || placeId.isBlank() || label.isBlank()) {
                continue;
            }
            results.add(new PlaceSuggestion(placeId, label));
        }
        return results;
    }

    /** Resolves a selected Places suggestion to its exact coordinates. */
    public GeoPoint resolvePlace(String placeId, String sessionToken) {
        if (placeId == null || placeId.isBlank()) {
            throw new IllegalArgumentException("Debes seleccionar un lugar válido.");
        }

        Map<?, ?> response;
        try {
            response = placesRestClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/v1/places/{placeId}");
                        if (sessionToken != null && !sessionToken.isBlank()) {
                            builder.queryParam("sessionToken", sessionToken);
                        }
                        return builder.build(placeId);
                    })
                    .header("X-Goog-FieldMask", PLACE_DETAILS_FIELD_MASK)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            throw providerFailure("Place Details", e);
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo conectar con Google Places.", e);
        }

        if (response == null || !(response.get("location") instanceof Map<?, ?> location)
                || !(location.get("latitude") instanceof Number latitude)
                || !(location.get("longitude") instanceof Number longitude)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Google Places no devolvió las coordenadas del lugar seleccionado.");
        }

        double lat = latitude.doubleValue();
        double lon = longitude.doubleValue();
        if (!validCoordinates(lat, lon)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Google Places devolvió coordenadas fuera de rango.");
        }
        return new GeoPoint(lat, lon, getPlaceLabel(response, placeId));
    }

    /** Resolves a free-form address to the first real geocoding result. */
    public GeoPoint geocode(String address) {
        String sessionToken = UUID.randomUUID().toString();
        List<PlaceSuggestion> results = searchPlaces(address, null, null, sessionToken);
        if (results.isEmpty()) {
            throw new IllegalArgumentException("No se encontró la ubicación: " + address);
        }
        return resolvePlace(results.get(0).placeId(), sessionToken);
    }

    /** Calculates a real driving route and decodes its encoded polyline. */
    public GeoRoute directions(GeoPoint start, GeoPoint end, boolean avoidTolls) {
        Map<String, Object> body = Map.of(
                "origin", Map.of("location", Map.of("latLng", Map.of(
                        "latitude", start.lat(), "longitude", start.lon()))),
                "destination", Map.of("location", Map.of("latLng", Map.of(
                        "latitude", end.lat(), "longitude", end.lon()))),
                "travelMode", "DRIVE",
                "routingPreference", "TRAFFIC_AWARE",
                "routeModifiers", Map.of("avoidTolls", avoidTolls),
                "languageCode", "es",
                "units", "METRIC");

        Map<?, ?> response;
        try {
            response = routesRestClient.post()
                    .uri("/directions/v2:computeRoutes")
                    .header("X-Goog-FieldMask", ROUTE_FIELD_MASK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            throw providerFailure("Routes", e);
        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo conectar con Google Routes.", e);
        }

        if (response == null || !(response.get("routes") instanceof List<?> routes)
                || routes.isEmpty() || !(routes.get(0) instanceof Map<?, ?> route)
                || !(route.get("distanceMeters") instanceof Number distanceMeters)
                || !(route.get("duration") instanceof String duration)
                || !(route.get("polyline") instanceof Map<?, ?> polyline)
                || !(polyline.get("encodedPolyline") instanceof String encodedPolyline)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Google Routes no devolvió una ruta válida para esos puntos.");
        }

        List<List<Double>> geometry = decodePolyline(encodedPolyline);
        if (geometry.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Google Routes devolvió una geometría de ruta no válida.");
        }
        return new GeoRoute(distanceMeters.doubleValue() / 1000.0,
                parseDurationSeconds(duration) / 60.0, geometry);
    }

    private String getPlaceLabel(Map<?, ?> response, String fallback) {
        if (response.get("formattedAddress") instanceof String address && !address.isBlank()) {
            return address;
        }
        if (response.get("displayName") instanceof Map<?, ?> displayName
                && displayName.get("text") instanceof String name && !name.isBlank()) {
            return name;
        }
        return fallback;
    }

    private double parseDurationSeconds(String duration) {
        try {
            String value = duration.endsWith("s")
                    ? duration.substring(0, duration.length() - 1)
                    : duration;
            double seconds = Double.parseDouble(value);
            if (Double.isFinite(seconds) && seconds >= 0) {
                return seconds;
            }
        } catch (NumberFormatException ignored) {
            // Invalid provider durations are handled by the caller as malformed route data.
        }
        return 0;
    }

    private List<List<Double>> decodePolyline(String encoded) {
        List<List<Double>> points = new ArrayList<>();
        int index = 0;
        int latitude = 0;
        int longitude = 0;

        while (index < encoded.length()) {
            DecodedValue latitudeResult = decodePolylineValue(encoded, index);
            if (latitudeResult == null) {
                return List.of();
            }
            latitude += latitudeResult.value();
            index = latitudeResult.nextIndex();

            DecodedValue longitudeResult = decodePolylineValue(encoded, index);
            if (longitudeResult == null) {
                return List.of();
            }
            longitude += longitudeResult.value();
            index = longitudeResult.nextIndex();

            double lat = latitude / 100000.0;
            double lon = longitude / 100000.0;
            if (!validCoordinates(lat, lon)) {
                return List.of();
            }
            points.add(List.of(lon, lat));
        }
        return points;
    }

    private DecodedValue decodePolylineValue(String encoded, int startIndex) {
        int result = 0;
        int shift = 0;
        int index = startIndex;
        while (true) {
            if (index >= encoded.length() || shift > 30) {
                return null;
            }
            int chunk = encoded.charAt(index++) - 63;
            if (chunk < 0 || chunk > 63) {
                return null;
            }
            result |= (chunk & 0x1f) << shift;
            if (chunk < 0x20) {
                break;
            }
            shift += 5;
        }

        int delta = (result & 1) != 0 ? ~(result >> 1) : result >> 1;
        return new DecodedValue(delta, index);
    }

    private boolean validCoordinates(double lat, double lon) {
        return Double.isFinite(lat) && lat >= -90 && lat <= 90
                && Double.isFinite(lon) && lon >= -180 && lon <= 180;
    }

    private ResponseStatusException providerFailure(String api, RestClientResponseException e) {
        String hint = e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403
                ? " Verifica la clave, las restricciones, la facturación y que la API esté habilitada."
                : "";
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Google " + api + " respondió HTTP " + e.getStatusCode().value() + "." + hint, e);
    }

    /** Haversine distance between two points. */
    public double haversineKm(GeoPoint a, GeoPoint b) {
        double earthRadiusKm = 6371.0;
        double deltaLatitude = Math.toRadians(b.lat() - a.lat());
        double deltaLongitude = Math.toRadians(b.lon() - a.lon());
        double haversine = Math.sin(deltaLatitude / 2) * Math.sin(deltaLatitude / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.sin(deltaLongitude / 2) * Math.sin(deltaLongitude / 2);
        return 2 * earthRadiusKm * Math.asin(Math.sqrt(haversine));
    }

    /** Interpolates a position along a route geometry at fraction 0..1. */
    public GeoPoint interpolate(List<List<Double>> geometry, double fraction) {
        if (geometry == null || geometry.size() < 2) {
            return null;
        }
        fraction = Math.max(0, Math.min(1, fraction));
        int segment = Math.min((int) Math.floor(fraction * (geometry.size() - 1)), geometry.size() - 2);
        double local = fraction * (geometry.size() - 1) - segment;
        List<Double> first = geometry.get(segment);
        List<Double> second = geometry.get(segment + 1);
        double longitude = first.get(0) + (second.get(0) - first.get(0)) * local;
        double latitude = first.get(1) + (second.get(1) - first.get(1)) * local;
        return new GeoPoint(latitude, longitude, "posición actual");
    }
}
