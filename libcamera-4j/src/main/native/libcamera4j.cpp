/*
 * libcamera4j - JNI bindings for libcamera
 *
 * This file provides the native implementation for the libcamera-4j Java library.
 */

#include <jni.h>
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

// Global state for tracking native objects
// Use recursive_mutex since allocHandle() is called while holding the lock
static std::recursive_mutex g_mutex;
static std::map<jlong, std::shared_ptr<CameraManager>> g_cameraManagers;
static std::map<jlong, std::shared_ptr<Camera>> g_cameras;
static std::map<jlong, std::unique_ptr<CameraConfiguration>> g_configurations;
static std::map<jlong, std::unique_ptr<FrameBufferAllocator>> g_allocators;
static std::map<jlong, std::unique_ptr<Request>> g_requests;
static jlong g_nextHandle = 1;

// Request completion queue per camera
static std::map<jlong, std::queue<Request*>> g_completedRequests;
static std::map<jlong, std::mutex> g_requestMutex;

static jlong allocHandle() {
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    return g_nextHandle++;
}

// -----------------------------------------------------------------------------
// Helper functions
// -----------------------------------------------------------------------------

static void throwException(JNIEnv* env, const char* className, const char* message) {
    jclass exClass = env->FindClass(className);
    if (exClass != nullptr) {
        env->ThrowNew(exClass, message);
    }
}

static void throwLibCameraException(JNIEnv* env, const char* message) {
    throwException(env, "in/virit/libcamera4j/LibCameraException", message);
}

// Convert std::string to jstring
static jstring toJString(JNIEnv* env, const std::string& str) {
    return env->NewStringUTF(str.c_str());
}

// Convert jstring to std::string
static std::string fromJString(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) return "";
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return result;
}

// -----------------------------------------------------------------------------
// CameraManager native methods
// -----------------------------------------------------------------------------

extern "C" {

JNIEXPORT jlong JNICALL Java_in_virit_libcamera4j_CameraManager_nativeCreate(
    JNIEnv* env, jobject obj)
{
    try {
        auto cm = std::make_shared<CameraManager>();
        jlong handle = allocHandle();

        std::lock_guard<std::recursive_mutex> lock(g_mutex);
        g_cameraManagers[handle] = cm;

        return handle;
    } catch (const std::exception& e) {
        throwLibCameraException(env, e.what());
        return 0;
    }
}

JNIEXPORT void JNICALL Java_in_virit_libcamera4j_CameraManager_nativeDestroy(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    g_cameraManagers.erase(handle);
}

JNIEXPORT jint JNICALL Java_in_virit_libcamera4j_CameraManager_nativeStart(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameraManagers.find(handle);
    if (it == g_cameraManagers.end()) {
        throwLibCameraException(env, "Invalid CameraManager handle");
        return -1;
    }

    return it->second->start();
}

JNIEXPORT void JNICALL Java_in_virit_libcamera4j_CameraManager_nativeStop(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameraManagers.find(handle);
    if (it != g_cameraManagers.end()) {
        it->second->stop();
    }
}

JNIEXPORT jobjectArray JNICALL Java_in_virit_libcamera4j_CameraManager_nativeGetCameraIds(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameraManagers.find(handle);
    if (it == g_cameraManagers.end()) {
        throwLibCameraException(env, "Invalid CameraManager handle");
        return nullptr;
    }

    auto cameras = it->second->cameras();

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(cameras.size(), stringClass, nullptr);

    for (size_t i = 0; i < cameras.size(); i++) {
        jstring id = toJString(env, cameras[i]->id());
        env->SetObjectArrayElement(result, i, id);
        env->DeleteLocalRef(id);
    }

    return result;
}

JNIEXPORT jlong JNICALL Java_in_virit_libcamera4j_CameraManager_nativeGetCamera(
    JNIEnv* env, jobject obj, jlong handle, jstring cameraId)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameraManagers.find(handle);
    if (it == g_cameraManagers.end()) {
        throwLibCameraException(env, "Invalid CameraManager handle");
        return 0;
    }

    std::string id = fromJString(env, cameraId);
    auto camera = it->second->get(id);

    if (!camera) {
        return 0;
    }

    jlong camHandle = allocHandle();
    g_cameras[camHandle] = camera;
    g_completedRequests[camHandle] = std::queue<Request*>();

    return camHandle;
}

JNIEXPORT jstring JNICALL Java_in_virit_libcamera4j_CameraManager_nativeVersion(
    JNIEnv* env, jclass clazz)
{
    return toJString(env, CameraManager::version());
}

// -----------------------------------------------------------------------------
// Camera native methods
// -----------------------------------------------------------------------------

JNIEXPORT jstring JNICALL Java_in_virit_libcamera4j_Camera_nativeGetId(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameras.find(handle);
    if (it == g_cameras.end()) {
        return nullptr;
    }
    return toJString(env, it->second->id());
}

JNIEXPORT jint JNICALL Java_in_virit_libcamera4j_Camera_nativeAcquire(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameras.find(handle);
    if (it == g_cameras.end()) {
        throwLibCameraException(env, "Invalid Camera handle");
        return -1;
    }
    return it->second->acquire();
}

JNIEXPORT void JNICALL Java_in_virit_libcamera4j_Camera_nativeRelease(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameras.find(handle);
    if (it != g_cameras.end()) {
        it->second->release();
        // Remove from map to drop shared_ptr reference
        // This allows CameraManager to properly clean up
        g_cameras.erase(it);
        g_completedRequests.erase(handle);
    }
}

JNIEXPORT jlong JNICALL Java_in_virit_libcamera4j_Camera_nativeGenerateConfiguration(
    JNIEnv* env, jobject obj, jlong handle, jintArray roles)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameras.find(handle);
    if (it == g_cameras.end()) {
        throwLibCameraException(env, "Invalid Camera handle");
        return 0;
    }

    // Convert Java int array to StreamRole vector
    jsize len = env->GetArrayLength(roles);
    jint* roleValues = env->GetIntArrayElements(roles, nullptr);

    std::vector<StreamRole> streamRoles;
    for (jsize i = 0; i < len; i++) {
        streamRoles.push_back(static_cast<StreamRole>(roleValues[i]));
    }
    env->ReleaseIntArrayElements(roles, roleValues, 0);

    auto config = it->second->generateConfiguration(streamRoles);
    if (!config) {
        return 0;
    }

    jlong configHandle = allocHandle();
    g_configurations[configHandle] = std::move(config);

    return configHandle;
}

JNIEXPORT jint JNICALL Java_in_virit_libcamera4j_Camera_nativeConfigure(
    JNIEnv* env, jobject obj, jlong handle, jlong configHandle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);

    auto camIt = g_cameras.find(handle);
    if (camIt == g_cameras.end()) {
        throwLibCameraException(env, "Invalid Camera handle");
        return -1;
    }

    auto confIt = g_configurations.find(configHandle);
    if (confIt == g_configurations.end()) {
        throwLibCameraException(env, "Invalid CameraConfiguration handle");
        return -1;
    }

    return camIt->second->configure(confIt->second.get());
}

// Request completed callback - stores completed requests in queue
static void requestCompleted(Request* request) {
    // Find which camera this request belongs to
    Camera* cam = request->cookie() != 0 ?
        reinterpret_cast<Camera*>(request->cookie()) : nullptr;

    if (cam) {
        // Find handle for this camera
        std::lock_guard<std::recursive_mutex> lock(g_mutex);
        for (auto& [handle, camera] : g_cameras) {
            if (camera.get() == cam) {
                g_completedRequests[handle].push(request);
                break;
            }
        }
    }
}

JNIEXPORT jint JNICALL Java_in_virit_libcamera4j_Camera_nativeStart(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameras.find(handle);
    if (it == g_cameras.end()) {
        throwLibCameraException(env, "Invalid Camera handle");
        return -1;
    }

    // Connect request completed signal
    it->second->requestCompleted.connect(requestCompleted);

    return it->second->start();
}

JNIEXPORT void JNICALL Java_in_virit_libcamera4j_Camera_nativeStop(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameras.find(handle);
    if (it != g_cameras.end()) {
        it->second->stop();
        // Clear completed requests queue
        while (!g_completedRequests[handle].empty()) {
            g_completedRequests[handle].pop();
        }
    }
}

JNIEXPORT jlong JNICALL Java_in_virit_libcamera4j_Camera_nativeCreateRequest(
    JNIEnv* env, jobject obj, jlong handle, jlong cookie)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameras.find(handle);
    if (it == g_cameras.end()) {
        throwLibCameraException(env, "Invalid Camera handle");
        return 0;
    }

    // Use camera pointer as cookie for callback routing
    auto request = it->second->createRequest(reinterpret_cast<uint64_t>(it->second.get()));
    if (!request) {
        return 0;
    }

    jlong reqHandle = allocHandle();
    g_requests[reqHandle] = std::move(request);

    return reqHandle;
}

JNIEXPORT jint JNICALL Java_in_virit_libcamera4j_Camera_nativeQueueRequest(
    JNIEnv* env, jobject obj, jlong handle, jlong requestHandle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);

    auto camIt = g_cameras.find(handle);
    if (camIt == g_cameras.end()) {
        throwLibCameraException(env, "Invalid Camera handle");
        return -1;
    }

    auto reqIt = g_requests.find(requestHandle);
    if (reqIt == g_requests.end()) {
        throwLibCameraException(env, "Invalid Request handle");
        return -1;
    }

    return camIt->second->queueRequest(reqIt->second.get());
}

// -----------------------------------------------------------------------------
// CameraConfiguration native methods
// -----------------------------------------------------------------------------

JNIEXPORT void JNICALL Java_in_virit_libcamera4j_CameraConfiguration_nativeDestroy(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    g_configurations.erase(handle);
}

JNIEXPORT jint JNICALL Java_in_virit_libcamera4j_CameraConfiguration_nativeSize(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_configurations.find(handle);
    if (it == g_configurations.end()) {
        return 0;
    }
    return it->second->size();
}

JNIEXPORT jint JNICALL Java_in_virit_libcamera4j_CameraConfiguration_nativeValidate(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_configurations.find(handle);
    if (it == g_configurations.end()) {
        return -1;
    }
    return static_cast<jint>(it->second->validate());
}

JNIEXPORT jint JNICALL Java_in_virit_libcamera4j_CameraConfiguration_nativeGetWidth(
    JNIEnv* env, jobject obj, jlong handle, jint index)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_configurations.find(handle);
    if (it == g_configurations.end() || index < 0 || (size_t)index >= it->second->size()) {
        return 0;
    }
    return it->second->at(index).size.width;
}

JNIEXPORT jint JNICALL Java_in_virit_libcamera4j_CameraConfiguration_nativeGetHeight(
    JNIEnv* env, jobject obj, jlong handle, jint index)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_configurations.find(handle);
    if (it == g_configurations.end() || index < 0 || (size_t)index >= it->second->size()) {
        return 0;
    }
    return it->second->at(index).size.height;
}

JNIEXPORT jint JNICALL Java_in_virit_libcamera4j_CameraConfiguration_nativeGetStride(
    JNIEnv* env, jobject obj, jlong handle, jint index)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_configurations.find(handle);
    if (it == g_configurations.end() || index < 0 || (size_t)index >= it->second->size()) {
        return 0;
    }
    return it->second->at(index).stride;
}

JNIEXPORT jint JNICALL Java_in_virit_libcamera4j_CameraConfiguration_nativeGetPixelFormat(
    JNIEnv* env, jobject obj, jlong handle, jint index)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_configurations.find(handle);
    if (it == g_configurations.end() || index < 0 || (size_t)index >= it->second->size()) {
        return 0;
    }
    return it->second->at(index).pixelFormat.fourcc();
}

JNIEXPORT void JNICALL Java_in_virit_libcamera4j_CameraConfiguration_nativeSetSize(
    JNIEnv* env, jobject obj, jlong handle, jint index, jint width, jint height)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_configurations.find(handle);
    if (it == g_configurations.end() || index < 0 || (size_t)index >= it->second->size()) {
        return;
    }
    it->second->at(index).size.width = width;
    it->second->at(index).size.height = height;
}

JNIEXPORT void JNICALL Java_in_virit_libcamera4j_CameraConfiguration_nativeSetPixelFormat(
    JNIEnv* env, jobject obj, jlong handle, jint index, jint fourcc)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_configurations.find(handle);
    if (it == g_configurations.end() || index < 0 || (size_t)index >= it->second->size()) {
        return;
    }
    it->second->at(index).pixelFormat = PixelFormat(fourcc);
}

JNIEXPORT void JNICALL Java_in_virit_libcamera4j_CameraConfiguration_nativeSetBufferCount(
    JNIEnv* env, jobject obj, jlong handle, jint index, jint count)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_configurations.find(handle);
    if (it == g_configurations.end() || index < 0 || (size_t)index >= it->second->size()) {
        return;
    }
    it->second->at(index).bufferCount = count;
}

// -----------------------------------------------------------------------------
// FrameBufferAllocator native methods
// -----------------------------------------------------------------------------

JNIEXPORT jlong JNICALL Java_in_virit_libcamera4j_FrameBufferAllocator_nativeCreate(
    JNIEnv* env, jobject obj, jlong cameraHandle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_cameras.find(cameraHandle);
    if (it == g_cameras.end()) {
        throwLibCameraException(env, "Invalid Camera handle");
        return 0;
    }

    auto allocator = std::make_unique<FrameBufferAllocator>(it->second);
    jlong handle = allocHandle();
    g_allocators[handle] = std::move(allocator);

    return handle;
}

JNIEXPORT void JNICALL Java_in_virit_libcamera4j_FrameBufferAllocator_nativeDestroy(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    g_allocators.erase(handle);
}

JNIEXPORT jint JNICALL Java_in_virit_libcamera4j_FrameBufferAllocator_nativeAllocate(
    JNIEnv* env, jobject obj, jlong handle, jlong configHandle, jint streamIndex)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);

    auto allocIt = g_allocators.find(handle);
    if (allocIt == g_allocators.end()) {
        throwLibCameraException(env, "Invalid FrameBufferAllocator handle");
        return -1;
    }

    auto confIt = g_configurations.find(configHandle);
    if (confIt == g_configurations.end()) {
        throwLibCameraException(env, "Invalid CameraConfiguration handle");
        return -1;
    }

    if (streamIndex < 0 || (size_t)streamIndex >= confIt->second->size()) {
        throwLibCameraException(env, "Invalid stream index");
        return -1;
    }

    Stream* stream = confIt->second->at(streamIndex).stream();
    return allocIt->second->allocate(stream);
}

JNIEXPORT jint JNICALL Java_in_virit_libcamera4j_FrameBufferAllocator_nativeGetBufferCount(
    JNIEnv* env, jobject obj, jlong handle, jlong configHandle, jint streamIndex)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);

    auto allocIt = g_allocators.find(handle);
    if (allocIt == g_allocators.end()) {
        return 0;
    }

    auto confIt = g_configurations.find(configHandle);
    if (confIt == g_configurations.end()) {
        return 0;
    }

    if (streamIndex < 0 || (size_t)streamIndex >= confIt->second->size()) {
        return 0;
    }

    Stream* stream = confIt->second->at(streamIndex).stream();
    const auto& buffers = allocIt->second->buffers(stream);
    return buffers.size();
}

// -----------------------------------------------------------------------------
// Request native methods
// -----------------------------------------------------------------------------

JNIEXPORT void JNICALL Java_in_virit_libcamera4j_Request_nativeDestroy(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    g_requests.erase(handle);
}

JNIEXPORT jint JNICALL Java_in_virit_libcamera4j_Request_nativeAddBuffer(
    JNIEnv* env, jobject obj, jlong handle, jlong configHandle, jint streamIndex,
    jlong allocatorHandle, jint bufferIndex)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);

    auto reqIt = g_requests.find(handle);
    if (reqIt == g_requests.end()) {
        throwLibCameraException(env, "Invalid Request handle");
        return -1;
    }

    auto confIt = g_configurations.find(configHandle);
    if (confIt == g_configurations.end()) {
        throwLibCameraException(env, "Invalid CameraConfiguration handle");
        return -1;
    }

    auto allocIt = g_allocators.find(allocatorHandle);
    if (allocIt == g_allocators.end()) {
        throwLibCameraException(env, "Invalid FrameBufferAllocator handle");
        return -1;
    }

    Stream* stream = confIt->second->at(streamIndex).stream();
    const auto& buffers = allocIt->second->buffers(stream);

    if (bufferIndex < 0 || (size_t)bufferIndex >= buffers.size()) {
        throwLibCameraException(env, "Invalid buffer index");
        return -1;
    }

    return reqIt->second->addBuffer(stream, buffers[bufferIndex].get());
}

JNIEXPORT jint JNICALL Java_in_virit_libcamera4j_Request_nativeReuse(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return -1;
    }
    it->second->reuse(Request::ReuseBuffers);
    return 0;
}

JNIEXPORT jint JNICALL Java_in_virit_libcamera4j_Request_nativeStatus(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return -1;
    }
    return static_cast<jint>(it->second->status());
}

JNIEXPORT void JNICALL Java_in_virit_libcamera4j_Request_nativeSetAfMode(
    JNIEnv* env, jobject obj, jlong handle, jint mode)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return;
    }
    it->second->controls().set(controls::AfMode, mode);
}

JNIEXPORT void JNICALL Java_in_virit_libcamera4j_Request_nativeSetLensPosition(
    JNIEnv* env, jobject obj, jlong handle, jfloat position)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return;
    }
    it->second->controls().set(controls::LensPosition, position);
}

JNIEXPORT void JNICALL Java_in_virit_libcamera4j_Request_nativeSetAeEnable(
    JNIEnv* env, jobject obj, jlong handle, jboolean enable)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return;
    }
    it->second->controls().set(controls::AeEnable, enable == JNI_TRUE);
}

JNIEXPORT void JNICALL Java_in_virit_libcamera4j_Request_nativeSetExposureTime(
    JNIEnv* env, jobject obj, jlong handle, jint microseconds)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return;
    }
    it->second->controls().set(controls::ExposureTime, microseconds);
}

JNIEXPORT void JNICALL Java_in_virit_libcamera4j_Request_nativeSetAnalogueGain(
    JNIEnv* env, jobject obj, jlong handle, jfloat gain)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return;
    }
    it->second->controls().set(controls::AnalogueGain, gain);
}

// -----------------------------------------------------------------------------
// Buffer access for captured frames
// -----------------------------------------------------------------------------

// Structure to hold mapped buffer info for multiple planes
struct MappedPlane {
    void* data;
    size_t length;
    unsigned int offset;  // offset within the plane
};

struct MappedBuffer {
    std::vector<MappedPlane> planes;
    size_t totalLength;
};
static std::map<jlong, MappedBuffer> g_mappedBuffers;

JNIEXPORT jlong JNICALL Java_in_virit_libcamera4j_FrameBuffer_nativeMapBuffer(
    JNIEnv* env, jobject obj, jlong allocatorHandle, jlong configHandle,
    jint streamIndex, jint bufferIndex)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);

    auto allocIt = g_allocators.find(allocatorHandle);
    if (allocIt == g_allocators.end()) {
        throwLibCameraException(env, "Invalid FrameBufferAllocator handle");
        return 0;
    }

    auto confIt = g_configurations.find(configHandle);
    if (confIt == g_configurations.end()) {
        throwLibCameraException(env, "Invalid CameraConfiguration handle");
        return 0;
    }

    Stream* stream = confIt->second->at(streamIndex).stream();
    const auto& buffers = allocIt->second->buffers(stream);

    if (bufferIndex < 0 || (size_t)bufferIndex >= buffers.size()) {
        throwLibCameraException(env, "Invalid buffer index");
        return 0;
    }

    FrameBuffer* fb = buffers[bufferIndex].get();
    const auto& fbPlanes = fb->planes();

    if (fbPlanes.empty()) {
        throwLibCameraException(env, "FrameBuffer has no planes");
        return 0;
    }

    MappedBuffer mappedBuffer;
    mappedBuffer.totalLength = 0;

    // Map all planes
    for (size_t i = 0; i < fbPlanes.size(); i++) {
        const auto& plane = fbPlanes[i];

        // Validate plane parameters
        if (plane.fd.get() < 0) {
            for (auto& mp : mappedBuffer.planes) {
                munmap(mp.data, mp.length);
            }
            char errMsg[128];
            snprintf(errMsg, sizeof(errMsg), "Invalid plane fd at index %zu", i);
            throwLibCameraException(env, errMsg);
            return 0;
        }

        // Calculate the size to map - use the actual plane length
        // For raw buffers, the offset might be 0 and length is the full buffer size
        size_t mapSize = plane.offset + plane.length;
        if (mapSize == 0) {
            for (auto& mp : mappedBuffer.planes) {
                munmap(mp.data, mp.length);
            }
            char errMsg[128];
            snprintf(errMsg, sizeof(errMsg), "Zero-size plane at index %zu", i);
            throwLibCameraException(env, errMsg);
            return 0;
        }

        void* data = mmap(nullptr, mapSize, PROT_READ, MAP_SHARED, plane.fd.get(), 0);
        if (data == MAP_FAILED) {
            // Unmap any planes we already mapped
            for (auto& mp : mappedBuffer.planes) {
                munmap(mp.data, mp.length);
            }
            char errMsg[128];
            snprintf(errMsg, sizeof(errMsg),
                     "Failed to mmap buffer plane %zu (fd=%d, size=%zu): %s",
                     i, plane.fd.get(), mapSize, strerror(errno));
            throwLibCameraException(env, errMsg);
            return 0;
        }

        mappedBuffer.planes.push_back({data, mapSize, plane.offset});
        mappedBuffer.totalLength += plane.length;
    }

    jlong mapHandle = allocHandle();
    g_mappedBuffers[mapHandle] = std::move(mappedBuffer);

    return mapHandle;
}

JNIEXPORT void JNICALL Java_in_virit_libcamera4j_FrameBuffer_nativeUnmapBuffer(
    JNIEnv* env, jobject obj, jlong mapHandle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_mappedBuffers.find(mapHandle);
    if (it != g_mappedBuffers.end()) {
        for (auto& plane : it->second.planes) {
            munmap(plane.data, plane.length);
        }
        g_mappedBuffers.erase(it);
    }
}

JNIEXPORT jint JNICALL Java_in_virit_libcamera4j_FrameBuffer_nativeGetBufferSize(
    JNIEnv* env, jobject obj, jlong mapHandle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_mappedBuffers.find(mapHandle);
    if (it == g_mappedBuffers.end()) {
        return 0;
    }
    return it->second.totalLength;
}

JNIEXPORT void JNICALL Java_in_virit_libcamera4j_FrameBuffer_nativeCopyBuffer(
    JNIEnv* env, jobject obj, jlong mapHandle, jbyteArray dest, jint offset, jint length)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_mappedBuffers.find(mapHandle);
    if (it == g_mappedBuffers.end()) {
        throwLibCameraException(env, "Invalid map handle");
        return;
    }

    // Copy data from all planes sequentially
    jint destOffset = offset;
    size_t remaining = std::min((size_t)length, it->second.totalLength);

    for (const auto& plane : it->second.planes) {
        if (remaining == 0) break;

        // Calculate actual data start (base pointer + plane offset)
        uint8_t* planeData = static_cast<uint8_t*>(plane.data) + plane.offset;
        size_t planeDataLen = plane.length - plane.offset;
        size_t copyLen = std::min(remaining, planeDataLen);

        env->SetByteArrayRegion(dest, destOffset, copyLen, (jbyte*)planeData);
        destOffset += copyLen;
        remaining -= copyLen;
    }
}

// -----------------------------------------------------------------------------
// Completed request polling
// -----------------------------------------------------------------------------

JNIEXPORT jlong JNICALL Java_in_virit_libcamera4j_Camera_nativePollCompletedRequest(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);

    auto it = g_completedRequests.find(handle);
    if (it == g_completedRequests.end() || it->second.empty()) {
        return 0;
    }

    Request* req = it->second.front();
    it->second.pop();

    // Find the handle for this request
    for (auto& [reqHandle, request] : g_requests) {
        if (request.get() == req) {
            return reqHandle;
        }
    }

    return 0;
}

JNIEXPORT jlong JNICALL Java_in_virit_libcamera4j_Request_nativeGetTimestamp(
    JNIEnv* env, jobject obj, jlong handle, jlong configHandle, jint streamIndex,
    jlong allocatorHandle, jint bufferIndex)
{
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

JNIEXPORT jlong JNICALL Java_in_virit_libcamera4j_Request_nativeGetSequence(
    JNIEnv* env, jobject obj, jlong handle, jlong configHandle, jint streamIndex,
    jlong allocatorHandle, jint bufferIndex)
{
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

// -----------------------------------------------------------------------------
// Request metadata extraction (EXIF-like data)
// -----------------------------------------------------------------------------

JNIEXPORT jlong JNICALL Java_in_virit_libcamera4j_Request_nativeGetExposureTime(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return 0;
    }

    const ControlList& metadata = it->second->metadata();
    auto expTime = metadata.get(controls::ExposureTime);
    if (expTime) {
        return *expTime;
    }
    return 0;
}

JNIEXPORT jdouble JNICALL Java_in_virit_libcamera4j_Request_nativeGetAnalogueGain(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return 1.0;
    }

    const ControlList& metadata = it->second->metadata();
    auto gain = metadata.get(controls::AnalogueGain);
    if (gain) {
        return *gain;
    }
    return 1.0;
}

JNIEXPORT jdouble JNICALL Java_in_virit_libcamera4j_Request_nativeGetDigitalGain(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return 1.0;
    }

    const ControlList& metadata = it->second->metadata();
    auto gain = metadata.get(controls::DigitalGain);
    if (gain) {
        return *gain;
    }
    return 1.0;
}

JNIEXPORT jdoubleArray JNICALL Java_in_virit_libcamera4j_Request_nativeGetColourGains(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);

    jdoubleArray result = env->NewDoubleArray(2);
    jdouble defaults[] = {1.0, 1.0};

    if (it == g_requests.end()) {
        env->SetDoubleArrayRegion(result, 0, 2, defaults);
        return result;
    }

    const ControlList& metadata = it->second->metadata();
    auto gains = metadata.get(controls::ColourGains);
    if (gains) {
        jdouble values[] = {(*gains)[0], (*gains)[1]};
        env->SetDoubleArrayRegion(result, 0, 2, values);
    } else {
        env->SetDoubleArrayRegion(result, 0, 2, defaults);
    }
    return result;
}

JNIEXPORT jint JNICALL Java_in_virit_libcamera4j_Request_nativeGetColourTemperature(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return 0;
    }

    const ControlList& metadata = it->second->metadata();
    auto temp = metadata.get(controls::ColourTemperature);
    if (temp) {
        return *temp;
    }
    return 0;
}

JNIEXPORT jdouble JNICALL Java_in_virit_libcamera4j_Request_nativeGetLux(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        return 0.0;
    }

    const ControlList& metadata = it->second->metadata();
    auto lux = metadata.get(controls::Lux);
    if (lux) {
        return *lux;
    }
    return 0.0;
}

// -----------------------------------------------------------------------------
// DNG-specific metadata extraction
// -----------------------------------------------------------------------------

JNIEXPORT jintArray JNICALL Java_in_virit_libcamera4j_Request_nativeGetSensorBlackLevels(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    jintArray result = env->NewIntArray(4);
    jint defaults[] = {4096, 4096, 4096, 4096};  // Default for 10-bit sensor

    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        env->SetIntArrayRegion(result, 0, 4, defaults);
        return result;
    }

    const ControlList& metadata = it->second->metadata();
    auto blackLevel = metadata.get(controls::SensorBlackLevels);
    if (blackLevel) {
        jint levels[] = {(*blackLevel)[0], (*blackLevel)[1], (*blackLevel)[2], (*blackLevel)[3]};
        env->SetIntArrayRegion(result, 0, 4, levels);
    } else {
        env->SetIntArrayRegion(result, 0, 4, defaults);
    }
    return result;
}

JNIEXPORT jdoubleArray JNICALL Java_in_virit_libcamera4j_Request_nativeGetColourCorrectionMatrix(
    JNIEnv* env, jobject obj, jlong handle)
{
    std::lock_guard<std::recursive_mutex> lock(g_mutex);
    jdoubleArray result = env->NewDoubleArray(9);
    // Identity matrix as default
    jdouble defaults[] = {1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0};

    auto it = g_requests.find(handle);
    if (it == g_requests.end()) {
        env->SetDoubleArrayRegion(result, 0, 9, defaults);
        return result;
    }

    const ControlList& metadata = it->second->metadata();
    auto ccm = metadata.get(controls::ColourCorrectionMatrix);
    if (ccm) {
        jdouble values[9];
        for (int i = 0; i < 9; i++) {
            values[i] = (*ccm)[i];
        }
        env->SetDoubleArrayRegion(result, 0, 9, values);
    } else {
        env->SetDoubleArrayRegion(result, 0, 9, defaults);
    }
    return result;
}

} // extern "C"

// Note: DNG writing was moved to pure Java (DngWriter.java)
