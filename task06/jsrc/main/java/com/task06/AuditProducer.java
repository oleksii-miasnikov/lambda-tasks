package com.task06;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

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

	private final ObjectMapper objectMapper;

	public AuditProducer() {
		this.objectMapper = new ObjectMapper();
	}

	public Void handleRequest(DynamodbEvent dynamodbEvent, Context context) {
		context.getLogger().log("handleRequest started");
		context.getLogger().log("dynamodbEvent" + dynamodbEvent);
		context.getLogger().log("context" + context);
		for (DynamodbEvent.DynamodbStreamRecord record : dynamodbEvent.getRecords()) {
			context.getLogger().log("Incoming record: " + record);
		}
		return null;
	}
}