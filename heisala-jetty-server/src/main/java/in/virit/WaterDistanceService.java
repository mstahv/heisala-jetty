package in.virit;

import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@ApplicationScoped
public class WaterDistanceService {

    private static final Logger LOG = Logger.getLogger(WaterDistanceService.class);
    private static final Path DATA_FILE = Path.of("water-distance-data.csv");
    private static final Path RAW_LOG_FILE = Path.of("water-distance-raw.csv");
    private static final int MAX_AGE_DAYS = 7;

    // Plausible distance range for the sensor installation (150mm - 5000mm).
    // Radar: LD2413 range is 150-10000mm, but values outside this window are noise.
    // Ultrasonic: JSN-SR04T reports 6016 when out of range, and <200 is sensor noise.
    private static final int MIN_PLAUSIBLE_MM = 200;
    private static final int MAX_PLAUSIBLE_MM = 5000;

    // Sea level doesn't jump more than ~50mm between 10-second readings.
    // Larger jumps indicate physical device movement (e.g. battery change, repositioning).
    // After enough consecutive rejections, accept the new level (device was permanently moved).
    private static final int MAX_CHANGE_MM = 50;
    private static final int RESET_AFTER_REJECTIONS = 6;

    public record Measurement(long epochMillis, int radarMm, int ultrasonicMm, int wifiRssi) {
        boolean hasRadar() { return radarMm > 0; }
        boolean hasUltrasonic() { return ultrasonicMm > 0; }
        boolean hasWifiRssi() { return wifiRssi != 0; }
    }

    public enum Transport { REST, BLE }

    /** Stores values exactly as received from the ESP32, before any filtering. */
    public record RawMeasurement(long epochMillis, int radarMm, int ultrasonicMm, int wifiRssi, long batchId, Transport transport) {}

    private final ArrayList<Measurement> measurements = new ArrayList<>();
    private final ArrayList<RawMeasurement> rawMeasurements = new ArrayList<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicLong batchIdCounter = new AtomicLong(1);

    // Sliding window median filter for radar — rejects sporadic outliers before
    // they reach the rate-of-change filter (which would accept after 6 rejections).
    private static final int MEDIAN_WINDOW = 5;
    private final int[] radarMedianWindow = new int[MEDIAN_WINDOW];
    private int radarMedianCount = 0;

    private int lastValidRadarMm = -1;
    private int lastValidUltrasonicMm = -1;
    private int consecutiveRadarRejections = 0;
    private int consecutiveUltrasonicRejections = 0;

    @PostConstruct
    void init() {
        loadFromCsv();
        LOG.infof("Loaded %d water distance measurements from CSV", measurements.size());
    }

    public long nextBatchId() {
        return batchIdCounter.getAndIncrement();
    }

    public void addMeasurement(int radarMm, int ultrasonicMm, int wifiRssi) {
        addMeasurement(System.currentTimeMillis(), radarMm, ultrasonicMm, wifiRssi, nextBatchId(), Transport.REST);
    }

    public void addMeasurement(long epochMillis, int radarMm, int ultrasonicMm, int wifiRssi) {
        addMeasurement(epochMillis, radarMm, ultrasonicMm, wifiRssi, nextBatchId(), Transport.REST);
    }

    public void addMeasurement(long epochMillis, int radarMm, int ultrasonicMm, int wifiRssi, long batchId) {
        addMeasurement(epochMillis, radarMm, ultrasonicMm, wifiRssi, batchId, Transport.REST);
    }

    public void addMeasurement(long epochMillis, int radarMm, int ultrasonicMm, int wifiRssi, long batchId, Transport transport) {
        // Store unfiltered data first for debugging
        var raw = new RawMeasurement(epochMillis, radarMm, ultrasonicMm, wifiRssi, batchId, transport);

        int filteredRadar = isPlausible(radarMm) ? radarMm : -1;
        int filteredUltrasonic = isPlausible(ultrasonicMm) ? ultrasonicMm : -1;

        filteredRadar = medianFilterRadar(filteredRadar);
        filteredRadar = applyRateFilter(filteredRadar, true);
        filteredUltrasonic = applyRateFilter(filteredUltrasonic, false);

        var m = new Measurement(epochMillis, filteredRadar, filteredUltrasonic, wifiRssi);
        lock.writeLock().lock();
        try {
            rawMeasurements.add(raw);
            measurements.add(m);
        } finally {
            lock.writeLock().unlock();
        }
        appendRawLog(raw);
    }

    private void appendRawLog(RawMeasurement raw) {
        try {
            String line = raw.epochMillis() + "," + raw.radarMm() + "," + raw.ultrasonicMm()
                    + "," + raw.wifiRssi() + "," + raw.batchId() + "," + raw.transport().name() + "\n";
            Files.writeString(RAW_LOG_FILE, line,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.error("Failed to append raw measurement to log", e);
        }
    }

    /**
     * Sliding-window median filter for radar. Only plausible values enter the
     * window; the median of the window is returned. This rejects up to
     * (MEDIAN_WINDOW/2) sporadic outliers per window.
     */
    private int medianFilterRadar(int valueMm) {
        if (valueMm < 0) return -1;

        // Shift window and append new value
        if (radarMedianCount < MEDIAN_WINDOW) {
            radarMedianWindow[radarMedianCount++] = valueMm;
        } else {
            System.arraycopy(radarMedianWindow, 1, radarMedianWindow, 0, MEDIAN_WINDOW - 1);
            radarMedianWindow[MEDIAN_WINDOW - 1] = valueMm;
        }

        int[] sorted = Arrays.copyOf(radarMedianWindow, radarMedianCount);
        Arrays.sort(sorted);
        return sorted[radarMedianCount / 2];
    }

    private int applyRateFilter(int valueMm, boolean radar) {
        if (valueMm < 0) return -1;

        int lastValid = radar ? lastValidRadarMm : lastValidUltrasonicMm;
        if (lastValid < 0) {
            // First valid reading — accept it
            if (radar) lastValidRadarMm = valueMm;
            else lastValidUltrasonicMm = valueMm;
            return valueMm;
        }

        if (Math.abs(valueMm - lastValid) > MAX_CHANGE_MM) {
            // Too large a jump — reject unless we've rejected too many in a row
            int rejections = radar ? ++consecutiveRadarRejections : ++consecutiveUltrasonicRejections;
            if (rejections >= RESET_AFTER_REJECTIONS) {
                // Device was probably moved permanently — accept new level
                if (radar) { lastValidRadarMm = valueMm; consecutiveRadarRejections = 0; }
                else { lastValidUltrasonicMm = valueMm; consecutiveUltrasonicRejections = 0; }
                return valueMm;
            }
            return -1;
        }

        // Normal reading — accept and reset rejection counter
        if (radar) { lastValidRadarMm = valueMm; consecutiveRadarRejections = 0; }
        else { lastValidUltrasonicMm = valueMm; consecutiveUltrasonicRejections = 0; }
        return valueMm;
    }

    private static boolean isPlausible(int valueMm) {
        return valueMm >= MIN_PLAUSIBLE_MM && valueMm <= MAX_PLAUSIBLE_MM;
    }

    public Measurement getLatest() {
        lock.readLock().lock();
        try {
            return measurements.isEmpty() ? null : measurements.getLast();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Measurement> getMeasurementsSince(Instant since) {
        long sinceMillis = since.toEpochMilli();
        lock.readLock().lock();
        try {
            // Binary search for start index since data is chronologically ordered
            int lo = 0, hi = measurements.size();
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (measurements.get(mid).epochMillis() < sinceMillis) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            return new ArrayList<>(measurements.subList(lo, measurements.size()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public RawMeasurement getLatestRaw() {
        lock.readLock().lock();
        try {
            return rawMeasurements.isEmpty() ? null : rawMeasurements.getLast();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<RawMeasurement> getRawMeasurementsByBatch(long batchId) {
        lock.readLock().lock();
        try {
            return rawMeasurements.stream()
                    .filter(m -> m.batchId() == batchId)
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<RawMeasurement> getRawMeasurementsSince(Instant since) {
        long sinceMillis = since.toEpochMilli();
        lock.readLock().lock();
        try {
            int lo = 0, hi = rawMeasurements.size();
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (rawMeasurements.get(mid).epochMillis() < sinceMillis) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            return new ArrayList<>(rawMeasurements.subList(lo, rawMeasurements.size()));
        } finally {
            lock.readLock().unlock();
        }
    }

    private static final long EXPECTED_INTERVAL_MS = 10_000; // ESP32 sends every 10s

    /**
     * Estimates missed bursts by counting gaps longer than 1.5x the expected interval.
     */
    public int estimateMissedBursts(Instant since) {
        List<Measurement> data = getMeasurementsSince(since);
        if (data.size() < 2) return 0;
        int missed = 0;
        for (int i = 1; i < data.size(); i++) {
            long gap = data.get(i).epochMillis() - data.get(i - 1).epochMillis();
            if (gap > EXPECTED_INTERVAL_MS * 3 / 2) {
                missed += (int) ((gap / EXPECTED_INTERVAL_MS) - 1);
            }
        }
        return missed;
    }

    /**
     * Returns total expected bursts for a time period vs actual received.
     */
    public int countMeasurementsSince(Instant since) {
        return getMeasurementsSince(since).size();
    }

    /**
     * Downsamples data to at most maxPoints by picking evenly spaced entries.
     */
    public static List<Measurement> getDownsampled(List<Measurement> data, int maxPoints) {
        if (data.size() <= maxPoints) {
            return data;
        }
        List<Measurement> result = new ArrayList<>(maxPoints);
        double step = (double) (data.size() - 1) / (maxPoints - 1);
        for (int i = 0; i < maxPoints; i++) {
            result.add(data.get((int) Math.round(i * step)));
        }
        return result;
    }

    @PreDestroy
    void shutdown() {
        persistToCsv();
    }

    @Scheduled(every = "1h")
    void persistToCsv() {
        List<Measurement> snapshot;
        lock.readLock().lock();
        try {
            snapshot = new ArrayList<>(measurements);
        } finally {
            lock.readLock().unlock();
        }

        try (BufferedWriter writer = Files.newBufferedWriter(DATA_FILE)) {
            for (Measurement m : snapshot) {
                writer.write(m.epochMillis() + "," + m.radarMm() + "," + m.ultrasonicMm() + "," + m.wifiRssi());
                writer.newLine();
            }
            LOG.debugf("Persisted %d measurements to CSV", snapshot.size());
        } catch (IOException e) {
            LOG.error("Failed to persist water distance data", e);
        }
    }

    @Scheduled(cron = "0 0 4 * * ?")
    void pruneOldEntries() {
        long cutoff = Instant.now().minus(MAX_AGE_DAYS, ChronoUnit.DAYS).toEpochMilli();
        long rawCutoff = Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli();
        lock.writeLock().lock();
        try {
            int before = measurements.size();
            measurements.removeIf(m -> m.epochMillis() < cutoff);
            int removed = before - measurements.size();
            if (removed > 0) {
                LOG.infof("Pruned %d old water distance entries", removed);
            }

            int rawBefore = rawMeasurements.size();
            rawMeasurements.removeIf(m -> m.epochMillis() < rawCutoff);
            int rawRemoved = rawBefore - rawMeasurements.size();
            if (rawRemoved > 0) {
                LOG.infof("Pruned %d old raw measurement entries", rawRemoved);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void loadFromCsv() {
        if (!Files.exists(DATA_FILE)) {
            return;
        }
        long rawCutoff = Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli();
        try (BufferedReader reader = Files.newBufferedReader(DATA_FILE)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    long epochMillis = Long.parseLong(parts[0].trim());
                    int radar = Integer.parseInt(parts[1].trim());
                    int ultrasonic = Integer.parseInt(parts[2].trim());
                    int rssi = parts.length >= 4 ? Integer.parseInt(parts[3].trim()) : 0;

                    // Populate raw store from last 24h of CSV data
                    if (epochMillis >= rawCutoff) {
                        rawMeasurements.add(new RawMeasurement(epochMillis, radar, ultrasonic, rssi, 0, Transport.REST));
                    }

                    int filteredRadar = isPlausible(radar) ? radar : -1;
                    int filteredUltrasonic = isPlausible(ultrasonic) ? ultrasonic : -1;
                    filteredRadar = medianFilterRadar(filteredRadar);
                    filteredRadar = applyRateFilter(filteredRadar, true);
                    filteredUltrasonic = applyRateFilter(filteredUltrasonic, false);
                    if (filteredRadar > 0 || filteredUltrasonic > 0) {
                        measurements.add(new Measurement(
                                epochMillis, filteredRadar, filteredUltrasonic, rssi));
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            LOG.error("Failed to load water distance data from CSV", e);
        }
    }
}
