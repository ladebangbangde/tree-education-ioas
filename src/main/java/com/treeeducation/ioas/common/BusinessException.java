package com.treeeducation.ioas.common;

/** Domain exception mapped to an API error response. */
public class BusinessException extends RuntimeException {
    private final int code;
    public BusinessException(int code, String message) { super(message); this.code = code; }
    public int code() { return code; }
    public static BusinessException notFound(String message) { return new BusinessException(404, message); }
    public static BusinessException badRequest(String message) { return new BusinessException(400, message); }
}
