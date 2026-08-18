package com.menusaas.shared.api;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}