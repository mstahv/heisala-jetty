package in.virit;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class SeaLevelReporter {

    private static final Logger LOG = Logger.getLogger(SeaLevelReporter.class);
    private static final String ENDPOINT = "https://caf.dokku1.parttio.org/api/sealevel";

    // Max age of measurement to consider "fresh"
    private static final long MAX_AGE_MS = 120_000;

    // Calibration (same as WaterLevelView)
    private static final int RADAR_CALIBRATION_DISTANCE_MM = 1175;
    private static final int ULTRASONIC_CALIBRATION_DISTANCE_MM = 1202;
    static final double CALIBRATION_LEVEL_CM = 1.0;

    // Hysteresis: once radar disagrees, require this many consecutive agreements
    // before trusting it again (same logic as WaterLevelView).
    private static final double SENSOR_AGREEMENT_THRESHOLD_CM = 5.0;
    private static final int RADAR_TRUST_COOLDOWN = 30;
    private static final long RADAR_TRUST_WINDOW_MS = 360_000; // 6 minutes of history

    @Inject
    WaterDistanceService service;

    @ConfigProperty(name = "sealevel.api.key")
    Optional<String> apiKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Scheduled(every = "1m")
    void reportSeaLevel() {
        if (apiKey.isEmpty() || apiKey.get().isBlank()) {
            LOG.info("Sea level reporting disabled: SEALEVEL_API_KEY not set");
            return;
        }

        var latest = service.getLatest();
        if (latest == null) {
            return;
        }

        long age = System.currentTimeMillis() - latest.epochMillis();
        if (age > MAX_AGE_MS) {
            LOG.infof("Skipping sea level report: latest measurement is %d s old", age / 1000);
            return;
        }

        // Use recent history to determine radar trust via hysteresis
        boolean radarTrusted = isRadarTrusted(service.getMeasurementsSince(
                Instant.ofEpochMilli(System.currentTimeMillis() - RADAR_TRUST_WINDOW_MS)));

        double levelCm;
        if (latest.hasRadar() && latest.hasUltrasonic()) {
            double radarLevel = CALIBRATION_LEVEL_CM + (RADAR_CALIBRATION_DISTANCE_MM - latest.radarMm()) / 10.0;
            double usLevel = CALIBRATION_LEVEL_CM + (ULTRASONIC_CALIBRATION_DISTANCE_MM - latest.ultrasonicMm()) / 10.0;
            levelCm = radarTrusted ? radarLevel : usLevel;
        } else if (latest.hasRadar()) {
            levelCm = CALIBRATION_LEVEL_CM + (RADAR_CALIBRATION_DISTANCE_MM - latest.radarMm()) / 10.0;
        } else if (latest.hasUltrasonic()) {
            levelCm = CALIBRATION_LEVEL_CM + (ULTRASONIC_CALIBRATION_DISTANCE_MM - latest.ultrasonicMm()) / 10.0;
        } else {
            return;
        }

        int levelMm = (int) Math.round(levelCm * 10);
        postSeaLevel(levelMm);
    }

    /**
     * Replays recent measurements to determine current radar trust state.
     * Radar starts trusted; any disagreement with ultrasonic resets trust,
     * which requires RADAR_TRUST_COOLDOWN consecutive agreements to restore.
     */
    private boolean isRadarTrusted(List<WaterDistanceService.Measurement> recent) {
        boolean trusted = true;
        int consecutiveAgreements = RADAR_TRUST_COOLDOWN;
        for (var m : recent) {
            if (m.hasRadar() && m.hasUltrasonic()) {
                double radarLevel = CALIBRATION_LEVEL_CM + (RADAR_CALIBRATION_DISTANCE_MM - m.radarMm()) / 10.0;
                double usLevel = CALIBRATION_LEVEL_CM + (ULTRASONIC_CALIBRATION_DISTANCE_MM - m.ultrasonicMm()) / 10.0;
                if (Math.abs(radarLevel - usLevel) > SENSOR_AGREEMENT_THRESHOLD_CM) {
                    trusted = false;
                    consecutiveAgreements = 0;
                } else {
                    consecutiveAgreements++;
                    if (consecutiveAgreements >= RADAR_TRUST_COOLDOWN) {
                        trusted = true;
                    }
                }
            }
        }
        return trusted;
    }

    private void postSeaLevel(int levelMm) {
        try {
            String body = "level=" + levelMm
                    + "&apiKey=" + URLEncoder.encode(apiKey.get(), StandardCharsets.UTF_8);

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                LOG.infof("Sea level reported: %d mm (HTTP %d)", levelMm, response.statusCode());
            } else {
                LOG.warnf("Sea level report failed: HTTP %d - %s", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            LOG.warnf("Sea level report failed: %s", e.getMessage());
        }
    }
}
