package com.hub.exception;

public class ForbiddenException extends BizException {

    public ForbiddenException(String message) {
        super(403, message);
    }
}
