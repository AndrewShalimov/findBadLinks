package org.posts.exceptions;

public class WordpressException extends Exception {


    public WordpressException(Throwable e) {
        super(e);
    }

    public WordpressException(String message) {
        super(message);
    }
}
