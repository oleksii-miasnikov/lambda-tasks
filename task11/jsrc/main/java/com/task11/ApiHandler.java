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
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
		@EnvironmentVariable(key = "TABLES_TABLE", value = "${tables_table}"),
		@EnvironmentVariable(key = "RESERVATIONS_TABLE", value = "${reservations_table}"),
		@EnvironmentVariable(key = "COGNITO_ID", value = "${booking_userpool}", valueTransformer = USER_POOL_NAME_TO_USER_POOL_ID),
		@EnvironmentVariable(key = "CLIENT_ID", value = "${booking_userpool}", valueTransformer = USER_POOL_NAME_TO_CLIENT_ID)
})
public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private final CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.create();
	private final String userPoolId = System.getenv("COGNITO_ID");
	private final String clientId = System.getenv("CLIENT_ID");
	private final String tableReservations = System.getenv("RESERVATIONS_TABLE");
	private final String tableTables = System.getenv("TABLES_TABLE");
	private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {

		context.getLogger().log("handleRequest started");
		context.getLogger().log("requestEvent --> " + requestEvent);
		String method = requestEvent.getHttpMethod();
		String path = requestEvent.getPath();
		String body = requestEvent.getBody();
		context.getLogger().log("Method --> " + method);
		context.getLogger().log("Path --> " + path);
		context.getLogger().log("body --> " + body);

		try {
			Map<String, Object> methodResponse =  new HashMap<>();

			if ("/reservations".equals(path) && "GET".equals(method)) {
				context.getLogger().log("/reservations GET");
				methodResponse = reservationsGetHandler(context);
			} else if ("/tables".equals(path) && "GET".equals(method)) {
				context.getLogger().log("/tables GET");
				methodResponse = tablesGetHandler(context);
			} else if (path.contains("/tables/") && "GET".equals(method)) {
				String tableId = path.substring(8);
				context.getLogger().log("/tables/" + tableId + "GET");
				methodResponse = getTableByTableId(tableId, context);
			} else {
				Map<String, Object> requestBody = objectMapper.readValue(body, Map.class);

				if ("/signup".equals(path) && "POST".equals(method)) {
					context.getLogger().log("/signup POST");
					methodResponse = signUpHandler(requestBody, context);
				} else if ("/signin".equals(path) && "POST".equals(method)) {
					context.getLogger().log("/signin POST");
					methodResponse = signInHandler(requestBody, context);
				} else if ("/tables".equals(path) && "POST".equals(method)) {
					context.getLogger().log("/tables POST");
					methodResponse = tablesPostHandler(requestBody, context);
				}  else if ("/reservations".equals(path) && "POST".equals(method)) {
					context.getLogger().log("/reservations POST");
					methodResponse = reservationsPostHandler(requestBody, context);
				}
			}

			APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
			response.setStatusCode((Integer) methodResponse.get("statusCode"));
			if (methodResponse.get("body") != null) {
				response.setBody(objectMapper.writeValueAsString(methodResponse.get("body")));
			}
			context.getLogger().log("handleRequest finished");

			return response;

		} catch (Exception exception) {
			exception.printStackTrace();
			return new APIGatewayProxyResponseEvent()
					.withStatusCode(500)
					.withBody("{\"error\": \"" + exception.getMessage() + "\"}");
		}
	}

	private Map<String, Object> signUpHandler(Map<String, Object> body, Context context){

		context.getLogger().log("signUpHandler started");
		Map<String, Object> response = new HashMap<>();

		// getting fields from request body
		String firstName = (String) body.get("firstName");
		String lastName = (String) body.get("lastName");
		String email = (String) body.get("email");
		String password = (String) body.get("password");

		try {
			// creating cognito request
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

			// creating cognito user
			AdminCreateUserResponse createUserResponse = cognitoClient.adminCreateUser(request);

			// setting permanent password
			AdminSetUserPasswordRequest setPasswordRequest = AdminSetUserPasswordRequest.builder()
					.userPoolId(userPoolId)
					.username(email)
					.password(password)
					.permanent(true)
					.build();

			AdminSetUserPasswordResponse setPasswordResponse = cognitoClient.adminSetUserPassword(setPasswordRequest);

			// creating response
			response.put("statusCode", 200);

		} catch (Exception exception) {
			response.put("statusCode", 400);
			response.put("body", exception.getMessage());
		}

		return response;
	}

	private Map<String, Object> signInHandler(Map<String, Object> body, Context context){

		context.getLogger().log("signInHandler started");
		Map<String, Object> response = new HashMap<>();

		// getting fields from request body
		String email = (String) body.get("email");
		String password = (String) body.get("password");

		try {
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

			AdminInitiateAuthResponse authResponse = cognitoClient.adminInitiateAuth(authRequest);

			// Extract the access token from the response
			String accessToken = authResponse.authenticationResult().idToken();

			// Return a successful response
			response.put("statusCode", 200);
			response.put("body", Map.of("accessToken", accessToken));

		} catch (Exception exception) {
			response.put("statusCode", 400);
			response.put("body", exception.getMessage());
		}

		return response;
	}

	private Map<String, Object> tablesPostHandler(Map<String, Object> body, Context context){

		context.getLogger().log("tablesPost started");
		Map<String, Object> response = new HashMap<>();

		String id = String.valueOf(body.get("id"));
		String number = String.valueOf(body.get("number"));
		String places = String.valueOf(body.get("places"));
		Boolean isVip = (Boolean) body.get("isVip");

		try {
			// Create forecast data
			//String id = UUID.randomUUID().toString();
			Map<String, AttributeValue> item = new HashMap<>();
			item.put("id", AttributeValue.builder().s(String.valueOf(id)).build());
			item.put("number", AttributeValue.builder().n(number).build());
			item.put("places", AttributeValue.builder().n(places).build());
			item.put("isVip", AttributeValue.builder().bool(isVip).build());

			// Add optional field minOrder if present
			if (body.get("minOrder") != null) {
				item.put("minOrder", AttributeValue.builder().n(String.valueOf(String.valueOf(body.get("minOrder")))).build());
			}

			context.getLogger().log("table item created : " + item);
			context.getLogger().log("table: " + tableTables);

			// Save to DynamoDB
			PutItemRequest putItemRequest = PutItemRequest.builder()
					.tableName(tableTables)
					.item(item)
					.build();

			dynamoDbClient.putItem(putItemRequest);
			context.getLogger().log("table item saved to the db");

			// Build a successful response
			response.put("statusCode", 200);
			response.put("body", Map.of("id",Integer.parseInt(id)));
		} catch (Exception exception) {
			response.put("statusCode", 400);
			response.put("body", exception.getMessage());
		}
		return response;
	}

	private Map<String, Object> tablesGetHandler(Context context){

		context.getLogger().log("tablesGet started");

		Map<String, Object> response = new HashMap<>();

		try {
			ScanRequest scanRequest = ScanRequest.builder()
					.tableName(tableTables)
					.build();

			ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);
			context.getLogger().log("scanResponse " + scanResponse);

			// Build the response JSON
			List<Map<String, Object>> items = new ArrayList<>();

			for (Map<String, AttributeValue> item : scanResponse.items()) {
				Map<String, Object> tableRecord = new HashMap<>();
				tableRecord.put("id", Integer.parseInt(item.get("id").s()));
				tableRecord.put("number", Integer.parseInt(item.get("number").n()));
				tableRecord.put("places", Integer.parseInt(item.get("places").n()));
				tableRecord.put("isVip", Boolean.parseBoolean(item.get("isVip").bool().toString()));

				if (item.containsKey("minOrder")) {
					tableRecord.put("minOrder", Integer.parseInt(item.get("minOrder").n()));
				}

				context.getLogger().log("tableRecord " + tableRecord);
				items.add(tableRecord);
			}
			response.put("statusCode", 200);
			response.put("body", Map.of("tables", items));

		} catch (Exception exception) {
			response.put("statusCode", 400);
			context.getLogger().log("exception " + exception.getMessage());
			response.put("body", exception.getMessage());
		}
		return response;
	}

	private Map<String, Object> getTableByTableId(String tableId, Context context) {
		context.getLogger().log("getTableByTableId started");

		Map<String, Object> response = new HashMap<>();

		GetItemRequest getItemRequest = GetItemRequest.builder()
				.tableName(tableTables)
				.key(Map.of("id", AttributeValue.builder().s(tableId).build()))
				.build();

		context.getLogger().log("getItemRequest " + getItemRequest);

		try {
			GetItemResponse getItemResponse = dynamoDbClient.getItem(getItemRequest);

			context.getLogger().log("getItemResponse " + getItemResponse);

			Map<String, AttributeValue> item = getItemResponse.item();

			Map<String, Object> tableRecord = new HashMap<>();
			tableRecord.put("id", Integer.parseInt(item.get("id").s()));
			tableRecord.put("number", Integer.parseInt(item.get("number").n()));
			tableRecord.put("places", Integer.parseInt(item.get("places").n()));
			tableRecord.put("isVip", Boolean.parseBoolean(item.get("isVip").bool().toString()));

			if (item.containsKey("minOrder")) {
				tableRecord.put("minOrder", Integer.parseInt(item.get("minOrder").n()));
			}

			context.getLogger().log("tableRecord " + tableRecord);
			response.put("statusCode", 200);
			response.put("body", tableRecord);

		} catch (Exception exception) {
			response.put("statusCode", 400);
			context.getLogger().log("exception " + exception.getMessage());
			response.put("body", exception.getMessage());
		}
		return response;

	}

	private void tablesGetById(String tableNumber, Context context) throws Exception{

		context.getLogger().log("tablesGetById started. tableNumber: " + tableNumber);

		ScanRequest scanRequest = ScanRequest.builder()
				.tableName(tableTables)
				.filterExpression("#numberAttr = :tableNumber")
				.expressionAttributeNames(Map.of("#numberAttr", "number"))
				.expressionAttributeValues(Map.of(":tableNumber", AttributeValue.builder().n(tableNumber).build()))
				.build();

		context.getLogger().log("scanRequest: " + scanRequest);

		ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);

		context.getLogger().log("scanResponse: " + scanResponse);

		if(scanResponse.items().isEmpty()) {
			context.getLogger().log("Table №" + tableNumber + " is absent.");
			throw new Exception("Table №" + tableNumber + " is absent.");
		}

		context.getLogger().log("Table №" + tableNumber + " is present.");
	}

	private Map<String, Object> reservationsPostHandler(Map<String, Object> body, Context context){

		context.getLogger().log("reservationsPost started");
		Map<String, Object> response = new HashMap<>();

		String tableNumber = String.valueOf(body.get("tableNumber"));
		String clientName = (String) body.get("clientName");
		String phoneNumber = (String) body.get("phoneNumber");
		String date = (String) body.get("date");
		String slotTimeStart = (String) body.get("slotTimeStart");
		String slotTimeEnd = (String) body.get("slotTimeEnd");

		context.getLogger().log("data: " +
				tableNumber + ", " +
				clientName + ", " +
				phoneNumber + ", " +
				date + ", " +
				slotTimeStart + ", " +
				slotTimeEnd);
		try {

			tablesGetById(tableNumber, context);

			conflictReservations(tableNumber, date, slotTimeStart, slotTimeEnd, context);

			// Create forecast data
			String id = UUID.randomUUID().toString();
			Map<String, AttributeValue> item = new HashMap<>();
			item.put("id", AttributeValue.builder().s(id).build());
			item.put("tableNumber", AttributeValue.builder().n(tableNumber).build());
			item.put("clientName", AttributeValue.builder().s(clientName).build());
			item.put("phoneNumber", AttributeValue.builder().s(phoneNumber).build());
			item.put("date", AttributeValue.builder().s(date).build());
			item.put("slotTimeStart", AttributeValue.builder().s(slotTimeStart).build());
			item.put("slotTimeEnd", AttributeValue.builder().s(slotTimeEnd).build());

			context.getLogger().log("table item created : " + item);
			context.getLogger().log("table: " + tableTables);

			// Save to DynamoDB
			PutItemRequest putItemRequest = PutItemRequest.builder()
					.tableName(tableReservations)
					.item(item)
					.build();

			dynamoDbClient.putItem(putItemRequest);
			context.getLogger().log("table item saved to the db");

			// Build a successful response
			response.put("statusCode", 200);
			response.put("body", Map.of("reservationId", id));
		} catch (Exception exception) {
			response.put("statusCode", 400);
			response.put("body", exception.getMessage());
		}
		return response;

	}

	private void conflictReservations(String tableNumber, String date, String slotTimeStart, String slotTimeEnd, Context context) throws Exception {
		context.getLogger().log("conflictReservations");

		// Query DynamoDB for existing reservations with the same tableNumber and date
		Map<String, AttributeValue> expressionValues = new HashMap<>();
		expressionValues.put(":tableNumber", AttributeValue.builder().n(String.valueOf(tableNumber)).build());
		expressionValues.put(":date", AttributeValue.builder().s(date).build());

		ScanRequest scanRequest = ScanRequest.builder()
				.tableName(tableReservations)
				.filterExpression("tableNumber = :tableNumber AND #dateAttr = :date")
				.expressionAttributeValues(Map.of(
						":tableNumber", AttributeValue.builder().n(String.valueOf(tableNumber)).build(),
						":date", AttributeValue.builder().s(date).build()
				))
				.expressionAttributeNames(Map.of("#dateAttr", "date")) // Handle reserved keyword
				.build();

		context.getLogger().log("scanRequest: " + scanRequest);

		ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);

		context.getLogger().log("scanResponse: " + scanResponse);

		List<Map<String, AttributeValue>> existingReservations = scanResponse.items();

		context.getLogger().log("existingReservations: " + existingReservations);

		for (Map<String, AttributeValue> reservation : existingReservations) {
			String existingSlotStart = reservation.get("slotTimeStart").s();
			String existingSlotEnd = reservation.get("slotTimeEnd").s();

			if (isTimeConflict(slotTimeStart, slotTimeEnd, existingSlotStart, existingSlotEnd, context)) {
				context.getLogger().log("Conflict detected");
				throw new Exception("Conflict detected");
			}
		}
		context.getLogger().log("No conflicts detected");
	}

	private boolean isTimeConflict(String newStart, String newEnd, String existingStart, String existingEnd, Context context) {
		context.getLogger().log("isTimeConflict");

		int newStartMinutes = timeToMinutes(newStart, context);
		int newEndMinutes = timeToMinutes(newEnd, context);
		int existingStartMinutes = timeToMinutes(existingStart, context);
		int existingEndMinutes = timeToMinutes(existingEnd, context);

		return (newStartMinutes < existingEndMinutes) && (newEndMinutes > existingStartMinutes);
	}

	private int timeToMinutes(String time, Context context) {
		context.getLogger().log("timeToMinutes");

		String[] parts = time.split(":");
		int hours = Integer.parseInt(parts[0]);
		int minutes = Integer.parseInt(parts[1]);

		return hours * 60 + minutes;
	}


	private Map<String, Object> reservationsGetHandler(Context context){

		context.getLogger().log("reservationsGet started");

		Map<String, Object> response = new HashMap<>();

		try {
			// Scan the DynamoDB table
			ScanRequest scanRequest = ScanRequest.builder()
					.tableName(tableReservations)
					.build();

			ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);
			context.getLogger().log("scanResponse " + scanResponse);

			// Build the response JSON
			List<Map<String, Object>> items = new ArrayList<>();

			for (Map<String, AttributeValue> item : scanResponse.items()) {
				Map<String, Object> tableRecord = new HashMap<>();
//				tableRecord.put("id", item.get("id").s());
				tableRecord.put("tableNumber", Integer.parseInt(item.get("tableNumber").n()));
				tableRecord.put("clientName", item.get("clientName").s());
				tableRecord.put("phoneNumber", item.get("phoneNumber").s());
				tableRecord.put("date", item.get("date").s());
				tableRecord.put("slotTimeStart", item.get("slotTimeStart").s());
				tableRecord.put("slotTimeEnd", item.get("slotTimeEnd").s());

				context.getLogger().log("tableRecord " + tableRecord);
				items.add(tableRecord);
			}

			response.put("statusCode", 200);
			response.put("body", Map.of("reservations", items));

		} catch (Exception exception) {
			response.put("statusCode", 400);
			context.getLogger().log("exception " + exception);
			response.put("body", exception.getMessage());
		}
		return response;

	}
}
