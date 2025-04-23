package userservice.models;

public record SignInResponse(String accessToken,
                            String refreshToken,
                            String idToken,
                            String tokenType,
                            Integer expiresIn) {
}
