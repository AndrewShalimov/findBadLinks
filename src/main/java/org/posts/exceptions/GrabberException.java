package org.posts.exceptions;

public class GrabberException extends Exception {

    public GrabberException(Throwable e) {
        super(e);
    }

    public GrabberException(String message) {
        super(message);
    }
}
