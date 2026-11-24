package com.gps.worldexplorerinteractive.repository;

import com.gps.worldexplorerinteractive.model.AlertEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AlertEventRepository extends MongoRepository<AlertEvent, String> {
    List<AlertEvent> findByTripIdOrderByCreatedAtDesc(String tripId);
}
