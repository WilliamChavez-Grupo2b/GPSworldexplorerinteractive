package com.gps.worldexplorerinteractive.repository;

import com.gps.worldexplorerinteractive.model.Trip;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TripRepository extends MongoRepository<Trip, String> {
    List<Trip> findByUserIdOrderByStartedAtDesc(String userId);
    List<Trip> findByStatus(String status);
}
