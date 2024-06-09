package com.felix.service;


import lombok.Getter;

@Getter
public class Response<T> {

    private final T data;
    private final int errorCode;
    private final String errorMessage;

    public static <T> Response<T> success(T data) {
        return new Response<>(data, 0, null);
    }

    public static Response error(int errorCode) {
        return new Response<>(null, errorCode, null);
    }

    public static Response error(int errorCode, String errorMessage) {
        return new Response<>(null, errorCode, errorMessage);
    }

    public static <T> Response<T> error(int errorCode, String errorMessage, T data) {
        return new Response<>(data, errorCode, errorMessage);
    }

    private Response(T data, int errorCode, String errorMessage) {
        this.data = data;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public boolean isSuccessful() {
        return errorCode == 0;
    }

}
