package helloworld;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

/**
 * Handler for requests to Lambda function.
 */
public class App implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final String userPoolId;
    private final CognitoIdentityProviderClient cognitoClient;
    private final Gson gson;

    public App() {
        this.userPoolId = System.getenv("USER_POOL_ID");
        this.cognitoClient = CognitoIdentityProviderClient.builder().region(Region.US_EAST_1).build();
        this.gson = new Gson();
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        String path = event.getRawPath();
        String httpMethod = event.getRequestContext().getHttp().getMethod();

        if ("/users".equals(path) && "POST".equalsIgnoreCase(httpMethod)) {
            return createUser(event);
        } else if (path.startsWith("/users/") && "GET".equalsIgnoreCase(httpMethod)) {
            String username = path.substring("/users/".length());
            return getUserInfo(username);
        }

        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(HttpStatusCode.NOT_FOUND)
                .withBody("Path Not Found")
                .build();
    }

    private APIGatewayV2HTTPResponse createUser(APIGatewayV2HTTPEvent event) {
        try {
            String body = event.getBody();
            JsonObject jsonBody = gson.fromJson(body, JsonObject.class);
            String username = jsonBody.get("username").getAsString();
            String email = jsonBody.get("email").getAsString();
            String password = jsonBody.get("password").getAsString();

            AdminCreateUserRequest createUserRequest =  AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(username)
                    .userAttributes(
                            AttributeType.builder().name("email").value(email).build()
                    )
                    .temporaryPassword(password)
                    .messageAction(MessageActionType.RESEND)
                    .build();

            AdminCreateUserResponse createUserResponse = cognitoClient.adminCreateUser(createUserRequest);

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.CREATED)
                    .withBody(gson.toJson(createUserResponse.user()))
                    .build();
        } catch (Exception e) {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("Error creating user: " + e.getMessage())
                    .build();
        }
    }

    private APIGatewayV2HTTPResponse getUserInfo(String username) {
        try {
            AdminGetUserRequest getUserRequest = AdminGetUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(username)
                    .build();

            AdminGetUserResponse getUserResponse = cognitoClient.adminGetUser(getUserRequest);

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.OK)
                    .withBody(gson.toJson(getUserResponse))
                    .build();
        } catch (Exception e) {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("Error getting user info: " + e.getMessage())
                    .build();
        }
    }
}
