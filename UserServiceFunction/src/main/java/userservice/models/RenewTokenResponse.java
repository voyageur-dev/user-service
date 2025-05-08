package userservice.models;

public record RenewTokenResponse(
        String accessToken,
        String refreshToken,
        Integer expiresIn
) {}
