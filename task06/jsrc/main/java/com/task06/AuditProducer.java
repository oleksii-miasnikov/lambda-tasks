package com.task06;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
    lambdaName = "audit_producer",
	roleName = "audit_producer-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@DynamoDbTriggerEventSource(
		targetTable = "Configuration",
		batchSize = 123
)
@DependsOn(
		name = "Configuration",
		resourceType = ResourceType.DYNAMODB_TABLE
)

public class AuditProducer implements RequestHandler<DynamodbEvent, Void> {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public Void handleRequest(DynamodbEvent dynamodbEvent, Context context) {
		context.getLogger().log("handleRequest started");
		context.getLogger().log("dynamodbEvent" + dynamodbEvent);
		context.getLogger().log("context" + context);
		dynamodbEvent.getRecords().forEach(this::logDynamoDBRecord);
		return null;
	}

	private void logDynamoDBRecord(DynamodbStreamRecord record) {
		System.out.println(record.getEventID());
		System.out.println(record.getEventName());
		System.out.println("DynamoDB Record: " + GSON.toJson(record.getDynamodb()));
	}
}