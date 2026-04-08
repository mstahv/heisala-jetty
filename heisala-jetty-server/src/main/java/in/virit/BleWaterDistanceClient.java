package in.virit;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.DiscoveryFilter;
import com.github.hypfvieh.bluetooth.DiscoveryTransport;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * BLE GATT client that connects to the ESP32 water level sensor.
 * The ESP32 runs a BLE GATT server with a characteristic containing
 * batched sensor readings in a compact binary format.
 *
 * Binary protocol (little-endian):
 *   [0]     sequence number (uint8, increments per batch)
 *   [1]     reading count N (uint8, 1-12)
 *   [2]     wifi_rssi (int8, 0 when using BLE)
 *   [3..]   N × 8-byte readings:
 *             [0-3] offset_ms (int32) — negative age relative to send time
 *             [4-5] radar_mm (int16)
 *             [6-7] ultrasonic_mm (int16)
 */
@ApplicationScoped
public class BleWaterDistanceClient {

    private static final Logger LOG = Logger.getLogger(BleWaterDistanceClient.class);

    static final String SERVICE_UUID = "a1e8f8d0-1d34-5678-abcd-0123456789ab";
    static final String DATA_CHAR_UUID = "a1e8f8d1-1d34-5678-abcd-0123456789ab";
    static final String DEVICE_NAME = "HeisalaWater";

    private static final int SCAN_DURATION_SEC = 10;
    private static final long POLL_INTERVAL_MS = 2_000;
    private static final long RECONNECT_DELAY_MS = 5_000;

    @Inject
    WaterDistanceService service;

    @ConfigProperty(name = "ble.water.enabled", defaultValue = "true")
    boolean bleEnabled;

    private volatile boolean running = true;
    private int lastSeqNum = -1;

    void onStart(@Observes StartupEvent ev) {
        if (!bleEnabled) {
            LOG.info("BLE water distance client disabled");
            return;
        }
        Thread t = new Thread(this::bleLoop, "ble-water-client");
        t.setDaemon(true);
        t.start();
    }

    void onStop(@Observes ShutdownEvent ev) {
        running = false;
    }

    private void bleLoop() {
        LOG.info("BLE water distance client starting...");
        try {
            DeviceManager manager = DeviceManager.createInstance(false);
            BluetoothAdapter adapter = manager.getAdapter();
            if (adapter == null) {
                LOG.warn("No Bluetooth adapter found, BLE water distance client disabled");
                return;
            }

            if (!adapter.isPowered()) {
                adapter.setPowered(true);
                LOG.info("Bluetooth adapter powered on");
            }

            // Filter for BLE devices only
            manager.setScanFilter(Map.of(
                    DiscoveryFilter.Transport, DiscoveryTransport.LE
            ));

            LOG.infof("BLE adapter: %s (%s)", adapter.getName(), adapter.getAddress());
            mainLoop(manager);
        } catch (Exception e) {
            LOG.warnf("BLE initialization failed (Bluetooth may not be available): %s", e.getMessage());
        }
    }

    private void mainLoop(DeviceManager manager) {
        while (running) {
            BluetoothDevice device = null;
            try {
                device = scanForDevice(manager);
                if (device == null) {
                    sleep(RECONNECT_DELAY_MS);
                    continue;
                }

                LOG.infof("Connecting to %s (%s)...", device.getName(), device.getAddress());
                device.connect();
                LOG.info("Connected to ESP32 BLE water sensor");

                // Give the device time to expose GATT services
                sleep(2000);

                BluetoothGattService gattService = device.getGattServiceByUuid(SERVICE_UUID);
                if (gattService == null) {
                    LOG.warn("GATT service not found on connected device");
                    device.disconnect();
                    sleep(RECONNECT_DELAY_MS);
                    continue;
                }

                BluetoothGattCharacteristic dataChar =
                        gattService.getGattCharacteristicByUuid(DATA_CHAR_UUID);
                if (dataChar == null) {
                    LOG.warn("Data characteristic not found in GATT service");
                    device.disconnect();
                    sleep(RECONNECT_DELAY_MS);
                    continue;
                }

                LOG.info("BLE GATT characteristic found, polling for data...");
                pollLoop(device, dataChar);

            } catch (Exception e) {
                LOG.debugf("BLE error: %s", e.getMessage());
            } finally {
                if (device != null) {
                    try { device.disconnect(); } catch (Exception ignored) {}
                }
            }
            sleep(RECONNECT_DELAY_MS);
        }
    }

    private BluetoothDevice scanForDevice(DeviceManager manager) {
        LOG.debug("Scanning for ESP32 BLE water sensor...");
        List<BluetoothDevice> devices;
        try {
            devices = manager.scanForBluetoothDevices(SCAN_DURATION_SEC);
        } catch (Exception e) {
            LOG.debugf("Scan error: %s", e.getMessage());
            devices = manager.getDevices();
        }

        for (BluetoothDevice device : devices) {
            String name = device.getName();
            if (DEVICE_NAME.equals(name)) {
                return device;
            }
            // Also check by advertised service UUIDs
            String[] uuids = device.getUuids();
            if (uuids != null) {
                for (String uuid : uuids) {
                    if (SERVICE_UUID.equalsIgnoreCase(uuid)) {
                        return device;
                    }
                }
            }
        }

        LOG.debug("ESP32 BLE device not found during scan");
        return null;
    }

    private void pollLoop(BluetoothDevice device, BluetoothGattCharacteristic dataChar) {
        while (running && Boolean.TRUE.equals(device.isConnected())) {
            try {
                byte[] value = dataChar.readValue(Collections.emptyMap());
                if (value != null && value.length >= 3) {
                    int seq = value[0] & 0xFF;
                    if (seq != lastSeqNum) {
                        lastSeqNum = seq;
                        processData(value);
                    }
                }
            } catch (Exception e) {
                LOG.debugf("BLE read error (device may have disconnected): %s", e.getMessage());
                break;
            }
            sleep(POLL_INTERVAL_MS);
        }
        LOG.info("BLE poll loop ended (device disconnected or shutting down)");
    }

    void processData(byte[] data) {
        if (data.length < 3) return;

        int count = data[1] & 0xFF;
        int rssi = data[2]; // signed byte

        int expectedLen = 3 + count * 8;
        if (data.length < expectedLen) {
            LOG.warnf("BLE data too short: expected %d bytes, got %d", expectedLen, data.length);
            return;
        }

        long now = System.currentTimeMillis();
        long batchId = service.nextBatchId();
        ByteBuffer buf = ByteBuffer.wrap(data, 3, count * 8).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < count; i++) {
            int offsetMs = buf.getInt();
            short radarMm = buf.getShort();
            short ultrasonicMm = buf.getShort();

            long epochMillis = now + offsetMs;
            service.addMeasurement(epochMillis, radarMm, ultrasonicMm, rssi, batchId,
                    WaterDistanceService.Transport.BLE);
        }

        LOG.infof("BLE: received %d readings (batchId=%d, seq=%d)", count, batchId, data[0] & 0xFF);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
