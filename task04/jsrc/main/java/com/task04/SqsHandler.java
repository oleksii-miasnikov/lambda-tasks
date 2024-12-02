package com.task04;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.events.SqsTriggerEventSource;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;

import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
    lambdaName = "sqs_handler",
	roleName = "sqs_handler-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@SqsTriggerEventSource(
		targetQueue = "async_queue",
		batchSize = 123
)
@DependsOn(
		name = "async_queue",
		resourceType = ResourceType.SQS_QUEUE
)
public class SqsHandler implements RequestHandler<SQSEvent, String> {

	@Override
	public String handleRequest(SQSEvent event, Context context) {
		try {
			for (SQSEvent.SQSMessage message : event.getRecords()) {
				context.getLogger().log("Received SQS message: " + message.getBody());
			}
		} catch (Exception exception) {
			context.getLogger().log("Error processing SQS messages: " + exception.getMessage());
			throw exception;
		}
		return "Processed " + event.getRecords().size() + " messages.";
	}
}
