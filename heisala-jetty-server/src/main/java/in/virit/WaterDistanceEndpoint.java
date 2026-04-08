package in.virit;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

@jakarta.ws.rs.Path("/waterdistance")
public class WaterDistanceEndpoint {

    private static final Logger LOG = Logger.getLogger(WaterDistanceEndpoint.class);

    @Inject
    WaterDistanceService service;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response postMeasurement(String body) {
        try {
            if (body.contains("\"readings\"")) {
                return handleBatch(body);
            }
            int radarMm = parseIntField(body, "radar_mm");
            int ultrasonicMm = parseIntField(body, "ultrasonic_mm");
            int wifiRssi = body.contains("\"wifi_rssi\"") ? parseIntField(body, "wifi_rssi") : 0;
            service.addMeasurement(radarMm, ultrasonicMm, wifiRssi);
            return Response.ok("{\"status\":\"ok\"}").build();
        } catch (Exception e) {
            LOG.warnf("Invalid water distance payload: %s", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    private Response handleBatch(String body) {
        long now = System.currentTimeMillis();
        int wifiRssi = body.contains("\"wifi_rssi\"") ? parseIntField(body, "wifi_rssi") : 0;
        long batchId = service.nextBatchId();

        // Find the readings array
        int arrStart = body.indexOf('[');
        int arrEnd = body.lastIndexOf(']');
        if (arrStart < 0 || arrEnd < 0 || arrEnd <= arrStart) {
            throw new IllegalArgumentException("Missing readings array");
        }
        String arrBody = body.substring(arrStart + 1, arrEnd);

        int count = 0;
        int pos = 0;
        while (pos < arrBody.length()) {
            int objStart = arrBody.indexOf('{', pos);
            if (objStart < 0) break;
            int objEnd = arrBody.indexOf('}', objStart);
            if (objEnd < 0) break;

            String entry = arrBody.substring(objStart, objEnd + 1);
            long offsetMs = parseLongField(entry, "offset_ms");
            int radarMm = parseIntField(entry, "radar_mm");
            int ultrasonicMm = parseIntField(entry, "ultrasonic_mm");
            long epochMillis = now + offsetMs;

            service.addMeasurement(epochMillis, radarMm, ultrasonicMm, wifiRssi, batchId);
            count++;
            pos = objEnd + 1;
        }

        LOG.infof("Batch: received %d readings (batchId=%d)", count, batchId);
        return Response.ok("{\"status\":\"ok\",\"count\":" + count + "}").build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLatest() {
        var m = service.getLatest();
        if (m == null) {
            return Response.ok("{\"status\":\"no data\"}").build();
        }
        String json = "{\"epoch_millis\":" + m.epochMillis()
                + ",\"radar_mm\":" + m.radarMm()
                + ",\"ultrasonic_mm\":" + m.ultrasonicMm()
                + ",\"wifi_rssi\":" + m.wifiRssi() + "}";
        return Response.ok(json).build();
    }

    private static long parseLongField(String json, String field) {
        return Long.parseLong(parseRawNumberField(json, field));
    }

    private static int parseIntField(String json, String field) {
        return Integer.parseInt(parseRawNumberField(json, field));
    }

    private static String parseRawNumberField(String json, String field) {
        String key = "\"" + field + "\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx < 0) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
        int colonIdx = json.indexOf(':', keyIdx + key.length());
        if (colonIdx < 0) {
            throw new IllegalArgumentException("Malformed JSON for field: " + field);
        }
        int start = colonIdx + 1;
        while (start < json.length() && json.charAt(start) == ' ') {
            start++;
        }
        int end = start;
        if (end < json.length() && json.charAt(end) == '-') {
            end++;
        }
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (end == start || (end == start + 1 && json.charAt(start) == '-')) {
            throw new IllegalArgumentException("Invalid value for field: " + field);
        }
        return json.substring(start, end);
    }
}
