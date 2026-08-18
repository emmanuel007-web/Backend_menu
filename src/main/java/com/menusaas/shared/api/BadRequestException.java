package com.menusaas.shared.api;

public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}