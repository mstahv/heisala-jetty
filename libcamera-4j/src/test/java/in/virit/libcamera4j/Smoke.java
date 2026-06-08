package in.virit.libcamera4j;

import java.util.List;

/**
 * Standalone, camera-free smoke check for the FFM binding layer. Not a JUnit test
 * (it requires the libcamera runtime + native library on the loader path, which is
 * only available on a Pi or in the builder container). Run manually:
 *
 * <pre>
 *   java --enable-native-access=ALL-UNNAMED -cp target/classes in.virit.libcamera4j.Smoke
 * </pre>
 *
 * Exercises every marshalling kind without needing a camera: string-into-buffer
 * (version), long return (create), int return (start/count), void (stop/destroy).
 */
public final class Smoke {
    public static void main(String[] args) {
        System.out.println("libcamera version: " + CameraManager.version());
        try (CameraManager manager = CameraManager.create()) {
            manager.start();
            List<Camera> cameras = manager.cameras();
            System.out.println("cameras found: " + cameras.size());
            for (Camera c : cameras) {
                System.out.println("  - " + c.id());
            }
        }
        System.out.println("FFM smoke OK");
    }
}
