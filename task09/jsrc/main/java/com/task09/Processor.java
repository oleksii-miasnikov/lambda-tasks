package com.task09;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import com.syndicate.deployment.annotations.lambda.LambdaLayer;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.Architecture;
import com.syndicate.deployment.model.ArtifactExtension;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import com.syndicate.deployment.model.TracingMode;
import com.task08.utils.WeatherForecast;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
    lambdaName = "processor",
	roleName = "processor-role",
	layers = {"sdk-layer"},
	isPublishVersion = true,
	tracingMode = TracingMode.Active,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaLayer(
		layerName = "sdk-layer",
		libraries = {"lib/weather-forecast-1.0.jar"},
		runtime = DeploymentRuntime.JAVA11,
		architectures = {Architecture.ARM64},
		artifactExtension = ArtifactExtension.ZIP
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)

public class Processor implements RequestHandler<Object, Map<String, AttributeValue>> {

	private static final String URL = "https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=temperature_2m,wind_speed_10m&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m";
	private static final String TABLE_NAME = "cmtr-024ba94e-Weather-test";

	public Map<String, AttributeValue> handleRequest(Object request, Context context) {
		context.getLogger().log("Hello from lambda");
		WeatherForecast weatherForecast = new WeatherForecast();
		String forecast = weatherForecast.getWeatherForecast(URL);
		context.getLogger().log("Weather forecast:" + forecast);

		//Convert string to map
		ObjectMapper objectMapper = new ObjectMapper();
		Map<String, AttributeValue> map =  new HashMap<>();
		try {
			map = objectMapper.readValue(forecast, Map.class);
			System.out.println(map);
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Create forecast data
		String id = UUID.randomUUID().toString();
		Map<String, AttributeValue> item = new HashMap<>();
		item.put("id", AttributeValue.builder().s(id).build());
		item.put("forecast", AttributeValue.builder().m(map).build());
		context.getLogger().log("forecast created");

		// Save to DynamoDB
		PutItemRequest putItemRequest = PutItemRequest.builder()
				.tableName(TABLE_NAME)
				.item(item)
				.build();
		DynamoDbClient dynamoDbClient = DynamoDbClient.create();
		dynamoDbClient.putItem(putItemRequest);
		context.getLogger().log("event saved to the db");

		return map;
	}
}

