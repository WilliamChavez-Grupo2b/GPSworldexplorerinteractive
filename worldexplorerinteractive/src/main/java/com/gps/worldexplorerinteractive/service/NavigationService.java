package com.gps.worldexplorerinteractive.service;

import com.gps.worldexplorerinteractive.model.AlertEvent;
import com.gps.worldexplorerinteractive.model.RouteOption;
import com.gps.worldexplorerinteractive.model.Trip;
import com.gps.worldexplorerinteractive.repository.AlertEventRepository;
import com.gps.worldexplorerinteractive.repository.TripRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Random;

/**
 * Navegación en tiempo real y alertas de seguridad (RF-03, RF-04, RF-05).
 * Usa la geometría geográfica de la ruta para interpolar la posición actual
 * del vehículo y calcula el avance con fórmulas de geolocalización (Haversine).
 */
@Service
public class NavigationService {

    private final TripRepository tripRepository;
    private final AlertEventRepository alertEventRepository;
    private final GeoService geoService;
    private final Random random = new Random();

    public NavigationService(TripRepository tripRepository,
                             AlertEventRepository alertEventRepository,
                             GeoService geoService) {
        this.tripRepository = tripRepository;
        this.alertEventRepository = alertEventRepository;
        this.geoService = geoService;
    }

    public Trip startTrip(Trip trip) {
        trip.setStartedAt(Instant.now());
        trip.setStatus("ACTIVO");
        if (trip.getEvents() == null) trip.setEvents(new java.util.ArrayList<>());
        if (trip.getWeatherConditions() == null) trip.setWeatherConditions(new java.util.ArrayList<>());
        return tripRepository.save(trip);
    }

    /**
     * Simula un tick de navegación sobre la ruta geográfica:
     * posición (lat/lon), velocidad, giro próximo, distancia restante y ETA.
     * Si detecta velocidad excesiva o zona de riesgo, emite una alerta.
     */
    public NavigationState update(String tripId, RouteOption route) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No se encontró el viaje: " + tripId));
        double travelled = trip.getDistanceKm() > 0 ? trip.getDistanceKm() : 0;
        double remainingKm = Math.max(0.0, route.getDistanceKm() - travelled);
        double speedKmh = 40 + random.nextDouble() * 60;

        // Posición geográfica interpolada sobre la geometría de la ruta
        double fraction = route.getDistanceKm() > 0 ? travelled / route.getDistanceKm() : 1.0;
        GeoService.GeoPoint position = geoService.interpolate(route.getGeometry(), fraction);
        double lat = position != null ? position.lat() : route.getStartLat();
        double lon = position != null ? position.lon() : route.getStartLon();

        String nextTurn = nextTurn(route, fraction);

        List<String> events = trip.getEvents() != null ? trip.getEvents() : new java.util.ArrayList<>();
        events.add(String.format("Avance a %.0f km/h en (%.4f, %.4f). Próximo giro: %s",
                speedKmh, lat, lon, nextTurn));
        if (events.size() > 50) {
            events = events.subList(events.size() - 50, events.size());
        }

        if (speedKmh > 80) {
            createAlert(trip.getId(), "VELOCIDAD", "Velocidad excesiva: " + Math.round(speedKmh) + " km/h (límite 80 km/h)");
        }
        if (route.getRiskLevel() > 0.7) {
            createAlert(trip.getId(), "ZONA_RIESGO", "Zona de riesgo detectada en la ruta actual");
        }
        if ("LLUVIA".equals(route.getWeather()) || "NIEBLA".equals(route.getWeather())) {
            createAlert(trip.getId(), "CLIMA", "Condiciones climáticas adversas: " + route.getWeather());
        }

        trip.setEvents(events);
        trip.setDistanceKm(travelled + (remainingKm > 0 ? Math.min(2.5, route.getDistanceKm() * 0.15) : 0));
        tripRepository.save(trip);

        int etaMin = remainingKm > 0 ? (int) Math.ceil(remainingKm / (speedKmh / 60.0)) : 0;
        return new NavigationState(trip.getId(), (int) Math.round(speedKmh), nextTurn,
                Math.round(remainingKm * 10) / 10.0, etaMin, remainingKm <= 0, lat, lon);
    }

    /** Giro estimado según posición a lo largo de la ruta (cada ~25% del trazado). */
    private String nextTurn(RouteOption route, double fraction) {
        String[] turns = {"recto", "derecha", "recto", "izquierda"};
        int idx = Math.min(3, (int) Math.floor(fraction * 4));
        if (fraction >= 0.97) return "destino a la derecha";
        return turns[idx];
    }

    public Trip finishTrip(String tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No se encontró el viaje: " + tripId));
        trip.setFinishedAt(Instant.now());
        trip.setStatus("COMPLETADO");
        return tripRepository.save(trip);
    }

    private void createAlert(String tripId, String type, String message) {
        AlertEvent alert = new AlertEvent(null, tripId, type, message, Instant.now(), false);
        alertEventRepository.save(alert);
    }

    public record NavigationState(String tripId, int speedKmh, String nextTurn,
                                  double remainingKm, int etaMin, boolean arrived,
                                  double lat, double lon) {}
}
