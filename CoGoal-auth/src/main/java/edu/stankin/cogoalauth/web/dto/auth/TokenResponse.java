package edu.stankin.cogoalauth.web.dto.auth;

/**
 * @param accessToken      JWT for {@code Authorization: Bearer ...} in main and here
 * @param tokenType        always {@code Bearer}
 * @param expiresIn        access token lifetime in seconds
 * @param refreshToken     opaque token for {@code /refresh}; single use
 * @param refreshExpiresIn refresh token lifetime in seconds
 */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn,
        UserResponse user
) {

    public TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken, long refreshExpiresIn) {
        this(accessToken, tokenType, expiresIn, refreshToken, refreshExpiresIn, null);
    }

    public static final String BEARER = "Bearer";

    @Override
    public String toString() {
        return "TokenResponse(***)";
    }
}
