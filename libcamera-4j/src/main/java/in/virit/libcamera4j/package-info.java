/**
 * Java bindings for the libcamera C library.
 *
 * <p>This package provides a Java API for accessing camera devices using libcamera,
 * the modern camera stack for Linux. It uses the Foreign Function &amp; Memory API
 * (Project Panama) to interface with the native libcamera library.</p>
 *
 * <h2>Getting Started</h2>
 *
 * <p>The main entry point is {@link in.virit.libcamera4j.CameraManager}, which
 * provides access to camera devices:</p>
 *
 * <pre>{@code
 * try (CameraManager manager = CameraManager.create()) {
 *     manager.start();
 *
 *     List<Camera> cameras = manager.cameras();
 *     if (cameras.isEmpty()) {
 *         System.out.println("No cameras found");
 *         return;
 *     }
 *
 *     Camera camera = cameras.get(0);
 *     camera.acquire();
 *
 *     // Configure the camera for viewfinder use
 *     CameraConfiguration config = camera.generateConfiguration(StreamRole.VIEWFINDER);
 *     camera.configure(config);
 *
 *     // Allocate buffers
 *     try (FrameBufferAllocator allocator = new FrameBufferAllocator(camera)) {
 *         Stream stream = config.get(0).stream();
 *         allocator.allocate(stream);
 *
 *         // Create and queue requests
 *         camera.onRequestCompleted(request -> {
 *             // Process completed frame...
 *         });
 *
 *         camera.start();
 *
 *         for (FrameBuffer buffer : allocator.buffers(stream)) {
 *             Request request = camera.createRequest();
 *             request.addBuffer(stream, buffer);
 *             camera.queueRequest(request);
 *         }
 *
 *         // Wait for captures...
 *
 *         camera.stop();
 *     }
 *
 *     camera.release();
 * }
 * }</pre>
 *
 * <h2>Requirements</h2>
 *
 * <ul>
 *   <li>Java 21 or later (uses Foreign Function &amp; Memory API)</li>
 *   <li>libcamera installed on the system</li>
 *   <li>On Raspberry Pi: {@code sudo apt install libcamera-dev}</li>
 * </ul>
 *
 * <h2>Key Classes</h2>
 *
 * <ul>
 *   <li>{@link in.virit.libcamera4j.CameraManager} - Entry point for discovering cameras</li>
 *   <li>{@link in.virit.libcamera4j.Camera} - Represents a camera device</li>
 *   <li>{@link in.virit.libcamera4j.CameraConfiguration} - Camera configuration</li>
 *   <li>{@link in.virit.libcamera4j.Request} - Capture request</li>
 *   <li>{@link in.virit.libcamera4j.FrameBuffer} - Frame buffer with captured data</li>
 *   <li>{@link in.virit.libcamera4j.FrameBufferAllocator} - Buffer allocation</li>
 * </ul>
 *
 * @see <a href="https://libcamera.org/">libcamera website</a>
 */
package in.virit.libcamera4j;
