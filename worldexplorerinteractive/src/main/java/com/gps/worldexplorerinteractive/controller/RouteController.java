package com.gps.worldexplorerinteractive.controller;

import com.gps.worldexplorerinteractive.model.RouteOption;
import com.gps.worldexplorerinteractive.model.Trip;
import com.gps.worldexplorerinteractive.service.GeoService;
import com.gps.worldexplorerinteractive.service.NavigationService;
import com.gps.worldexplorerinteractive.service.RouteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteService routeService;
    private final NavigationService navigationService;
    private final GeoService geoService;

    public RouteController(RouteService routeService, NavigationService navigationService, GeoService geoService) {
        this.routeService = routeService;
        this.navigationService = navigationService;
        this.geoService = geoService;
    }

    /** RF-01 / RF-02: planificar rutas origen -> destino con preferencias. */
    @PostMapping("/plan")
    public List<RouteOption> plan(@RequestBody Map<String, Object> body) {
        String origin = body.get("origin") instanceof String value ? value.trim() : "";
        String destination = body.get("destination") instanceof String value ? value.trim() : "";
        if (origin.isBlank() || destination.isBlank()) {
            throw new IllegalArgumentException("Origen y destino son obligatorios");
        }
        GeoService.GeoPoint start = requestPoint(body, "origin", origin);
        GeoService.GeoPoint end = requestPoint(body, "destination", destination);
        if ((start == null) != (end == null)) {
            throw new IllegalArgumentException("Debes indicar las coordenadas del origen y del destino");
        }
        boolean avoidTolls = Boolean.TRUE.equals(body.get("avoidTolls"));
        boolean preferSafe = Boolean.TRUE.equals(body.get("preferSafeRoutes"));
        return routeService.planRoutes(origin, destination, avoidTolls, preferSafe, start, end);
    }

    /** RF-03: iniciar un viaje con la ruta seleccionada. */
    @PostMapping("/trips")
    public Trip startTrip(@RequestBody Trip trip) {
        return navigationService.startTrip(trip);
    }

    /** RF-03 / RF-04: actualizar navegación en tiempo real. */
    @PostMapping("/trips/{tripId}/update")
    public ResponseEntity<NavigationService.NavigationState> update(@PathVariable String tripId,
                                                                    @RequestBody RouteOption route) {
        return ResponseEntity.ok(navigationService.update(tripId, route));
    }

    @PostMapping("/trips/{tripId}/finish")
    public Trip finish(@PathVariable String tripId) {
        return navigationService.finishTrip(tripId);
    }

    /** RF-08: geocoding directo -> coordenadas geográficas. */
    @GetMapping("/geocode")
    public GeoService.GeoPoint geocode(@RequestParam String address) {
        return geoService.geocode(address);
    }

    @GetMapping("/geocode/suggestions")
    public List<GeoService.PlaceSuggestion> geocodeSuggestions(@RequestParam String query,
                                                                @RequestParam(required = false) Double focusLat,
                                                                @RequestParam(required = false) Double focusLon,
                                                                @RequestParam(required = false) String sessionToken) {
        if ((focusLat == null) != (focusLon == null)) {
            throw new IllegalArgumentException("Debes indicar ambas coordenadas para enfocar la búsqueda");
        }
        if (focusLat != null && (!Double.isFinite(focusLat) || focusLat < -90 || focusLat > 90
                || !Double.isFinite(focusLon) || focusLon < -180 || focusLon > 180)) {
            throw new IllegalArgumentException("Las coordenadas para enfocar la búsqueda están fuera de rango");
        }
        return geoService.searchPlaces(query, focusLat, focusLon, sessionToken);
    }

    @GetMapping("/geocode/place")
    public GeoService.GeoPoint geocodePlace(@RequestParam String placeId,
                                             @RequestParam(required = false) String sessionToken) {
        return geoService.resolvePlace(placeId, sessionToken);
    }

    /** RF-08: mi ubicación actual (distancia con Haversine entre dos puntos). */
    @GetMapping("/distance")
    public Map<String, Object> distance(@RequestParam double lat1, @RequestParam double lon1,
                                        @RequestParam double lat2, @RequestParam double lon2) {
        double km = geoService.haversineKm(new GeoService.GeoPoint(lat1, lon1, "a"),
                new GeoService.GeoPoint(lat2, lon2, "b"));
        return Map.of("distanceKm", Math.round(km * 10) / 10.0);
    }

    private GeoService.GeoPoint requestPoint(Map<String, Object> body, String prefix, String label) {
        Object rawLat = body.get(prefix + "Lat");
        Object rawLon = body.get(prefix + "Lon");
        if (rawLat == null && rawLon == null) {
            return null;
        }
        if (!(rawLat instanceof Number latitude) || !(rawLon instanceof Number longitude)) {
            throw new IllegalArgumentException("Las coordenadas de " + prefix + " no son válidas");
        }

        double lat = latitude.doubleValue();
        double lon = longitude.doubleValue();
        if (!Double.isFinite(lat) || lat < -90 || lat > 90
                || !Double.isFinite(lon) || lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Las coordenadas de " + prefix + " están fuera de rango");
        }
        return new GeoService.GeoPoint(lat, lon, label);
    }
}
