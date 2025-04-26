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
import userservice.models.SignInResponse;

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
            default -> APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.NOT_FOUND)
                    .withBody("Path Not Found")
                    .build();
        };
    }

    private APIGatewayV2HTTPResponse getUser(APIGatewayV2HTTPEvent event) {
        try {
            AdminGetUserRequest getUserRequest = AdminGetUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(event.getPathParameters().get("username"))
                    .build();

            AdminGetUserResponse getUserResponse = cognitoClient.adminGetUser(getUserRequest);

            GetUserResponse userResponse = new GetUserResponse(
                    getUserResponse.username(),
                    getUserResponse.userStatusAsString(),
                    getUserResponse.userCreateDate(),
                    getUserResponse.userLastModifiedDate()
            );

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.OK)
                    .withBody(gson.toJson(userResponse))
                    .build();
        } catch (Exception e) {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("Error getting user info: " + e.getMessage())
                    .build();
        }
    }

    private APIGatewayV2HTTPResponse signIn(APIGatewayV2HTTPEvent event) {
        try {
            String body = event.getBody();
            JsonObject jsonBody = gson.fromJson(body, JsonObject.class);
            String username = jsonBody.get("username").getAsString();
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

        } catch (NotAuthorizedException e) {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.UNAUTHORIZED)
                    .withBody("Invalid username or password")
                    .build();
        } catch (Exception e) {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("Error during sign in: " + e.getMessage())
                    .build();
        }
    }

    private APIGatewayV2HTTPResponse signUp(APIGatewayV2HTTPEvent event) {
        try {
            String body = event.getBody();
            JsonObject jsonBody = gson.fromJson(body, JsonObject.class);
            String username = jsonBody.get("username").getAsString();
            String password = jsonBody.get("password").getAsString();

            SignUpRequest signUpRequest = SignUpRequest.builder()
                    .clientId(clientId)
                    .username(username)
                    .password(password)
                    .build();

            SignUpResponse signUpResponse = cognitoClient.signUp(signUpRequest);

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.CREATED)
                    .withBody(signUpResponse.userSub())
                    .build();

        } catch (Exception e) {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("Error during sign up: " + e.getMessage())
                    .build();
        }
    }

    private APIGatewayV2HTTPResponse confirmSignUp(APIGatewayV2HTTPEvent event) {
        try {
            String body = event.getBody();
            JsonObject jsonBody = gson.fromJson(body, JsonObject.class);
            String username = jsonBody.get("username").getAsString();
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
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("Error during sign up confirmation: " + e.getMessage())
                    .build();
        }
    }

    private APIGatewayV2HTTPResponse resendCode(APIGatewayV2HTTPEvent event) {
        try {
            String body = event.getBody();
            JsonObject jsonBody = gson.fromJson(body, JsonObject.class);
            String username = jsonBody.get("username").getAsString();

            cognitoClient.resendConfirmationCode(ResendConfirmationCodeRequest.builder()
                    .clientId(clientId)
                    .username(username)
                    .build()
            );

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.OK)
                    .build();

        } catch (Exception e) {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("Error during resend code: " + e.getMessage())
                    .build();
        }
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
