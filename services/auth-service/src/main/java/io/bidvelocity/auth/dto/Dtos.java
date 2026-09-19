package io.bidvelocity.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class Dtos {

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 100) String firstName,
            @NotBlank @Size(min = 1, max = 100) String lastName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            String role,                    // USER | SELLER (never ADMIN)
            @AssertTrue(message = "Terms must be accepted") boolean termsAccepted
    ) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record UserDto(long id, String email, String firstName, String lastName,
                          String status, java.util.List<String> roles, String provider) {}

    public record AuthResponse(String accessToken, String refreshToken, long expiresInSeconds, UserDto user) {}
}
