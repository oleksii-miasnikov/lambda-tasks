package com.task06;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
    lambdaName = "audit_producer",
	roleName = "audit_producer-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@DynamoDbTriggerEventSource(
		targetTable = "Configuration",
		batchSize = 11
)
@DependsOn(
		name = "Configuration",
		resourceType = ResourceType.DYNAMODB_TABLE
)

public class AuditProducer implements RequestHandler<DynamodbEvent, Void> {

	private static final String TABLE_NAME = "cmtr-024ba94e-Audit-test";
	private final AmazonDynamoDB dynamoDbClient = AmazonDynamoDBClientBuilder.defaultClient();

	public Void handleRequest(DynamodbEvent dynamodbEvent, Context context) {
		context.getLogger().log("handleRequest started");
		context.getLogger().log("dynamodbEvent" + dynamodbEvent);
		context.getLogger().log("context" + context);
		for (DynamodbEvent.DynamodbStreamRecord record : dynamodbEvent.getRecords()) {
			String eventName = record.getEventName();

			if ("INSERT".equals(eventName)) {
				handleInsert(record, context);
			} else if ("MODIFY".equals(eventName)) {
				handleModify(record, context);
			}
		}
		return null;
	}

	private void handleInsert(DynamodbEvent.DynamodbStreamRecord record, Context context) {
		context.getLogger().log("handleInsert started");

		Map<String, AttributeValue> newImage = record.getDynamodb().getNewImage();
		String key = newImage.get("key").getS();
		int value = Integer.parseInt(newImage.get("value").getN());

		// Create the audit item
		Map<String, AttributeValue> auditItem = new HashMap<>();
		auditItem.put("id", new AttributeValue(UUID.randomUUID().toString()));
		auditItem.put("itemKey", new AttributeValue(key));
		auditItem.put("modificationTime", new AttributeValue(Instant.now().toString()));
		auditItem.put("newValue", new AttributeValue().withM(newImage));

		// Store the audit item in the Audit table
		putItemInAuditTable(auditItem, context);
	}

	private void handleModify(DynamodbEvent.DynamodbStreamRecord record, Context context) {
		context.getLogger().log("handleModify started");
		Map<String, AttributeValue> oldImage = record.getDynamodb().getOldImage();
		Map<String, AttributeValue> newImage = record.getDynamodb().getNewImage();

		String key = newImage.get("key").getS();
		int oldValue = Integer.parseInt(oldImage.get("value").getN());
		int newValue = Integer.parseInt(newImage.get("value").getN());

		if (oldValue != newValue) {
			// Create the audit item
			Map<String, AttributeValue> auditItem = new HashMap<>();
			auditItem.put("id", new AttributeValue(UUID.randomUUID().toString()));
			auditItem.put("itemKey", new AttributeValue(key));
			auditItem.put("modificationTime", new AttributeValue(Instant.now().toString()));
			auditItem.put("updatedAttribute", new AttributeValue("value"));
			auditItem.put("oldValue", new AttributeValue().withN(String.valueOf(oldValue)));
			auditItem.put("newValue", new AttributeValue().withN(String.valueOf(newValue)));

			// Store the audit item in the Audit table
			putItemInAuditTable(auditItem, context);
		}
	}

	private void putItemInAuditTable(Map<String, AttributeValue> auditItem, Context context) {
		context.getLogger().log("putItemInAuditTable started");
		PutItemRequest request = new PutItemRequest()
				.withTableName(TABLE_NAME)
				.withItem(auditItem);
		dynamoDbClient.putItem(request);
	}
}