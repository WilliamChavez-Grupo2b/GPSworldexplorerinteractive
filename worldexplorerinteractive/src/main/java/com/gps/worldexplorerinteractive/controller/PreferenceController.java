package com.gps.worldexplorerinteractive.controller;

import com.gps.worldexplorerinteractive.model.Preference;
import com.gps.worldexplorerinteractive.model.User;
import com.gps.worldexplorerinteractive.repository.PreferenceRepository;
import com.gps.worldexplorerinteractive.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** RF-07: preferencias del usuario. RF-11: autenticación básica. */
@RestController
@RequestMapping("/api/preferences")
public class PreferenceController {

    private final PreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PreferenceController(PreferenceRepository preferenceRepository,
                                UserRepository userRepository,
                                PasswordEncoder passwordEncoder) {
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<Preference> get(@PathVariable String userId) {
        return preferenceRepository.findByUserId(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{userId}")
    public Preference save(@PathVariable String userId, @RequestBody Preference preference) {
        preference.setUserId(userId);
        return preferenceRepository.save(preference);
    }

    /** Registro simple de usuario (RF-11). */
    @PostMapping("/register")
    public User register(@RequestBody Map<String, String> body) {
        User user = new User();
        user.setUsername(body.get("username"));
        user.setPassword(passwordEncoder.encode(body.get("password")));
        user.setFullName(body.getOrDefault("fullName", body.get("username")));
        user.setRole(body.getOrDefault("role", "CONDUCTOR"));
        return userRepository.save(user);
    }
}
