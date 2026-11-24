package com.gps.worldexplorerinteractive.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "route_options")
public class RouteOption {

    @Id
    private String id;
    private String origin;
    private String destination;
    private String name;
    private double distanceKm;
    private double durationMin;
    private double trafficLevel;   // 0 = libre, 1 = saturado
    private double riskLevel;      // 0 = sin riesgo, 1 = riesgo alto
    private String weather;        // DESPEJADO | LLUVIA | NIEBLA | NIEVE
    private List<String> waypoints;
    private double score;          // puntaje calculado por el servicio de optimización
}
