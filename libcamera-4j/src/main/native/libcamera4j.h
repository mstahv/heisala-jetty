/*
 * libcamera4j - C ABI for the Foreign Function & Memory (Panama) bindings.
 *
 * This header declares the flat C entry points that the Java FFM layer binds to.
 * libcamera itself is C++; this shim flattens its C++ API (shared_ptr lifetimes,
 * signals, templated controls) into a handle-based C ABI with no JNI dependency.
 *
 * Conventions:
 *   - Objects are referenced by opaque int64_t handles (0 == invalid/failure).
 *   - Status-returning functions return 0 on success, negative on error.
 *   - Strings are returned by copying into a caller-provided buffer; the return
 *     value is the number of bytes written (excluding the NUL), or -1 on error.
 *   - Fixed-size arrays are returned via caller-allocated out pointers.
 */
#ifndef LIBCAMERA4J_H
#define LIBCAMERA4J_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ---- CameraManager ---- */
int64_t lc4j_cm_create(void);
void    lc4j_cm_destroy(int64_t handle);
int32_t lc4j_cm_start(int64_t handle);
void    lc4j_cm_stop(int64_t handle);
int32_t lc4j_cm_camera_count(int64_t handle);
int32_t lc4j_cm_camera_id(int64_t handle, int32_t index, char* buf, int32_t buflen);
int64_t lc4j_cm_get_camera(int64_t handle, const char* cameraId);
int32_t lc4j_cm_version(char* buf, int32_t buflen);

/* ---- Camera ---- */
int32_t lc4j_cam_acquire(int64_t handle);
void    lc4j_cam_release(int64_t handle);
int64_t lc4j_cam_generate_configuration(int64_t handle, const int32_t* roles, int32_t count);
int32_t lc4j_cam_configure(int64_t handle, int64_t configHandle);
int64_t lc4j_cam_create_request(int64_t handle, int64_t cookie);
int32_t lc4j_cam_queue_request(int64_t handle, int64_t requestHandle);
int32_t lc4j_cam_start(int64_t handle);
void    lc4j_cam_stop(int64_t handle);
int64_t lc4j_cam_poll_completed_request(int64_t handle);

/* ---- CameraConfiguration ---- */
void    lc4j_config_destroy(int64_t handle);
int32_t lc4j_config_size(int64_t handle);
int32_t lc4j_config_validate(int64_t handle);
int32_t lc4j_config_get_width(int64_t handle, int32_t index);
int32_t lc4j_config_get_height(int64_t handle, int32_t index);
int32_t lc4j_config_get_stride(int64_t handle, int32_t index);
int32_t lc4j_config_get_pixel_format(int64_t handle, int32_t index);
void    lc4j_config_set_size(int64_t handle, int32_t index, int32_t width, int32_t height);
void    lc4j_config_set_pixel_format(int64_t handle, int32_t index, int32_t fourcc);
void    lc4j_config_set_buffer_count(int64_t handle, int32_t index, int32_t count);

/* ---- FrameBufferAllocator ---- */
int64_t lc4j_alloc_create(int64_t cameraHandle);
void    lc4j_alloc_destroy(int64_t handle);
int32_t lc4j_alloc_allocate(int64_t handle, int64_t configHandle, int32_t streamIndex);

/* ---- Request ---- */
void    lc4j_req_destroy(int64_t handle);
int32_t lc4j_req_add_buffer(int64_t handle, int64_t configHandle, int32_t streamIndex,
                            int64_t allocatorHandle, int32_t bufferIndex);
int32_t lc4j_req_reuse(int64_t handle);
int32_t lc4j_req_status(int64_t handle);
int64_t lc4j_req_get_timestamp(int64_t handle, int64_t configHandle, int32_t streamIndex,
                               int64_t allocatorHandle, int32_t bufferIndex);
int64_t lc4j_req_get_sequence(int64_t handle, int64_t configHandle, int32_t streamIndex,
                              int64_t allocatorHandle, int32_t bufferIndex);
int64_t lc4j_req_get_exposure_time(int64_t handle);
double  lc4j_req_get_analogue_gain(int64_t handle);
double  lc4j_req_get_digital_gain(int64_t handle);
void    lc4j_req_get_colour_gains(int64_t handle, double* out2);
int32_t lc4j_req_get_colour_temperature(int64_t handle);
double  lc4j_req_get_lux(int64_t handle);
void    lc4j_req_get_sensor_black_levels(int64_t handle, int32_t* out4);
void    lc4j_req_get_colour_correction_matrix(int64_t handle, double* out9);
void    lc4j_req_set_af_mode(int64_t handle, int32_t mode);
void    lc4j_req_set_lens_position(int64_t handle, float position);
void    lc4j_req_set_ae_enable(int64_t handle, int32_t enable);
void    lc4j_req_set_exposure_time(int64_t handle, int32_t microseconds);
void    lc4j_req_set_analogue_gain(int64_t handle, float gain);

/* ---- FrameBuffer ---- */
int64_t lc4j_fb_map(int64_t allocatorHandle, int64_t configHandle, int32_t streamIndex, int32_t bufferIndex);
void    lc4j_fb_unmap(int64_t mapHandle);
int32_t lc4j_fb_size(int64_t mapHandle);
int32_t lc4j_fb_plane_count(int64_t mapHandle);
int64_t lc4j_fb_plane_address(int64_t mapHandle, int32_t planeIndex);
int64_t lc4j_fb_plane_length(int64_t mapHandle, int32_t planeIndex);

#ifdef __cplusplus
}
#endif

#endif /* LIBCAMERA4J_H */
