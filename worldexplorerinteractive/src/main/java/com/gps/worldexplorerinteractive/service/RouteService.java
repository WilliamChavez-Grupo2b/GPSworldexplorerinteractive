package com.gps.worldexplorerinteractive.service;

import com.gps.worldexplorerinteractive.model.RouteOption;
import com.gps.worldexplorerinteractive.repository.RouteOptionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Servicio de planificación y optimización de rutas (RF-01, RF-02, RF-04).
 * Usa la API de geolocalización configurada para geocodificar origen/destino
 * y calcular rutas reales con geometría; puntúa según preferencias del conductor.
 */
@Service
public class RouteService {

    private final RouteOptionRepository routeOptionRepository;
    private final GeoService geoService;
    private final Random random = new Random();

    public RouteService(RouteOptionRepository routeOptionRepository, GeoService geoService) {
        this.routeOptionRepository = routeOptionRepository;
        this.geoService = geoService;
    }

    public List<RouteOption> planRoutes(String origin, String destination,
                                        boolean avoidTolls, boolean preferSafeRoutes) {
        return planRoutes(origin, destination, avoidTolls, preferSafeRoutes, null, null);
    }

    public List<RouteOption> planRoutes(String origin, String destination,
                                        boolean avoidTolls, boolean preferSafeRoutes,
                                        GeoService.GeoPoint start, GeoService.GeoPoint end) {
        if (start == null) {
            start = geoService.geocode(origin);
        }
        if (end == null) {
            end = geoService.geocode(destination);
        }

        List<RouteOption> options = generateCandidateRoutes(start, end, avoidTolls);
        for (RouteOption r : options) {
            r.setScore(computeScore(r, avoidTolls, preferSafeRoutes));
        }
        options.sort(Comparator.comparingDouble(RouteOption::getScore).reversed());
        return routeOptionRepository.saveAll(options);
    }

    private List<RouteOption> generateCandidateRoutes(GeoService.GeoPoint start, GeoService.GeoPoint end,
                                                       boolean avoidTolls) {
        String[] names = {"Ruta rápida", "Ruta segura", "Ruta panorámica"};
        String[] weathers = {"DESPEJADO", "LLUVIA", "NIEBLA", "NIEVE"};
        List<RouteOption> list = new ArrayList<>();

        // Ruta base real calculada por la API (distancia, duración y geometría)
        GeoService.GeoRoute base = geoService.directions(start, end, avoidTolls);

        for (int i = 0; i < 3; i++) {
            RouteOption route = new RouteOption();
            route.setOrigin(start.label());
            route.setDestination(end.label());
            route.setName(names[i]);

            // Variantes de la ruta base: atajo (-8%), panorámica (+15%)
            double factor = i == 0 ? 1.0 : (i == 1 ? 0.92 : 1.15);
            route.setDistanceKm(Math.round(base.distanceKm() * factor * 10) / 10.0);
            route.setDurationMin(Math.round(base.durationMin() * factor * (0.9 + random.nextDouble() * 0.4) * 10) / 10.0);
            route.setTrafficLevel(Math.round(random.nextDouble() * 100) / 100.0);
            route.setRiskLevel(Math.round(random.nextDouble() * 100) / 100.0);
            route.setWeather(weathers[random.nextInt(weathers.length)]);

            route.setGeometry(base.geometry());
            route.setStartLat(start.lat());
            route.setStartLon(start.lon());
            route.setEndLat(end.lat());
            route.setEndLon(end.lon());
            list.add(route);
        }
        return list;
    }

    /**
     * Puntaje: menor distancia, menos tráfico, menos riesgo y mejor clima = mayor puntaje.
     */
    private double computeScore(RouteOption r, boolean avoidTolls, boolean preferSafeRoutes) {
        double score = 100 - Math.min(80, r.getDistanceKm() / 2);
        score -= r.getTrafficLevel() * 30;
        score -= r.getRiskLevel() * (preferSafeRoutes ? 60 : 20);
        if ("LLUVIA".equals(r.getWeather())) score -= 10;
        if ("NIEBLA".equals(r.getWeather())) score -= 15;
        if ("NIEVE".equals(r.getWeather())) score -= 25;
        if (avoidTolls && r.getName().toLowerCase().contains("peaje")) score -= 40;
        return Math.round(score * 10) / 10.0;
    }
}
