package in.virit;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serves generated timelapse videos via Vert.x route.
 * Uses Vert.x sendFile which handles HTTP Range requests automatically,
 * required by Safari/iOS for video playback.
 */
@ApplicationScoped
public class VideoEndpoint {

    private static final Path TIMELAPSE_DIR = Path.of("timelapse");

    void initRoutes(@Observes Router router) {
        router.get("/api/videos/:filename").handler(this::serveVideo);
    }

    private void serveVideo(RoutingContext ctx) {
        String filename = ctx.pathParam("filename");
        Path videoPath = TIMELAPSE_DIR.resolve(filename);

        if (!videoPath.normalize().startsWith(TIMELAPSE_DIR.normalize())) {
            ctx.response().setStatusCode(403).end();
            return;
        }

        if (Files.exists(videoPath) && Files.isRegularFile(videoPath)) {
            String contentType = filename.endsWith(".mp4") ? "video/mp4" : "video/x-msvideo";
            ctx.response().putHeader("Content-Type", contentType);
            ctx.response().sendFile(videoPath.toAbsolutePath().toString());
        } else {
            ctx.response().setStatusCode(404).end();
        }
    }
}
