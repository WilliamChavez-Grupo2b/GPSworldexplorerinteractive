package com.gps.worldexplorerinteractive.repository;

import com.gps.worldexplorerinteractive.model.RouteOption;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RouteOptionRepository extends MongoRepository<RouteOption, String> {
    List<RouteOption> findByOriginAndDestination(String origin, String destination);
}
