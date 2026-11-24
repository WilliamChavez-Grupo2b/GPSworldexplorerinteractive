package com.gps.worldexplorerinteractive.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "trips")
public class Trip {

    @Id
    private String id;
    private String userId;
    private String origin;
    private String destination;
    private String routeOptionId;
    private String routeName;
    private double distanceKm;
    private double durationMin;
    private Instant startedAt;
    private Instant finishedAt;
    private String status; // ACTIVO | COMPLETADO | CANCELADO
    private List<String> events;
    private List<String> weatherConditions;
}
