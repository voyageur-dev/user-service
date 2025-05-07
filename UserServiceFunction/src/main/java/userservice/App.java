package userservice;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.google.gson.*;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import userservice.models.GetUserResponse;
import userservice.models.RenewTokenResponse;
import userservice.models.SignInResponse;
import userservice.models.SignUpResponse;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Map;

/**
 * Handler for requests to Lambda function.
 */
public class App implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final String SIGN_IN_PATH = "POST /users/signIn";
    private static final String SIGN_UP_PATH = "POST /users";
    private static final String CONFIRM_SIGN_UP_PATH = "POST /users/code";
    private static final String RESEND_CONFIRM_PATH = "POST /users/resend";
    private static final String GET_USER_PATH = "GET /users/{username}";
    private static final String RENEW_TOKEN_PATH = "PUT /users/token";
    private static final String FORGOT_PASSWORD_PATH = "POST /users/forgot";
    private static final String CONFIRM_FORGOT_PASSWORD_PATH = "POST /users/forgot/code";

    private static final String USERNAME = "username";
    
    private final String userPoolId;
    private final String clientId;
    private final CognitoIdentityProviderClient cognitoClient;
    private final Gson gson;

    public App() {
        this.userPoolId = System.getenv("USER_POOL_ID");
        this.clientId = System.getenv("CLIENT_ID");
        this.cognitoClient = CognitoIdentityProviderClient.builder().region(Region.US_EAST_1).build();
        this.gson = new GsonBuilder().registerTypeAdapter(Instant.class, new InstantTypeAdapter()).create();
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        String path = event.getRouteKey();

        return switch (path) {
            case SIGN_UP_PATH -> signUp(event);
            case SIGN_IN_PATH -> signIn(event);
            case CONFIRM_SIGN_UP_PATH -> confirmSignUp(event);
            case RESEND_CONFIRM_PATH -> resendCode(event);
            case GET_USER_PATH -> getUser(event);
            case RENEW_TOKEN_PATH -> renewToken(event);
            case FORGOT_PASSWORD_PATH -> forgotPassword(event);
            case CONFIRM_FORGOT_PASSWORD_PATH -> confirmForgotPassword(event);
            default -> APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.NOT_FOUND)
                    .withBody("Path Not Found")
                    .build();
        };
    }

    private APIGatewayV2HTTPResponse getUser(APIGatewayV2HTTPEvent event) {
        try {
            GetUserResponse userResponse = getUserById(event.getPathParameters().get(USERNAME));

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.OK)
                    .withBody(gson.toJson(userResponse))
                    .build();
        } catch (Exception e) {
            System.out.println("Error getting user info: " + e.getMessage());
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    private APIGatewayV2HTTPResponse signIn(APIGatewayV2HTTPEvent event) {
        try {
            String body = event.getBody();
            JsonObject jsonBody = gson.fromJson(body, JsonObject.class);
            String username = jsonBody.get(USERNAME).getAsString();
            String password = jsonBody.get("password").getAsString();

            AdminInitiateAuthRequest authRequest = AdminInitiateAuthRequest.builder()
                    .userPoolId(userPoolId)
                    .clientId(clientId)
                    .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                    .authParameters(Map.of(
                            "USERNAME", username,
                            "PASSWORD", password
                    ))
                    .build();

            AdminInitiateAuthResponse authResponse = cognitoClient.adminInitiateAuth(authRequest);

            AuthenticationResultType authResult = authResponse.authenticationResult();
            SignInResponse signInResponse = new SignInResponse(
                    authResult.accessToken(),
                    authResult.refreshToken(),
                    authResult.idToken(),
                    authResult.tokenType(),
                    authResult.expiresIn()
            );

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.OK)
                    .withBody(gson.toJson(signInResponse))
                    .build();

        } catch (UserNotConfirmedException e) {
            System.out.println("Error during sign in: User email not verified");
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.FORBIDDEN)
                    .build();
        } catch (NotAuthorizedException e) {
            System.out.println("Error during sign in: Invalid username or password");
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.UNAUTHORIZED)
                    .build();
        } catch (Exception e) {
            System.out.println("Error during sign in: " + e.getMessage());
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    private APIGatewayV2HTTPResponse signUp(APIGatewayV2HTTPEvent event) {
        try {
            String body = event.getBody();
            JsonObject jsonBody = gson.fromJson(body, JsonObject.class);
            String username = jsonBody.get(USERNAME).getAsString();
            String password = jsonBody.get("password").getAsString();

            SignUpRequest signUpRequest = SignUpRequest.builder()
                    .clientId(clientId)
                    .username(username)
                    .password(password)
                    .build();

            SignUpResponse signUpResponse = new SignUpResponse(cognitoClient.signUp(signUpRequest).userSub());

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.CREATED)
                    .withBody(gson.toJson(signUpResponse))
                    .build();

        } catch (UsernameExistsException e) {
            String body = event.getBody();
            JsonObject jsonBody = gson.fromJson(body, JsonObject.class);
            String username = jsonBody.get(USERNAME).getAsString();

            GetUserResponse userResponse = getUserById(username);
            if (UserStatusType.UNCONFIRMED.toString().equals(userResponse.userStatus())) {
                System.out.println("Error during sign up: User email not verified");
                return APIGatewayV2HTTPResponse.builder()
                        .withStatusCode(HttpStatusCode.FORBIDDEN)
                        .build();
            }

            System.out.println("Error during sign up: User already exist");
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.BAD_REQUEST)
                    .build();
        } catch (Exception e) {
            System.out.println("Error during sign up: " + e.getMessage());
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    private APIGatewayV2HTTPResponse confirmSignUp(APIGatewayV2HTTPEvent event) {
        try {
            String body = event.getBody();
            JsonObject jsonBody = gson.fromJson(body, JsonObject.class);
            String username = jsonBody.get(USERNAME).getAsString();
            String code = jsonBody.get("code").getAsString();

            cognitoClient.confirmSignUp(ConfirmSignUpRequest.builder()
                    .clientId(clientId)
                    .username(username)
                    .confirmationCode(code)
                    .build()
            );

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.OK)
                    .build();

        } catch (Exception e) {
            System.out.println("Error during sign up confirmation: " + e.getMessage());
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    private APIGatewayV2HTTPResponse resendCode(APIGatewayV2HTTPEvent event) {
        try {
            String body = event.getBody();
            JsonObject jsonBody = gson.fromJson(body, JsonObject.class);
            String username = jsonBody.get(USERNAME).getAsString();

            cognitoClient.resendConfirmationCode(ResendConfirmationCodeRequest.builder()
                    .clientId(clientId)
                    .username(username)
                    .build()
            );

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.OK)
                    .build();

        } catch (Exception e) {
            System.out.println("Error during resend code: " + e.getMessage());
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    private APIGatewayV2HTTPResponse renewToken(APIGatewayV2HTTPEvent event) {
        try {
            String body = event.getBody();
            JsonObject jsonBody = gson.fromJson(body, JsonObject.class);
            String refreshToken = jsonBody.get("refreshToken").getAsString();

            InitiateAuthRequest refreshRequest = InitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                    .clientId(clientId)
                    .authParameters(Map.of("REFRESH_TOKEN", refreshToken))
                    .build();

            InitiateAuthResponse refreshResponse = cognitoClient.initiateAuth(refreshRequest);

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.CREATED)
                    .withBody(gson.toJson(new RenewTokenResponse(refreshResponse.authenticationResult().accessToken())))
                    .build();

        } catch (Exception e) {
            System.out.println("Error during refresh token: " + e.getMessage());
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    private APIGatewayV2HTTPResponse forgotPassword(APIGatewayV2HTTPEvent event) {
        try {
            String body = event.getBody();
            JsonObject jsonBody = gson.fromJson(body, JsonObject.class);
            String username = jsonBody.get(USERNAME).getAsString();

            ForgotPasswordRequest forgotPasswordRequest = ForgotPasswordRequest.builder()
                    .clientId(clientId)
                    .username(username)
                    .build();

            cognitoClient.forgotPassword(forgotPasswordRequest);

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.OK)
                    .withBody("Password reset code sent successfully")
                    .build();

        } catch (Exception e) {
            System.out.println("Error during forgot password: " + e.getMessage());
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    private APIGatewayV2HTTPResponse confirmForgotPassword(APIGatewayV2HTTPEvent event) {
        try {
            String body = event.getBody();
            JsonObject jsonBody = gson.fromJson(body, JsonObject.class);
            String username = jsonBody.get(USERNAME).getAsString();
            String confirmationCode = jsonBody.get("confirmationCode").getAsString();
            String newPassword = jsonBody.get("newPassword").getAsString();

            ConfirmForgotPasswordRequest confirmForgotPasswordRequest = ConfirmForgotPasswordRequest.builder()
                    .clientId(clientId)
                    .username(username)
                    .confirmationCode(confirmationCode)
                    .password(newPassword)
                    .build();

            cognitoClient.confirmForgotPassword(confirmForgotPasswordRequest);

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.OK)
                    .withBody("Password has been reset successfully")
                    .build();

        } catch (Exception e) {
            System.out.println("Error during confirm forgot password: " + e.getMessage());
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    private GetUserResponse getUserById(String username) {
        AdminGetUserRequest getUserRequest = AdminGetUserRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .build();

        AdminGetUserResponse getUserResponse = cognitoClient.adminGetUser(getUserRequest);

        GetUserResponse userResponse = new GetUserResponse(
                getUserResponse.username(),
                getUserResponse.userStatusAsString(),
                getUserResponse.userCreateDate(),
                getUserResponse.userLastModifiedDate()
        );

        return userResponse;
    }

    private static class InstantTypeAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        @Override
        public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }

        @Override
        public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            return Instant.parse(json.getAsString());
        }
    }
}
