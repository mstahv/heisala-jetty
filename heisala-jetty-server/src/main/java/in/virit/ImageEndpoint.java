package in.virit;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * REST endpoint to serve timelapse images
 */
@jakarta.ws.rs.Path("/images")
public class ImageEndpoint {
    
    private static final Path TIMELAPSE_DIR = Path.of("timelapse");
    
    @GET
    @jakarta.ws.rs.Path("{path:.+}")
    @Produces("image/jpeg")
    public Response serveImage(@PathParam("path") String path) {
        try {
            Path imagePath = TIMELAPSE_DIR.resolve(path);
            
            // Security check: ensure the path is within the timelapse directory
            if (!imagePath.normalize().startsWith(TIMELAPSE_DIR.normalize())) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }
            
            if (Files.exists(imagePath) && Files.isRegularFile(imagePath)) {
                byte[] imageData = Files.readAllBytes(imagePath);
                return Response.ok(imageData).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } catch (IOException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }
}