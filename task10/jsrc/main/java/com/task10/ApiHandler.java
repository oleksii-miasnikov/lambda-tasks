package com.task10;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;

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
public class ApiHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	private final CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.create();
	private final String userPoolId = System.getenv("COGNITO_ID");

	public Map<String, Object> handleRequest(Map<String, Object> requestEvent, Context context) {
		Map<String, Object> response = new HashMap<>();

		context.getLogger().log("handleRequest started");
		context.getLogger().log("COGNITO_ID: " + System.getenv("COGNITO_ID"));
		context.getLogger().log("CLIENT_ID: " + System.getenv("CLIENT_ID"));

		String firstName = (String) requestEvent.get("firstName");
		String lastName = (String) requestEvent.get("lastName");
		String email = (String) requestEvent.get("email");
		String password = (String) requestEvent.get("password");

		context.getLogger().log("data: " + firstName + ", " + lastName + ", " + email + ", " + password);


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

		// Create the user
		AdminCreateUserResponse createUserResponse = cognitoClient.adminCreateUser(request);

		// Build success response
		response.put("statusCode", 200);
		response.put("body", "User created successfully: " + createUserResponse.user().username());

		return response;
	}
}
