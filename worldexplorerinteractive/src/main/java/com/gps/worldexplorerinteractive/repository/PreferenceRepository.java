package com.gps.worldexplorerinteractive.repository;

import com.gps.worldexplorerinteractive.model.Preference;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PreferenceRepository extends MongoRepository<Preference, String> {
    Optional<Preference> findByUserId(String userId);
}
