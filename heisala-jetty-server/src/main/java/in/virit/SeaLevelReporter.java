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

        double levelCm;
        if (latest.hasRadar()) {
            levelCm = CALIBRATION_LEVEL_CM + (RADAR_CALIBRATION_DISTANCE_MM - latest.radarMm()) / 10.0;
        } else if (latest.hasUltrasonic()) {
            levelCm = CALIBRATION_LEVEL_CM + (ULTRASONIC_CALIBRATION_DISTANCE_MM - latest.ultrasonicMm()) / 10.0;
        } else {
            return;
        }

        int levelMm = (int) Math.round(levelCm * 10);
        postSeaLevel(levelMm);
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
