package in.virit.libcamera4j;

/**
 * Exception thrown when a libcamera operation fails.
 */
public class LibCameraException extends RuntimeException {

    private final int errorCode;

    /**
     * Creates a new exception with the given message.
     *
     * @param message the error message
     */
    public LibCameraException(String message) {
        super(message);
        this.errorCode = 0;
    }

    /**
     * Creates a new exception with the given message and error code.
     *
     * @param message the error message
     * @param errorCode the native error code
     */
    public LibCameraException(String message, int errorCode) {
        super(message + " (error code: " + errorCode + ")");
        this.errorCode = errorCode;
    }

    /**
     * Creates a new exception with the given message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public LibCameraException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = 0;
    }

    /**
     * Returns the native error code, if available.
     *
     * @return the error code, or 0 if not applicable
     */
    public int errorCode() {
        return errorCode;
    }

    /**
     * Creates an exception for a failed operation.
     *
     * @param operation the operation that failed
     * @param errorCode the native error code
     * @return the exception
     */
    public static LibCameraException forOperation(String operation, int errorCode) {
        String message = switch (errorCode) {
            case -1 -> operation + " failed: Operation not permitted";
            case -2 -> operation + " failed: No such file or directory";
            case -5 -> operation + " failed: I/O error";
            case -11 -> operation + " failed: Try again";
            case -12 -> operation + " failed: Out of memory";
            case -13 -> operation + " failed: Permission denied";
            case -16 -> operation + " failed: Device or resource busy";
            case -19 -> operation + " failed: No such device";
            case -22 -> operation + " failed: Invalid argument";
            case -28 -> operation + " failed: No space left on device";
            default -> operation + " failed with error code " + errorCode;
        };
        return new LibCameraException(message, errorCode);
    }
}
