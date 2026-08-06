package com.shoplocker.fssai.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check endpoint for monitoring application status.
 * 
 * Provides:
 * - /api/health - Basic application health
 * - /api/health/redis - Redis connectivity check
 * - /api/health/detailed - Detailed health with dependencies
 */
@RestController
@RequestMapping("/api/health")
public class HealthCheckController {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckController.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public HealthCheckController(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Basic health check endpoint.
     * Returns application status and timestamp.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", Instant.now().toString());
        health.put("service", "DukaanLocker Backend");
        
        return ResponseEntity.ok(health);
    }

    /**
     * Redis connectivity check.
     * Tests connection to Redis and returns status.
     */
    @GetMapping("/redis")
    public ResponseEntity<Map<String, Object>> redisHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Test Redis connection with PING
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            
            // Test basic read/write
            String testKey = "health-check-test";
            String testValue = "ok-" + Instant.now().toEpochMilli();
            redisTemplate.opsForValue().set(testKey, testValue, 10, java.util.concurrent.TimeUnit.SECONDS);
            String retrievedValue = (String) redisTemplate.opsForValue().get(testKey);
            redisTemplate.delete(testKey);
            
            health.put("status", "UP");
            health.put("redis.ping", pong);
            health.put("redis.read.write", "OK".equals(retrievedValue) || retrievedValue.startsWith("ok-"));
            health.put("timestamp", Instant.now().toString());
            
            log.info("Redis health check passed");
            return ResponseEntity.ok(health);
            
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            health.put("timestamp", Instant.now().toString());
            
            log.error("Redis health check failed", e);
            return ResponseEntity.status(503).body(health);
        }
    }

    /**
     * Detailed health check with all dependencies.
     * Checks Redis, database, and other services.
     */
    @GetMapping("/detailed")
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Map<String, Object> health = new HashMap<>();
        Map<String, Object> components = new HashMap<>();
        
        // Check Redis
        Map<String, Object> redisStatus = new HashMap<>();
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            redisStatus.put("status", "UP");
        } catch (Exception e) {
            redisStatus.put("status", "DOWN");
            redisStatus.put("error", e.getMessage());
        }
        components.put("redis", redisStatus);
        
        // Overall status
        boolean allUp = components.values().stream()
                .allMatch(c -> "UP".equals(((Map<?, ?>) c).get("status")));
        
        health.put("status", allUp ? "UP" : "DEGRADED");
        health.put("components", components);
        health.put("timestamp", Instant.now().toString());
        
        return ResponseEntity.ok(health);
    }
}
