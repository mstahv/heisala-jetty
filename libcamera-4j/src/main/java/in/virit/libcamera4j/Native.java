package in.virit.libcamera4j;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Foreign Function &amp; Memory (Project Panama) bindings to the {@code libcamera4j}
 * C-ABI shim (see {@code src/main/native/libcamera4j.h}).
 *
 * <p>This is the single boundary between Java and native code: every former JNI
 * {@code native} method is now a typed wrapper that invokes a cached
 * {@link MethodHandle} into the shared library. Strings and small fixed arrays
 * are marshalled through short-lived confined {@link Arena}s.</p>
 *
 * <p>Requires {@code --enable-native-access}.</p>
 */
final class Native {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = NativeLoader.lookup();

    private Native() {
    }

    private static MethodHandle h(String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = LOOKUP.find(name)
                .orElseThrow(() -> new UnsatisfiedLinkError("Missing native symbol: " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    private static RuntimeException wrap(Throwable t) {
        if (t instanceof Error e) {
            throw e;
        }
        if (t instanceof RuntimeException re) {
            return re;
        }
        return new LibCameraException("Native call failed", t);
    }

    // ---- CameraManager ----
    private static final MethodHandle CM_CREATE = h("lc4j_cm_create", FunctionDescriptor.of(JAVA_LONG));
    private static final MethodHandle CM_DESTROY = h("lc4j_cm_destroy", FunctionDescriptor.ofVoid(JAVA_LONG));
    private static final MethodHandle CM_START = h("lc4j_cm_start", FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
    private static final MethodHandle CM_STOP = h("lc4j_cm_stop", FunctionDescriptor.ofVoid(JAVA_LONG));
    private static final MethodHandle CM_CAMERA_COUNT = h("lc4j_cm_camera_count", FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
    private static final MethodHandle CM_CAMERA_ID = h("lc4j_cm_camera_id", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle CM_GET_CAMERA = h("lc4j_cm_get_camera", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, ADDRESS));
    private static final MethodHandle CM_VERSION = h("lc4j_cm_version", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    static long cmCreate() {
        try {
            return (long) CM_CREATE.invokeExact();
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static void cmDestroy(long handle) {
        try {
            CM_DESTROY.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static int cmStart(long handle) {
        try {
            return (int) CM_START.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static void cmStop(long handle) {
        try {
            CM_STOP.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static int cmCameraCount(long handle) {
        try {
            return (int) CM_CAMERA_COUNT.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static String cmCameraId(long handle, int index) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocate(512);
            int n = (int) CM_CAMERA_ID.invokeExact(handle, index, buf, 512);
            return n < 0 ? null : buf.getString(0);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static long cmGetCamera(long handle, String cameraId) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment id = arena.allocateFrom(cameraId);
            return (long) CM_GET_CAMERA.invokeExact(handle, id);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static String cmVersion() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocate(256);
            int n = (int) CM_VERSION.invokeExact(buf, 256);
            return n < 0 ? "" : buf.getString(0);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    // ---- Camera ----
    private static final MethodHandle CAM_ACQUIRE = h("lc4j_cam_acquire", FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
    private static final MethodHandle CAM_RELEASE = h("lc4j_cam_release", FunctionDescriptor.ofVoid(JAVA_LONG));
    private static final MethodHandle CAM_GEN_CONFIG = h("lc4j_cam_generate_configuration", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_INT));
    private static final MethodHandle CAM_CONFIGURE = h("lc4j_cam_configure", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG));
    private static final MethodHandle CAM_CREATE_REQUEST = h("lc4j_cam_create_request", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG));
    private static final MethodHandle CAM_QUEUE_REQUEST = h("lc4j_cam_queue_request", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG));
    private static final MethodHandle CAM_START = h("lc4j_cam_start", FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
    private static final MethodHandle CAM_STOP = h("lc4j_cam_stop", FunctionDescriptor.ofVoid(JAVA_LONG));
    private static final MethodHandle CAM_POLL = h("lc4j_cam_poll_completed_request", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));

    static int camAcquire(long handle) {
        try {
            return (int) CAM_ACQUIRE.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static void camRelease(long handle) {
        try {
            CAM_RELEASE.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static long camGenerateConfiguration(long handle, int[] roles) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = roles.length == 0
                    ? MemorySegment.NULL
                    : arena.allocate(JAVA_INT, roles.length);
            if (roles.length != 0) {
                MemorySegment.copy(roles, 0, seg, JAVA_INT, 0, roles.length);
            }
            return (long) CAM_GEN_CONFIG.invokeExact(handle, seg, roles.length);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static int camConfigure(long handle, long configHandle) {
        try {
            return (int) CAM_CONFIGURE.invokeExact(handle, configHandle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static long camCreateRequest(long handle, long cookie) {
        try {
            return (long) CAM_CREATE_REQUEST.invokeExact(handle, cookie);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static int camQueueRequest(long handle, long requestHandle) {
        try {
            return (int) CAM_QUEUE_REQUEST.invokeExact(handle, requestHandle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static int camStart(long handle) {
        try {
            return (int) CAM_START.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static void camStop(long handle) {
        try {
            CAM_STOP.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static long camPollCompletedRequest(long handle) {
        try {
            return (long) CAM_POLL.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    // ---- CameraConfiguration ----
    private static final MethodHandle CFG_DESTROY = h("lc4j_config_destroy", FunctionDescriptor.ofVoid(JAVA_LONG));
    private static final MethodHandle CFG_SIZE = h("lc4j_config_size", FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
    private static final MethodHandle CFG_VALIDATE = h("lc4j_config_validate", FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
    private static final MethodHandle CFG_GET_WIDTH = h("lc4j_config_get_width", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT));
    private static final MethodHandle CFG_GET_HEIGHT = h("lc4j_config_get_height", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT));
    private static final MethodHandle CFG_GET_STRIDE = h("lc4j_config_get_stride", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT));
    private static final MethodHandle CFG_GET_PIXFMT = h("lc4j_config_get_pixel_format", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT));
    private static final MethodHandle CFG_SET_SIZE = h("lc4j_config_set_size", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT, JAVA_INT, JAVA_INT));
    private static final MethodHandle CFG_SET_PIXFMT = h("lc4j_config_set_pixel_format", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT, JAVA_INT));
    private static final MethodHandle CFG_SET_BUFCOUNT = h("lc4j_config_set_buffer_count", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT, JAVA_INT));

    static void configDestroy(long handle) {
        try {
            CFG_DESTROY.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static int configSize(long handle) {
        try {
            return (int) CFG_SIZE.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static int configValidate(long handle) {
        try {
            return (int) CFG_VALIDATE.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static int configGetWidth(long handle, int index) {
        try {
            return (int) CFG_GET_WIDTH.invokeExact(handle, index);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static int configGetHeight(long handle, int index) {
        try {
            return (int) CFG_GET_HEIGHT.invokeExact(handle, index);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static int configGetStride(long handle, int index) {
        try {
            return (int) CFG_GET_STRIDE.invokeExact(handle, index);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static int configGetPixelFormat(long handle, int index) {
        try {
            return (int) CFG_GET_PIXFMT.invokeExact(handle, index);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static void configSetSize(long handle, int index, int width, int height) {
        try {
            CFG_SET_SIZE.invokeExact(handle, index, width, height);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static void configSetPixelFormat(long handle, int index, int fourcc) {
        try {
            CFG_SET_PIXFMT.invokeExact(handle, index, fourcc);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static void configSetBufferCount(long handle, int index, int count) {
        try {
            CFG_SET_BUFCOUNT.invokeExact(handle, index, count);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    // ---- FrameBufferAllocator ----
    private static final MethodHandle ALLOC_CREATE = h("lc4j_alloc_create", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));
    private static final MethodHandle ALLOC_DESTROY = h("lc4j_alloc_destroy", FunctionDescriptor.ofVoid(JAVA_LONG));
    private static final MethodHandle ALLOC_ALLOCATE = h("lc4j_alloc_allocate", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_INT));

    static long allocCreate(long cameraHandle) {
        try {
            return (long) ALLOC_CREATE.invokeExact(cameraHandle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static void allocDestroy(long handle) {
        try {
            ALLOC_DESTROY.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static int allocAllocate(long handle, long configHandle, int streamIndex) {
        try {
            return (int) ALLOC_ALLOCATE.invokeExact(handle, configHandle, streamIndex);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    // ---- Request ----
    private static final MethodHandle REQ_DESTROY = h("lc4j_req_destroy", FunctionDescriptor.ofVoid(JAVA_LONG));
    private static final MethodHandle REQ_ADD_BUFFER = h("lc4j_req_add_buffer", FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_INT));
    private static final MethodHandle REQ_REUSE = h("lc4j_req_reuse", FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
    private static final MethodHandle REQ_STATUS = h("lc4j_req_status", FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
    private static final MethodHandle REQ_GET_TIMESTAMP = h("lc4j_req_get_timestamp", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_INT));
    private static final MethodHandle REQ_GET_SEQUENCE = h("lc4j_req_get_sequence", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_INT));
    private static final MethodHandle REQ_GET_EXPOSURE = h("lc4j_req_get_exposure_time", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));
    private static final MethodHandle REQ_GET_AGAIN = h("lc4j_req_get_analogue_gain", FunctionDescriptor.of(JAVA_DOUBLE, JAVA_LONG));
    private static final MethodHandle REQ_GET_DGAIN = h("lc4j_req_get_digital_gain", FunctionDescriptor.of(JAVA_DOUBLE, JAVA_LONG));
    private static final MethodHandle REQ_GET_CGAINS = h("lc4j_req_get_colour_gains", FunctionDescriptor.ofVoid(JAVA_LONG, ADDRESS));
    private static final MethodHandle REQ_GET_CTEMP = h("lc4j_req_get_colour_temperature", FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
    private static final MethodHandle REQ_GET_LUX = h("lc4j_req_get_lux", FunctionDescriptor.of(JAVA_DOUBLE, JAVA_LONG));
    private static final MethodHandle REQ_GET_BLACK = h("lc4j_req_get_sensor_black_levels", FunctionDescriptor.ofVoid(JAVA_LONG, ADDRESS));
    private static final MethodHandle REQ_GET_CCM = h("lc4j_req_get_colour_correction_matrix", FunctionDescriptor.ofVoid(JAVA_LONG, ADDRESS));
    private static final MethodHandle REQ_SET_AFMODE = h("lc4j_req_set_af_mode", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT));
    private static final MethodHandle REQ_SET_LENS = h("lc4j_req_set_lens_position", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_FLOAT));
    private static final MethodHandle REQ_SET_AE = h("lc4j_req_set_ae_enable", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT));
    private static final MethodHandle REQ_SET_EXPOSURE = h("lc4j_req_set_exposure_time", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT));
    private static final MethodHandle REQ_SET_AGAIN = h("lc4j_req_set_analogue_gain", FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_FLOAT));

    static void reqDestroy(long handle) {
        try {
            REQ_DESTROY.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static int reqAddBuffer(long handle, long configHandle, int streamIndex, long allocatorHandle, int bufferIndex) {
        try {
            return (int) REQ_ADD_BUFFER.invokeExact(handle, configHandle, streamIndex, allocatorHandle, bufferIndex);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static int reqReuse(long handle) {
        try {
            return (int) REQ_REUSE.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static int reqStatus(long handle) {
        try {
            return (int) REQ_STATUS.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static long reqGetTimestamp(long handle, long configHandle, int streamIndex, long allocatorHandle, int bufferIndex) {
        try {
            return (long) REQ_GET_TIMESTAMP.invokeExact(handle, configHandle, streamIndex, allocatorHandle, bufferIndex);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static long reqGetSequence(long handle, long configHandle, int streamIndex, long allocatorHandle, int bufferIndex) {
        try {
            return (long) REQ_GET_SEQUENCE.invokeExact(handle, configHandle, streamIndex, allocatorHandle, bufferIndex);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static long reqGetExposureTime(long handle) {
        try {
            return (long) REQ_GET_EXPOSURE.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static double reqGetAnalogueGain(long handle) {
        try {
            return (double) REQ_GET_AGAIN.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static double reqGetDigitalGain(long handle) {
        try {
            return (double) REQ_GET_DGAIN.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static double[] reqGetColourGains(long handle) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(JAVA_DOUBLE, 2);
            REQ_GET_CGAINS.invokeExact(handle, out);
            double[] result = new double[2];
            MemorySegment.copy(out, JAVA_DOUBLE, 0, result, 0, 2);
            return result;
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static int reqGetColourTemperature(long handle) {
        try {
            return (int) REQ_GET_CTEMP.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static double reqGetLux(long handle) {
        try {
            return (double) REQ_GET_LUX.invokeExact(handle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static int[] reqGetSensorBlackLevels(long handle) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(JAVA_INT, 4);
            REQ_GET_BLACK.invokeExact(handle, out);
            int[] result = new int[4];
            MemorySegment.copy(out, JAVA_INT, 0, result, 0, 4);
            return result;
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static double[] reqGetColourCorrectionMatrix(long handle) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(JAVA_DOUBLE, 9);
            REQ_GET_CCM.invokeExact(handle, out);
            double[] result = new double[9];
            MemorySegment.copy(out, JAVA_DOUBLE, 0, result, 0, 9);
            return result;
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static void reqSetAfMode(long handle, int mode) {
        try {
            REQ_SET_AFMODE.invokeExact(handle, mode);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static void reqSetLensPosition(long handle, float position) {
        try {
            REQ_SET_LENS.invokeExact(handle, position);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static void reqSetAeEnable(long handle, boolean enable) {
        try {
            REQ_SET_AE.invokeExact(handle, enable ? 1 : 0);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static void reqSetExposureTime(long handle, int microseconds) {
        try {
            REQ_SET_EXPOSURE.invokeExact(handle, microseconds);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static void reqSetAnalogueGain(long handle, float gain) {
        try {
            REQ_SET_AGAIN.invokeExact(handle, gain);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    // ---- FrameBuffer ----
    private static final MethodHandle FB_MAP = h("lc4j_fb_map", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_INT, JAVA_INT));
    private static final MethodHandle FB_UNMAP = h("lc4j_fb_unmap", FunctionDescriptor.ofVoid(JAVA_LONG));
    private static final MethodHandle FB_SIZE = h("lc4j_fb_size", FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
    private static final MethodHandle FB_PLANE_COUNT = h("lc4j_fb_plane_count", FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
    private static final MethodHandle FB_PLANE_ADDRESS = h("lc4j_fb_plane_address", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_INT));
    private static final MethodHandle FB_PLANE_LENGTH = h("lc4j_fb_plane_length", FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_INT));

    static long fbMap(long allocatorHandle, long configHandle, int streamIndex, int bufferIndex) {
        try {
            return (long) FB_MAP.invokeExact(allocatorHandle, configHandle, streamIndex, bufferIndex);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static void fbUnmap(long mapHandle) {
        try {
            FB_UNMAP.invokeExact(mapHandle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static int fbSize(long mapHandle) {
        try {
            return (int) FB_SIZE.invokeExact(mapHandle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static int fbPlaneCount(long mapHandle) {
        try {
            return (int) FB_PLANE_COUNT.invokeExact(mapHandle);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static long fbPlaneAddress(long mapHandle, int planeIndex) {
        try {
            return (long) FB_PLANE_ADDRESS.invokeExact(mapHandle, planeIndex);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }

    static long fbPlaneLength(long mapHandle, int planeIndex) {
        try {
            return (long) FB_PLANE_LENGTH.invokeExact(mapHandle, planeIndex);
        } catch (Throwable t) {
            throw wrap(t);
        }
    }
}
