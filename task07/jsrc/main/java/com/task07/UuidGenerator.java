package com.task07;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.events.RuleEventSource;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;


import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@LambdaHandler(
    lambdaName = "uuid_generator",
	roleName = "uuid_generator-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@RuleEventSource(
	targetRule = "uuid_trigger"
)
public class UuidGenerator implements RequestHandler<Object, String> {

	private static final String BUCKET_NAME = "cmtr-024ba94e-uuid-storage-test";
	private final AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public String handleRequest(Object input, Context context) {
		context.getLogger().log("handleRequest started");
		try {
			// Generate 10 random UUIDs
			List<String> uuids = IntStream.range(0, 10)
					.mapToObj(i -> UUID.randomUUID().toString())
					.collect(Collectors.toList());
			context.getLogger().log("10 random UUIDs: " + uuids);

			// Prepare JSON payload
			HashMap<String, Object> data = new HashMap<>();
			data.put("ids", uuids);

			// Generate filename with ISO timestamp
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
			String fileName = Instant.now().atZone(ZoneOffset.UTC).format(formatter);
			context.getLogger().log("fileName: " + fileName);

			// Convert to JSON and upload to S3
			String jsonData = objectMapper.writeValueAsString(data);
			s3Client.putObject(BUCKET_NAME, fileName, jsonData);

			context.getLogger().log("Successfully saved file: " + fileName);
			return "UUIDs saved to S3 successfully";
		} catch (Exception e) {
			context.getLogger().log("Error: " + e.getMessage());
			throw new RuntimeException(e);
		}
	}
}
