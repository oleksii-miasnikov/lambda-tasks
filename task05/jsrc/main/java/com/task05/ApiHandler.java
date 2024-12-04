package com.task05;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
//import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;
//import com.syndicate.deployment.annotations.resources.DependsOn;
//import com.syndicate.deployment.model.ResourceType;
//import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

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
//@DynamoDbTriggerEventSource(targetTable = "Events", batchSize = 1)
//@DependsOn(name = "Events", resourceType = ResourceType.DYNAMODB_TABLE)

public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private static final String TABLE_NAME = "Event";
	private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		try {
			// Parse incoming request
			Map<String, Object> requestBody = objectMapper.readValue(request.getBody(), Map.class);
			int principalId = (Integer) requestBody.get("principalId");
			Map<String, Object> content = (Map<String, Object>) requestBody.get("content");
			System.out.println("Incoming principalId: " + principalId);

			// Create event data
			String id = UUID.randomUUID().toString();
			String createdAt = Instant.now().toString();

			Map<String, AttributeValue> item = new HashMap<>();
			item.put("id", AttributeValue.builder().s(id).build());
			item.put("principalId", AttributeValue.builder().n(String.valueOf(principalId)).build());
			item.put("createdAt", AttributeValue.builder().s(createdAt).build());
			item.put("body", AttributeValue.builder().m(convertToAttributeValueMap(content)).build());

			// Save to DynamoDB
			PutItemRequest putItemRequest = PutItemRequest.builder()
					.tableName(TABLE_NAME)
					.item(item)
					.build();
			dynamoDbClient.putItem(putItemRequest);

			// Build response
			Map<String, Object> responseEvent = new HashMap<>();
			responseEvent.put("id", id);
			responseEvent.put("principalId", principalId);
			responseEvent.put("createdAt", createdAt);
			responseEvent.put("body", content);

			APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
			response.setStatusCode(201);
			response.setBody(objectMapper.writeValueAsString(Map.of("event", responseEvent)));

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
