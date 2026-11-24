package com.gps.worldexplorerinteractive.controller;

import com.gps.worldexplorerinteractive.model.AlertEvent;
import com.gps.worldexplorerinteractive.model.Trip;
import com.gps.worldexplorerinteractive.repository.AlertEventRepository;
import com.gps.worldexplorerinteractive.repository.TripRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** RF-06: historial de viajes y eventos. */
@RestController
@RequestMapping("/api/history")
public class TripHistoryController {

    private final TripRepository tripRepository;
    private final AlertEventRepository alertEventRepository;

    public TripHistoryController(TripRepository tripRepository, AlertEventRepository alertEventRepository) {
        this.tripRepository = tripRepository;
        this.alertEventRepository = alertEventRepository;
    }

    @GetMapping("/trips")
    public List<Trip> all() {
        return tripRepository.findAll();
    }

    @GetMapping("/trips/{id}")
    public Trip byId(@PathVariable String id) {
        return tripRepository.findById(id).orElseThrow();
    }

    @GetMapping("/trips/{id}/alerts")
    public List<AlertEvent> alerts(@PathVariable String id) {
        return alertEventRepository.findByTripIdOrderByCreatedAtDesc(id);
    }
}
