package io.github.wasabithumb.jarstrap.packager.error;

public class PackagerException extends RuntimeException {

    public PackagerException(String message) {
        super(message);
    }

    public PackagerException(String message, Throwable cause) {
        super(message, cause);
    }

}
