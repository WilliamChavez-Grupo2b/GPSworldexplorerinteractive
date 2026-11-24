package com.gps.worldexplorerinteractive.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "alert_events")
public class AlertEvent {

    @Id
    private String id;
    private String tripId;
    private String type;    // VELOCIDAD | ZONA_RIESGO | CLIMA | DESVIO
    private String message;
    private Instant createdAt;
    private boolean resolved;
}
