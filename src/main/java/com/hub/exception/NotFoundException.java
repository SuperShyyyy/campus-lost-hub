package com.hub.exception;

public class NotFoundException extends BizException {

    public NotFoundException(String message) {
        super(404, message);
    }
}