package com.task04;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.events.SnsEventSource;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;

import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
    lambdaName = "sns_handler",
	roleName = "sns_handler-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@SnsEventSource(
		targetTopic = "lambda_topic"
)
@DependsOn(
		name = "lambda_topic",
		resourceType = ResourceType.SNS_TOPIC
)
public class SnsHandler implements RequestHandler<SNSEvent, String> {

	@Override
	public String handleRequest(SNSEvent event, Context context) {

		for (SNSEvent.SNSRecord record : event.getRecords()) {
			SNSEvent.SNS snsMessage = record.getSNS();
			context.getLogger().log("Received SNS message:");
			context.getLogger().log("Message ID: " + snsMessage.getMessageId());
			context.getLogger().log("Subject: " + snsMessage.getSubject());
			context.getLogger().log("Message: " + snsMessage.getMessage());
		}
		return "Successfully processed " + event.getRecords().size() + " SNS messages.";
	}
}
