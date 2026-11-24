package com.gps.worldexplorerinteractive.service;

import com.gps.worldexplorerinteractive.model.RouteOption;
import com.gps.worldexplorerinteractive.repository.RouteOptionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Servicio de planificación y optimización de rutas.
 * Genera rutas candidatas simuladas (tráfico/clima) y las puntúa
 * según las preferencias del conductor (RF-01, RF-02, RF-04).
 */
@Service
public class RouteService {

    private final RouteOptionRepository routeOptionRepository;

    public RouteService(RouteOptionRepository routeOptionRepository) {
        this.routeOptionRepository = routeOptionRepository;
    }

    public List<RouteOption> planRoutes(String origin, String destination,
                                        boolean avoidTolls, boolean preferSafeRoutes) {
        List<RouteOption> options = generateCandidateRoutes(origin, destination);
        for (RouteOption r : options) {
            r.setScore(computeScore(r, avoidTolls, preferSafeRoutes));
        }
        options.sort(Comparator.comparingDouble(RouteOption::getScore).reversed());
        return routeOptionRepository.saveAll(options);
    }

    private List<RouteOption> generateCandidateRoutes(String origin, String destination) {
        Random random = new Random();
        String[] names = {"Ruta rápida", "Ruta segura", "Ruta panorámica", "Atajo", "Ruta sin peajes"};
        String[] weathers = {"DESPEJADO", "LLUVIA", "NIEBLA", "NIEVE"};
        List<RouteOption> list = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double distance = 10 + random.nextDouble() * 50;
            double duration = distance * (0.9 + random.nextDouble() * 0.6);
            RouteOption route = new RouteOption();
            route.setOrigin(origin);
            route.setDestination(destination);
            route.setName(names[i % names.length]);
            route.setDistanceKm(Math.round(distance * 10) / 10.0);
            route.setDurationMin(Math.round(duration * 10) / 10.0);
            route.setTrafficLevel(Math.round(random.nextDouble() * 100) / 100.0);
            route.setRiskLevel(Math.round(random.nextDouble() * 100) / 100.0);
            route.setWeather(weathers[random.nextInt(weathers.length)]);
            list.add(route);
        }
        return list;
    }

    /**
     * Puntaje: menor distancia, menos tráfico, menos riesgo y mejor clima = mayor puntaje.
     */
    private double computeScore(RouteOption r, boolean avoidTolls, boolean preferSafeRoutes) {
        double score = 100 - r.getDistanceKm();
        score -= r.getTrafficLevel() * 30;
        score -= r.getRiskLevel() * (preferSafeRoutes ? 60 : 20);
        if ("LLUVIA".equals(r.getWeather())) score -= 10;
        if ("NIEBLA".equals(r.getWeather())) score -= 15;
        if ("NIEVE".equals(r.getWeather())) score -= 25;
        if (avoidTolls && r.getName().toLowerCase().contains("peaje")) score -= 40;
        return Math.round(score * 10) / 10.0;
    }
}
