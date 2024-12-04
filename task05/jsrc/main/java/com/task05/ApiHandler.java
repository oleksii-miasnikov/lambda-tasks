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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.Instant;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
    lambdaName = "api_handler",
	roleName = "api_handler-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)

public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private static final String TABLE_NAME = "cmtr-024ba94e-Events-test";

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		try {
			ObjectMapper objectMapper = new ObjectMapper();

			context.getLogger().log("Incoming request: " + request);
			context.getLogger().log("Request Body: " + request.getBody());

			// Parse incoming request
			Map<String, Object> requestBody = objectMapper.readValue(request.getBody(), Map.class);
			int principalId = (Integer) requestBody.get("principalId");
			Map<String, Object> content = (Map<String, Object>) requestBody.get("content");
			context.getLogger().log("request received");

			// Create event data
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
			String createdAt = Instant.now().atZone(ZoneOffset.UTC).format(formatter);
			String id = UUID.randomUUID().toString();

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
			DynamoDbClient dynamoDbClient = DynamoDbClient.create();
			dynamoDbClient.putItem(putItemRequest);
			context.getLogger().log("event saved to the db");

			// Create response
			Map<String, Object> responseEvent = new HashMap<>();
			responseEvent.put("id", id);
			responseEvent.put("principalId", principalId);
			responseEvent.put("createdAt", createdAt);
			responseEvent.put("body", content);
			context.getLogger().log("response created");

			APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
			Map<String, Object> body = new HashMap<>();
			body.put("event", responseEvent);
			body.put("statusCode", 201);
			response.setBody(objectMapper.writeValueAsString(body));
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
