package com.task10;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
//import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
//import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordResponse;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.HashMap;
import java.util.Map;

import static com.syndicate.deployment.model.environment.ValueTransformer.USER_POOL_NAME_TO_CLIENT_ID;
import static com.syndicate.deployment.model.environment.ValueTransformer.USER_POOL_NAME_TO_USER_POOL_ID;

/*
lambdas_alias_name: learn
tables_table: Tables
reservations_table: Reservations
booking_userpool: simple-booking-userpool
 */
@LambdaHandler(
    lambdaName = "api_handler",
	roleName = "api_handler-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@DependsOn(resourceType = ResourceType.COGNITO_USER_POOL, name = "${booking_userpool}")
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "REGION", value = "${region}"),
		@EnvironmentVariable(key = "COGNITO_ID", value = "${booking_userpool}", valueTransformer = USER_POOL_NAME_TO_USER_POOL_ID),
		@EnvironmentVariable(key = "CLIENT_ID", value = "${booking_userpool}", valueTransformer = USER_POOL_NAME_TO_CLIENT_ID)
})
public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private final CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.create();
	private final String userPoolId = System.getenv("COGNITO_ID");
	private final String clientId = System.getenv("CLIENT_ID");
	private final String tableReservations = System.getenv("reservations_table");
	private final String tableTables = System.getenv("tables_table");

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {

		context.getLogger().log("handleRequest started");
		String method = requestEvent.getHttpMethod();
		String path = requestEvent.getPath();
		String body = requestEvent.getBody();
		context.getLogger().log("Method --> " + method);
		context.getLogger().log("Path --> " + path);
		context.getLogger().log("body --> " + body);

		try {
			ObjectMapper objectMapper = new ObjectMapper();

			Map<String, Object> requestBody = objectMapper.readValue(body, Map.class);
			String jsonResponse = "";

			if ("/signup".equals(path) && "POST".equals(method)) {
				context.getLogger().log("/signup POST");
				jsonResponse = objectMapper.writeValueAsString(signUpHandler(requestBody, context));
			} else if ("/signin".equals(path) && "POST".equals(method)) {
				context.getLogger().log("/signin POST");
				jsonResponse = objectMapper.writeValueAsString(signInHandler(requestBody, context));
			} else if ("/tables ".equals(path) && "POST".equals(method)) {
				context.getLogger().log("/tables  POST");
				jsonResponse = objectMapper.writeValueAsString(tablesPost(requestBody, context));
			}


			APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
			Map<String, Object> responseBody = new HashMap<>();
			responseBody.put("event", jsonResponse);
			responseBody.put("statusCode", 200);
			response.setBody(objectMapper.writeValueAsString(responseBody));
			context.getLogger().log("handleRequest finished");

			return response;

		} catch (Exception exception) {
			exception.printStackTrace();
			return new APIGatewayProxyResponseEvent()
					.withStatusCode(500)
					.withBody("{\"error\": \"" + exception.getMessage() + "\"}");
		}
	}

	private Map<String, Object> signUpHandler (Map<String, Object> body, Context context){

		context.getLogger().log("signUpHandler started");

		Map<String, Object> response = new HashMap<>();
		String firstName = (String) body.get("firstName");
		String lastName = (String) body.get("lastName");
		String email = (String) body.get("email");
		String password = (String) body.get("password");

		AdminCreateUserRequest request = AdminCreateUserRequest.builder()
				.userPoolId(userPoolId)
				.username(email)
				.temporaryPassword(password)
				.messageAction("SUPPRESS")
				.userAttributes(
						AttributeType.builder().name("email").value(email).build(),
						AttributeType.builder().name("given_name").value(firstName).build(),
						AttributeType.builder().name("family_name").value(lastName).build()
				)
				.build();

		AdminCreateUserResponse createUserResponse = cognitoClient.adminCreateUser(request);

		AdminSetUserPasswordRequest setPasswordRequest = AdminSetUserPasswordRequest.builder()
				.userPoolId(userPoolId)
				.username(email)
				.password(password)
				.permanent(true) // Set password as permanent
				.build();

		AdminSetUserPasswordResponse setPasswordResponse = cognitoClient.adminSetUserPassword(setPasswordRequest);

		response.put("statusCode", 200);
		response.put("body", "User created successfully: " + createUserResponse.user().username());

		return response;
	}

	private Map<String, Object> signInHandler (Map<String, Object> body, Context context){

		context.getLogger().log("signInHandler started");
		ObjectMapper objectMapper = new ObjectMapper();
		Map<String, Object> response = new HashMap<>();
		try {
			String email = (String) body.get("email");
			String password = (String) body.get("password");
			context.getLogger().log("data: " + email + ", " + password);
			// Authenticate the user using AdminInitiateAuth
			AdminInitiateAuthRequest authRequest = AdminInitiateAuthRequest.builder()
					.userPoolId(userPoolId)
					.clientId(clientId)
					.authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
					.authParameters(Map.of(
							"USERNAME", email,
							"PASSWORD", password
					))
					.build();
			context.getLogger().log("authRequest created: " + authRequest);
			AdminInitiateAuthResponse authResponse = cognitoClient.adminInitiateAuth(authRequest);

			context.getLogger().log("authResponse: " + authResponse);

			// Extract the access token from the response
			String accessToken = authResponse.authenticationResult().accessToken();

			context.getLogger().log("accessToken" + accessToken);

			// Return a successful response
			response.put("statusCode", 200);
			response.put("body", objectMapper.writeValueAsString(Map.of("accessToken", accessToken)));

		} catch (Exception e) {
			response.put("statusCode", 400);
			response.put("error", e.getMessage());
		}

		return response;
	}

	private Map<String, Object> tablesPost (Map<String, Object> body, Context context){
		context.getLogger().log("tablesPost started");
		ObjectMapper objectMapper = new ObjectMapper();
		Map<String, Object> response = new HashMap<>();

		String id = (String) body.get("id");
		String number = (String) body.get("number");
		String places = (String) body.get("places");
		Boolean isVip = (Boolean) body.get("isVip");
		String minOrder = (String) body.get("minOrder");

		context.getLogger().log("data: " + id + ", "+ number + ", "+ places + ", "+ isVip + ", "+ minOrder);

		// Create forecast data
		//String id = UUID.randomUUID().toString();
		Map<String, AttributeValue> item = new HashMap<>();
		item.put("id", AttributeValue.builder().n(id).build());
		item.put("number", AttributeValue.builder().n(number).build());
		item.put("places", AttributeValue.builder().n(places).build());
		item.put("isVip", AttributeValue.builder().bool(isVip).build());

		// Add optional field minOrder if present
		if (minOrder != null) {
			item.put("minOrder", AttributeValue.builder().n(String.valueOf(minOrder)).build());
		}


		context.getLogger().log("table item created : " + item);

		// Save to DynamoDB
		PutItemRequest putItemRequest = PutItemRequest.builder()
				.tableName(tableTables)
				.item(item)
				.build();
		DynamoDbClient dynamoDbClient = DynamoDbClient.create();
		dynamoDbClient.putItem(putItemRequest);
		context.getLogger().log("forecast saved to the db");

		// Step 4: Build a successful response
		response.put("statusCode", 200);
		response.put("id", id);

		return response;
	}
}
