package com.task05;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import java.time.format.DateTimeFormatter;

import java.util.HashMap;
import java.util.Map;
import java.time.Instant;
import java.util.UUID;

@LambdaHandler(
    lambdaName = "api_handler",
	roleName = "api_handler-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)

public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private static final String TABLE_NAME = "cmtr-024ba94e-Events";
	private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
	private final ObjectMapper objectMapper = new ObjectMapper();
	private static final String PATTERN_FORMAT = "dd.MM.yyyyTHH:mm:ss.SSSZ";
	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(PATTERN_FORMAT);
			//.withZone(ZoneId.systemDefault());

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		try {
			context.getLogger().log("Incoming request: " + request);
			context.getLogger().log("Request Body: " + request.getBody());

			// Parse incoming request
			Map<String, Object> requestBody = objectMapper.readValue(request.getBody(), Map.class);
			int principalId = (Integer) requestBody.get("principalId");
			Map<String, Object> content = (Map<String, Object>) requestBody.get("content");
			context.getLogger().log("request received");

			// Create event data
			String id = UUID.randomUUID().toString();
			String createdAt = formatter.format(Instant.now());

			Map<String, AttributeValue> item = new HashMap<>();
			item.put("id", AttributeValue.builder().s(id).build());
			item.put("principalId", AttributeValue.builder().n(String.valueOf(principalId)).build());
			item.put("createdAt", AttributeValue.builder().s(createdAt).build());
			item.put("body", AttributeValue.builder().m(convertToAttributeValueMap(content)).build());
			context.getLogger().log("event created");

			// Save to DynamoDB
			PutItemRequest putItemRequest = PutItemRequest.builder()
					.tableName(TABLE_NAME)
					.item(item)
					.build();
			dynamoDbClient.putItem(putItemRequest);
			context.getLogger().log("event saved to the db");

			// Build response
			Map<String, Object> responseEvent = new HashMap<>();
			responseEvent.put("id", id);
			responseEvent.put("principalId", principalId);
			responseEvent.put("createdAt", createdAt);
			responseEvent.put("body", content);
			context.getLogger().log("response created");

			APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
			response.setStatusCode(201);
			response.setBody(objectMapper.writeValueAsString(
					Map.of(Map.of("statusCode", 201),
							Map.of("event", responseEvent))));
			context.getLogger().log("handleRequest finished");
			return response;

		} catch (Exception exception) {
			exception.printStackTrace();
			return new APIGatewayProxyResponseEvent()
					.withStatusCode(500)
					.withBody("{\"error\": \"" + exception.getMessage() + "\"}");
		}
	}

	private Map<String, AttributeValue> convertToAttributeValueMap(Map<String, Object> content) {
		Map<String, AttributeValue> attributeValueMap = new HashMap<>();
		for (Map.Entry<String, Object> entry : content.entrySet()) {
			attributeValueMap.put(entry.getKey(), AttributeValue.builder().s(entry.getValue().toString()).build());
		}
		return attributeValueMap;
	}
}
