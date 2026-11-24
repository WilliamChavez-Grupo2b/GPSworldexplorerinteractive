package com.gps.worldexplorerinteractive.service;

import com.gps.worldexplorerinteractive.model.AlertEvent;
import com.gps.worldexplorerinteractive.model.RouteOption;
import com.gps.worldexplorerinteractive.model.Trip;
import com.gps.worldexplorerinteractive.repository.AlertEventRepository;
import com.gps.worldexplorerinteractive.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NavigationServiceTest {

    @Test
    void updatingNavigationPreservesTheExistingTripFields() {
        TripRepository tripRepository = mock(TripRepository.class);
        AlertEventRepository alertRepository = mock(AlertEventRepository.class);
        GeoService geoService = mock(GeoService.class);
        Trip existing = existingTrip();
        when(tripRepository.findById("trip-1")).thenReturn(Optional.of(existing));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(geoService.interpolate(anyList(), anyDouble()))
                .thenReturn(new GeoService.GeoPoint(1.22, -77.29, "position"));
        NavigationService service = new NavigationService(tripRepository, alertRepository, geoService);

        service.update("trip-1", route());

        ArgumentCaptor<Trip> savedTrip = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(savedTrip.capture());
        assertEquals("Bogotá", savedTrip.getValue().getOrigin());
        assertEquals("Pasto", savedTrip.getValue().getDestination());
        assertEquals("Ruta rápida", savedTrip.getValue().getRouteName());
        assertEquals("ACTIVO", savedTrip.getValue().getStatus());
        assertEquals(Instant.parse("2026-11-24T10:00:00Z"), savedTrip.getValue().getStartedAt());
        assertEquals("Inicio registrado", savedTrip.getValue().getEvents().get(0));
    }

    @Test
    void finishingTripPersistsCompletionAndKeepsTripDetails() {
        TripRepository tripRepository = mock(TripRepository.class);
        AlertEventRepository alertRepository = mock(AlertEventRepository.class);
        GeoService geoService = mock(GeoService.class);
        Trip existing = existingTrip();
        when(tripRepository.findById("trip-1")).thenReturn(Optional.of(existing));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));
        NavigationService service = new NavigationService(tripRepository, alertRepository, geoService);

        Trip finished = service.finishTrip("trip-1");

        assertEquals("COMPLETADO", finished.getStatus());
        assertNotNull(finished.getFinishedAt());
        assertEquals("Bogotá", finished.getOrigin());
        assertEquals("Pasto", finished.getDestination());
        assertEquals("Ruta rápida", finished.getRouteName());
        assertEquals("Inicio registrado", finished.getEvents().get(0));
        verify(tripRepository).save(existing);
    }

    private Trip existingTrip() {
        Trip trip = new Trip();
        trip.setId("trip-1");
        trip.setUserId("user-1");
        trip.setOrigin("Bogotá");
        trip.setDestination("Pasto");
        trip.setRouteOptionId("route-1");
        trip.setRouteName("Ruta rápida");
        trip.setDistanceKm(4);
        trip.setDurationMin(300);
        trip.setStartedAt(Instant.parse("2026-11-24T10:00:00Z"));
        trip.setStatus("ACTIVO");
        trip.setEvents(new ArrayList<>(List.of("Inicio registrado")));
        trip.setWeatherConditions(new ArrayList<>(List.of("DESPEJADO")));
        return trip;
    }

    private RouteOption route() {
        RouteOption route = new RouteOption();
        route.setDistanceKm(100);
        route.setGeometry(List.of(List.of(-77.28, 1.21), List.of(-77.29, 1.22)));
        route.setStartLat(4.71);
        route.setStartLon(-74.07);
        route.setRiskLevel(0);
        return route;
    }
}
