package io.bidvelocity.auth.web;

import org.springframework.http.HttpStatus;

/** Domain errors carrying the unified API error contract. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    public static ApiException duplicateEmail() {
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "An account with this email already exists");
    }
    public static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Incorrect email or password");
    }
    public static ApiException suspended() {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "This account has been suspended by an administrator");
    }
    public static ApiException invalidRefresh() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token invalid, expired or reused");
    }
    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", what + " not found");
    }
}
