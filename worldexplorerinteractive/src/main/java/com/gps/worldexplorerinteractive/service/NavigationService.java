package com.gps.worldexplorerinteractive.service;

import com.gps.worldexplorerinteractive.model.AlertEvent;
import com.gps.worldexplorerinteractive.model.RouteOption;
import com.gps.worldexplorerinteractive.model.Trip;
import com.gps.worldexplorerinteractive.repository.AlertEventRepository;
import com.gps.worldexplorerinteractive.repository.TripRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Random;

/**
 * Navegación en tiempo real y alertas de seguridad (RF-03, RF-04, RF-05).
 */
@Service
public class NavigationService {

    private final TripRepository tripRepository;
    private final AlertEventRepository alertEventRepository;
    private final Random random = new Random();

    public NavigationService(TripRepository tripRepository, AlertEventRepository alertEventRepository) {
        this.tripRepository = tripRepository;
        this.alertEventRepository = alertEventRepository;
    }

    public Trip startTrip(Trip trip) {
        trip.setStartedAt(Instant.now());
        trip.setStatus("ACTIVO");
        if (trip.getEvents() == null) trip.setEvents(new java.util.ArrayList<>());
        if (trip.getWeatherConditions() == null) trip.setWeatherConditions(new java.util.ArrayList<>());
        return tripRepository.save(trip);
    }

    /**
     * Simula un tick de navegación: posición, giro siguiente, distancia restante.
     * Si detecta velocidad excesiva o zona de riesgo, emite una alerta.
     */
    public NavigationState update(Trip trip, RouteOption route) {
        double remainingKm = Math.max(0.0, route.getDistanceKm() - (trip.getDistanceKm() > 0 ? trip.getDistanceKm() : 0));
        double speedKmh = 40 + random.nextDouble() * 60;
        String nextTurn = randomTurn();

        List<String> events = trip.getEvents() != null ? trip.getEvents() : new java.util.ArrayList<>();
        events.add("Avance a " + speedKmh + " km/h, próximo giro: " + nextTurn);

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
        trip.setDistanceKm(route.getDistanceKm() - remainingKm);
        tripRepository.save(trip);

        return new NavigationState(trip.getId(), (int) Math.round(speedKmh), nextTurn,
                Math.round(remainingKm * 10) / 10.0,
                remainingKm > 0 ? (int) Math.ceil(remainingKm / (speedKmh / 60.0)) : 0,
                remainingKm <= 0);
    }

    public Trip finishTrip(String tripId) {
        Trip trip = tripRepository.findById(tripId).orElseThrow();
        trip.setFinishedAt(Instant.now());
        trip.setStatus("COMPLETADO");
        return tripRepository.save(trip);
    }

    private void createAlert(String tripId, String type, String message) {
        AlertEvent alert = new AlertEvent(null, tripId, type, message, Instant.now(), false);
        alertEventRepository.save(alert);
    }

    private String randomTurn() {
        String[] turns = {"derecha", "izquierda", "recto", "retorno"};
        return turns[random.nextInt(turns.length)];
    }

    public record NavigationState(String tripId, int speedKmh, String nextTurn,
                                  double remainingKm, int etaMin, boolean arrived) {}
}
