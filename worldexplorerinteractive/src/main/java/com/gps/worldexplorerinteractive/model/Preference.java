package com.gps.worldexplorerinteractive.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "preferences")
public class Preference {

    @Id
    private String id;
    private String userId;
    private String language;       // es | en
    private String distanceUnit;   // km | mi
    private int voiceVolume;       // 0-100
    private String alertType;      // VISUAL | SONORA | AMBAS
    private Boolean avoidTolls;
    private Boolean preferSafeRoutes;
    private String theme;          // CLARO | OSCURO

    public boolean isAvoidTolls() { return Boolean.TRUE.equals(avoidTolls); }
    public boolean isPreferSafeRoutes() { return Boolean.TRUE.equals(preferSafeRoutes); }
}
