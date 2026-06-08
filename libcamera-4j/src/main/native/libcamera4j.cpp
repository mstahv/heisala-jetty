/*
 * libcamera4j - C ABI shim for libcamera, bound from Java via the
 * Foreign Function & Memory API (Project Panama).
 *
 * libcamera is a C++ library; this shim flattens its C++ API into a handle-based
 * C ABI (see libcamera4j.h) with no JNI dependency. The libcamera logic, handle
 * maps, mmap buffer access and completion polling are unchanged from the original
 * JNI implementation — only the language boundary differs.
 */

#include "libcamera4j.h"

#include <libcamera/libcamera.h>
#include <libcamera/control_ids.h>

#include <memory>
#include <vector>
#include <map>
#include <mutex>
#include <queue>
#include <cstring>
#include <sys/mman.h>
#include <unistd.h>
#include <ctime>
#include <cmath>
#include <algorithm>
#include <cerrno>
#include <cstdio>

using namespace libcamera;

// -----------------------------------------------------------------------------
// Native handle management
// -----------------------------------------------------------------------------

// Use recursive_mutex since allocHandle() is called while holding the lock
static std::recursive_mutex g_mutex;
static std::map<int64_t, std::shared_ptr<CameraManager>> g_cameraManagers;
static std::map<int64_t, std::shared_ptr<Camera>> g_cameras;
static std::map<int64_t, std::unique_ptr<CameraConfiguration>> g_configurations;
static std::map<int64_t, std::unique_ptr<FrameBufferAllocator>> g_allocators;
static std::map<int64_t, std::unique_ptr<Request>> g_requests;
static int64_t g_nextHandle = 1;

// Request completion queue per camera
static std::map<int64_t, std::queue<Request*>> g_completedRequests;

static int64_t allocHandle() {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    return g_nextHandle++;
}

// Copy a std::string into a caller-provided buffer. Returns bytes written
// (excluding NUL), or -1 if the buffer is too small / invalid.
static int32_t copyString(const std::string& str, char* buf, int32_t buflen) {
    if (buf == nullptr || buflen <= 0) {
        return -1;
    }
    if ((size_t)buflen <= str.size()) {
        return -1;
    }
    std::memcpy(buf, str.c_str(), str.size());
    buf[str.size()] = '\0';
    return static_cast<int32_t>(str.size());
}

// -----------------------------------------------------------------------------
// CameraManager
// -----------------------------------------------------------------------------

extern "C" {

int64_t lc4j_cm_create(void) {
    try {
        auto cm = std::make_shared<CameraManager>();
        int64_t handle = allocHandle();
        std::lock_guard<std::recursive_mutex> lock(g_mutex);
        g_cameraManagers[handle] = cm;
        return handle;
    } catch (const std::exception&) {
        return 0;
    }
}

void lc4j_cm_destroy(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    g_cameraManagers.erase(handle);
}

int32_t lc4j_cm_start(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameraManagers.find(handle);
    if (it == g_cameraManagers.end()) {
        return -1;
    }
    return it->second->start();
}

void lc4j_cm_stop(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameraManagers.find(handle);
    if (it != g_cameraManagers.end()) {
        it->second->stop();
    }
}

int32_t lc4j_cm_camera_count(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameraManagers.find(handle);
    if (it == g_cameraManagers.end()) {
        return -1;
    }
    return static_cast<int32_t>(it->second->cameras().size());
}

int32_t lc4j_cm_camera_id(int64_t handle, int32_t index, char* buf, int32_t buflen) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameraManagers.find(handle);
    if (it == g_cameraManagers.end()) {
        return -1;
    }
    auto cameras = it->second->cameras();
    if (index < 0 || (size_t)index >= cameras.size()) {
        return -1;
    }
    return copyString(cameras[index]->id(), buf, buflen);
}

int64_t lc4j_cm_get_camera(int64_t handle, const char* cameraId) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameraManagers.find(handle);
    if (it == g_cameraManagers.end() || cameraId == nullptr) {
        return 0;
    }

    auto camera = it->second->get(std::string(cameraId));
    if (!camera) {
        return 0;
    }

    int64_t camHandle = allocHandle();
    g_cameras[camHandle] = camera;
    g_completedRequests[camHandle] = std::queue<Request*>();
    return camHandle;
}

int32_t lc4j_cm_version(char* buf, int32_t buflen) {
    return copyString(CameraManager::version(), buf, buflen);
}

// -----------------------------------------------------------------------------
// Camera
// -----------------------------------------------------------------------------

int32_t lc4j_cam_acquire(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameras.find(handle);
    if (it == g_cameras.end()) {
        return -1;
    }
    return it->second->acquire();
}

void lc4j_cam_release(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameras.find(handle);
    if (it != g_cameras.end()) {
        it->second->release();
        // Drop shared_ptr so CameraManager can clean up properly
        g_cameras.erase(it);
        g_completedRequests.erase(handle);
    }
}

int64_t lc4j_cam_generate_configuration(int64_t handle, const int32_t* roles, int32_t count) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameras.find(handle);
    if (it == g_cameras.end()) {
        return 0;
    }

    std::vector<StreamRole> streamRoles;
    for (int32_t i = 0; i < count; i++) {
        streamRoles.push_back(static_cast<StreamRole>(roles[i]));
    }

    auto config = it->second->generateConfiguration(streamRoles);
    if (!config) {
        return 0;
    }

    int64_t configHandle = allocHandle();
    g_configurations[configHandle] = std::move(config);
    return configHandle;
}

int32_t lc4j_cam_configure(int64_t handle, int64_t configHandle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto camIt = g_cameras.find(handle);
    if (camIt == g_cameras.end()) {
        return -1;
    }
    auto confIt = g_configurations.find(configHandle);
    if (confIt == g_configurations.end()) {
        return -1;
    }
    return camIt->second->configure(confIt->second.get());
}

// Request completed callback - stores completed requests in queue
static void requestCompleted(Request* request) {
    Camera* cam = request->cookie() != 0 ?
        reinterpret_cast<Camera*>(request->cookie()) : nullptr;

    if (cam) {
        std::lock_guard<std::recursive_mutex> lock(g_mutex);
        for (auto& [handle, camera] : g_cameras) {
            if (camera.get() == cam) {
                g_completedRequests[handle].push(request);
                break;
            }
        }
    }
}

int32_t lc4j_cam_start(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameras.find(handle);
    if (it == g_cameras.end()) {
        return -1;
    }
    it->second->requestCompleted.connect(requestCompleted);
    return it->second->start();
}

void lc4j_cam_stop(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameras.find(handle);
    if (it != g_cameras.end()) {
        it->second->stop();
        while (!g_completedRequests[handle].empty()) {
            g_completedRequests[handle].pop();
        }
    }
}

int64_t lc4j_cam_create_request(int64_t handle, int64_t cookie) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameras.find(handle);
    if (it == g_cameras.end()) {
        return 0;
    }

    // Use camera pointer as cookie for callback routing
    auto request = it->second->createRequest(reinterpret_cast<uint64_t>(it->second.get()));
    if (!request) {
        return 0;
    }

    int64_t reqHandle = allocHandle();
    g_requests[reqHandle] = std::move(request);
    return reqHandle;
}

int32_t lc4j_cam_queue_request(int64_t handle, int64_t requestHandle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto camIt = g_cameras.find(handle);
    if (camIt == g_cameras.end()) {
        return -1;
    }
    auto reqIt = g_requests.find(requestHandle);
    if (reqIt == g_requests.end()) {
        return -1;
    }
    return camIt->second->queueRequest(reqIt->second.get());
}

int64_t lc4j_cam_poll_completed_request(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_completedRequests.find(handle);
    if (it == g_completedRequests.end() || it->second.empty()) {
        return 0;
    }

    Request* req = it->second.front();
    it->second.pop();

    for (auto& [reqHandle, request] : g_requests) {
        if (request.get() == req) {
            return reqHandle;
        }
    }
    return 0;
}

// -----------------------------------------------------------------------------
// CameraConfiguration
// -----------------------------------------------------------------------------

void lc4j_config_destroy(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    g_configurations.erase(handle);
}

int32_t lc4j_config_size(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_configurations.find(handle);
    if (it == g_configurations.end()) {
        return 0;
    }
    return it->second->size();
}

int32_t lc4j_config_validate(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_configurations.find(handle);
    if (it == g_configurations.end()) {
        return -1;
    }
    return static_cast<int32_t>(it->second->validate());
}

int32_t lc4j_config_get_width(int64_t handle, int32_t index) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_configurations.find(handle);
    if (it == g_configurations.end() || index < 0 || (size_t)index >= it->second->size()) {
        return 0;
    }
    return it->second->at(index).size.width;
}

int32_t lc4j_config_get_height(int64_t handle, int32_t index) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_configurations.find(handle);
    if (it == g_configurations.end() || index < 0 || (size_t)index >= it->second->size()) {
        return 0;
    }
    return it->second->at(index).size.height;
}

int32_t lc4j_config_get_stride(int64_t handle, int32_t index) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_configurations.find(handle);
    if (it == g_configurations.end() || index < 0 || (size_t)index >= it->second->size()) {
        return 0;
    }
    return it->second->at(index).stride;
}

int32_t lc4j_config_get_pixel_format(int64_t handle, int32_t index) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_configurations.find(handle);
    if (it == g_configurations.end() || index < 0 || (size_t)index >= it->second->size()) {
        return 0;
    }
    return it->second->at(index).pixelFormat.fourcc();
}

void lc4j_config_set_size(int64_t handle, int32_t index, int32_t width, int32_t height) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_configurations.find(handle);
    if (it == g_configurations.end() || index < 0 || (size_t)index >= it->second->size()) {
        return;
    }
    it->second->at(index).size.width = width;
    it->second->at(index).size.height = height;
}

void lc4j_config_set_pixel_format(int64_t handle, int32_t index, int32_t fourcc) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_configurations.find(handle);
    if (it == g_configurations.end() || index < 0 || (size_t)index >= it->second->size()) {
        return;
    }
    it->second->at(index).pixelFormat = PixelFormat(fourcc);
}

void lc4j_config_set_buffer_count(int64_t handle, int32_t index, int32_t count) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_configurations.find(handle);
    if (it == g_configurations.end() || index < 0 || (size_t)index >= it->second->size()) {
        return;
    }
    it->second->at(index).bufferCount = count;
}

// -----------------------------------------------------------------------------
// FrameBufferAllocator
// -----------------------------------------------------------------------------

int64_t lc4j_alloc_create(int64_t cameraHandle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameras.find(cameraHandle);
    if (it == g_cameras.end()) {
        return 0;
    }
    auto allocator = std::make_unique<FrameBufferAllocator>(it->second);
    int64_t handle = allocHandle();
    g_allocators[handle] = std::move(allocator);
    return handle;
}

void lc4j_alloc_destroy(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    g_allocators.erase(handle);
}

int32_t lc4j_alloc_allocate(int64_t handle, int64_t configHandle, int32_t streamIndex) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto allocIt = g_allocators.find(handle);
    if (allocIt == g_allocators.end()) {
        return -1;
    }
    auto confIt = g_configurations.find(configHandle);
    if (confIt == g_configurations.end()) {
        return -1;
    }
    if (streamIndex < 0 || (size_t)streamIndex >= confIt->second->size()) {
        return -1;
    }
    Stream* stream = confIt->second->at(streamIndex).stream();
    return allocIt->second->allocate(stream);
}

// -----------------------------------------------------------------------------
// Request
// -----------------------------------------------------------------------------

void lc4j_req_destroy(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    g_requests.erase(handle);
}

int32_t lc4j_req_add_buffer(int64_t handle, int64_t configHandle, int32_t streamIndex,
                            int64_t allocatorHandle, int32_t bufferIndex) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto reqIt = g_requests.find(handle);
    if (reqIt == g_requests.end()) {
        return -1;
    }
    auto confIt = g_configurations.find(configHandle);
    if (confIt == g_configurations.end()) {
        return -1;
    }
    auto allocIt = g_allocators.find(allocatorHandle);
    if (allocIt == g_allocators.end()) {
        return -1;
    }

    Stream* stream = confIt->second->at(streamIndex).stream();
    const auto& buffers = allocIt->second->buffers(stream);
    if (bufferIndex < 0 || (size_t)bufferIndex >= buffers.size()) {
        return -1;
    }
    return reqIt->second->addBuffer(stream, buffers[bufferIndex].get());
}

int32_t lc4j_req_reuse(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return -1;
    }
    it->second->reuse(Request::ReuseBuffers);
    return 0;
}

int32_t lc4j_req_status(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return -1;
    }
    return static_cast<int32_t>(it->second->status());
}

int64_t lc4j_req_get_timestamp(int64_t handle, int64_t configHandle, int32_t streamIndex,
                               int64_t allocatorHandle, int32_t bufferIndex) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto allocIt = g_allocators.find(allocatorHandle);
    auto confIt = g_configurations.find(configHandle);
    if (allocIt == g_allocators.end() || confIt == g_configurations.end()) {
        return 0;
    }
    Stream* stream = confIt->second->at(streamIndex).stream();
    const auto& buffers = allocIt->second->buffers(stream);
    if (bufferIndex >= 0 && (size_t)bufferIndex < buffers.size()) {
        return buffers[bufferIndex]->metadata().timestamp;
    }
    return 0;
}

int64_t lc4j_req_get_sequence(int64_t handle, int64_t configHandle, int32_t streamIndex,
                              int64_t allocatorHandle, int32_t bufferIndex) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto allocIt = g_allocators.find(allocatorHandle);
    auto confIt = g_configurations.find(configHandle);
    if (allocIt == g_allocators.end() || confIt == g_configurations.end()) {
        return 0;
    }
    Stream* stream = confIt->second->at(streamIndex).stream();
    const auto& buffers = allocIt->second->buffers(stream);
    if (bufferIndex >= 0 && (size_t)bufferIndex < buffers.size()) {
        return buffers[bufferIndex]->metadata().sequence;
    }
    return 0;
}

int64_t lc4j_req_get_exposure_time(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return 0;
    }
    auto expTime = it->second->metadata().get(controls::ExposureTime);
    if (expTime) {
        return *expTime;
    }
    return 0;
}

double lc4j_req_get_analogue_gain(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return 1.0;
    }
    auto gain = it->second->metadata().get(controls::AnalogueGain);
    if (gain) {
        return *gain;
    }
    return 1.0;
}

double lc4j_req_get_digital_gain(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return 1.0;
    }
    auto gain = it->second->metadata().get(controls::DigitalGain);
    if (gain) {
        return *gain;
    }
    return 1.0;
}

void lc4j_req_get_colour_gains(int64_t handle, double* out2) {
    if (out2 == nullptr) {
        return;
    }
    out2[0] = 1.0;
    out2[1] = 1.0;
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return;
    }
    auto gains = it->second->metadata().get(controls::ColourGains);
    if (gains) {
        out2[0] = (*gains)[0];
        out2[1] = (*gains)[1];
    }
}

int32_t lc4j_req_get_colour_temperature(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return 0;
    }
    auto temp = it->second->metadata().get(controls::ColourTemperature);
    if (temp) {
        return *temp;
    }
    return 0;
}

double lc4j_req_get_lux(int64_t handle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return 0.0;
    }
    auto lux = it->second->metadata().get(controls::Lux);
    if (lux) {
        return *lux;
    }
    return 0.0;
}

void lc4j_req_get_sensor_black_levels(int64_t handle, int32_t* out4) {
    if (out4 == nullptr) {
        return;
    }
    // Default for 10-bit sensor
    out4[0] = out4[1] = out4[2] = out4[3] = 4096;
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return;
    }
    auto blackLevel = it->second->metadata().get(controls::SensorBlackLevels);
    if (blackLevel) {
        for (int i = 0; i < 4; i++) {
            out4[i] = (*blackLevel)[i];
        }
    }
}

void lc4j_req_get_colour_correction_matrix(int64_t handle, double* out9) {
    if (out9 == nullptr) {
        return;
    }
    // Identity matrix as default
    static const double identity[9] = {1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0};
    std::memcpy(out9, identity, sizeof(identity));
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return;
    }
    auto ccm = it->second->metadata().get(controls::ColourCorrectionMatrix);
    if (ccm) {
        for (int i = 0; i < 9; i++) {
            out9[i] = (*ccm)[i];
        }
    }
}

void lc4j_req_set_af_mode(int64_t handle, int32_t mode) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it != g_requests.end()) {
        it->second->controls().set(controls::AfMode, mode);
    }
}

void lc4j_req_set_lens_position(int64_t handle, float position) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it != g_requests.end()) {
        it->second->controls().set(controls::LensPosition, position);
    }
}

void lc4j_req_set_ae_enable(int64_t handle, int32_t enable) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it != g_requests.end()) {
        it->second->controls().set(controls::AeEnable, enable != 0);
    }
}

void lc4j_req_set_exposure_time(int64_t handle, int32_t microseconds) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it != g_requests.end()) {
        it->second->controls().set(controls::ExposureTime, microseconds);
    }
}

void lc4j_req_set_analogue_gain(int64_t handle, float gain) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it != g_requests.end()) {
        it->second->controls().set(controls::AnalogueGain, gain);
    }
}

// -----------------------------------------------------------------------------
// FrameBuffer mapping (zero-copy: raw mmap'd pointers handed to Java)
// -----------------------------------------------------------------------------

struct MappedPlane {
    void* data;
    size_t length;
    unsigned int offset;  // offset within the plane
};

struct MappedBuffer {
    std::vector<MappedPlane> planes;
    size_t totalLength;
};
static std::map<int64_t, MappedBuffer> g_mappedBuffers;

int64_t lc4j_fb_map(int64_t allocatorHandle, int64_t configHandle,
                    int32_t streamIndex, int32_t bufferIndex) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);

    auto allocIt = g_allocators.find(allocatorHandle);
    if (allocIt == g_allocators.end()) {
        return 0;
    }
    auto confIt = g_configurations.find(configHandle);
    if (confIt == g_configurations.end()) {
        return 0;
    }

    Stream* stream = confIt->second->at(streamIndex).stream();
    const auto& buffers = allocIt->second->buffers(stream);
    if (bufferIndex < 0 || (size_t)bufferIndex >= buffers.size()) {
        return 0;
    }

    FrameBuffer* fb = buffers[bufferIndex].get();
    const auto& fbPlanes = fb->planes();
    if (fbPlanes.empty()) {
        return 0;
    }

    MappedBuffer mappedBuffer;
    mappedBuffer.totalLength = 0;

    for (size_t i = 0; i < fbPlanes.size(); i++) {
        const auto& plane = fbPlanes[i];

        if (plane.fd.get() < 0) {
            for (auto& mp : mappedBuffer.planes) {
                munmap(mp.data, mp.length);
            }
            return 0;
        }

        size_t mapSize = plane.offset + plane.length;
        if (mapSize == 0) {
            for (auto& mp : mappedBuffer.planes) {
                munmap(mp.data, mp.length);
            }
            return 0;
        }

        void* data = mmap(nullptr, mapSize, PROT_READ, MAP_SHARED, plane.fd.get(), 0);
        if (data == MAP_FAILED) {
            for (auto& mp : mappedBuffer.planes) {
                munmap(mp.data, mp.length);
            }
            return 0;
        }

        mappedBuffer.planes.push_back({data, mapSize, plane.offset});
        mappedBuffer.totalLength += plane.length;
    }

    int64_t mapHandle = allocHandle();
    g_mappedBuffers[mapHandle] = std::move(mappedBuffer);
    return mapHandle;
}

void lc4j_fb_unmap(int64_t mapHandle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_mappedBuffers.find(mapHandle);
    if (it != g_mappedBuffers.end()) {
        for (auto& plane : it->second.planes) {
            munmap(plane.data, plane.length);
        }
        g_mappedBuffers.erase(it);
    }
}

int32_t lc4j_fb_size(int64_t mapHandle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_mappedBuffers.find(mapHandle);
    if (it == g_mappedBuffers.end()) {
        return 0;
    }
    return static_cast<int32_t>(it->second.totalLength);
}

int32_t lc4j_fb_plane_count(int64_t mapHandle) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_mappedBuffers.find(mapHandle);
    if (it == g_mappedBuffers.end()) {
        return 0;
    }
    return static_cast<int32_t>(it->second.planes.size());
}

// Returns the address of the usable data for a plane (mmap base + plane offset).
int64_t lc4j_fb_plane_address(int64_t mapHandle, int32_t planeIndex) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_mappedBuffers.find(mapHandle);
    if (it == g_mappedBuffers.end()) {
        return 0;
    }
    if (planeIndex < 0 || (size_t)planeIndex >= it->second.planes.size()) {
        return 0;
    }
    const auto& plane = it->second.planes[planeIndex];
    return reinterpret_cast<int64_t>(static_cast<uint8_t*>(plane.data) + plane.offset);
}

// Returns the usable length of a plane (mapped length minus the leading offset).
int64_t lc4j_fb_plane_length(int64_t mapHandle, int32_t planeIndex) {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_mappedBuffers.find(mapHandle);
    if (it == g_mappedBuffers.end()) {
        return 0;
    }
    if (planeIndex < 0 || (size_t)planeIndex >= it->second.planes.size()) {
        return 0;
    }
    const auto& plane = it->second.planes[planeIndex];
    return static_cast<int64_t>(plane.length - plane.offset);
}

} // extern "C"
