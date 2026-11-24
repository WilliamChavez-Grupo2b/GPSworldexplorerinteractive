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
        String origin = String.valueOf(body.get("origin"));
        String destination = String.valueOf(body.get("destination"));
        if (origin == null || origin.isBlank() || destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("Origen y destino son obligatorios");
        }
        boolean avoidTolls = Boolean.TRUE.equals(body.get("avoidTolls"));
        boolean preferSafe = Boolean.TRUE.equals(body.get("preferSafeRoutes"));
        return routeService.planRoutes(origin, destination, avoidTolls, preferSafe);
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
        Trip trip = new Trip();
        trip.setId(tripId);
        return ResponseEntity.ok(navigationService.update(trip, route));
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

    /** RF-08: mi ubicación actual (distancia con Haversine entre dos puntos). */
    @GetMapping("/distance")
    public Map<String, Object> distance(@RequestParam double lat1, @RequestParam double lon1,
                                        @RequestParam double lat2, @RequestParam double lon2) {
        double km = geoService.haversineKm(new GeoService.GeoPoint(lat1, lon1, "a"),
                new GeoService.GeoPoint(lat2, lon2, "b"));
        return Map.of("distanceKm", Math.round(km * 10) / 10.0);
    }
}
