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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

public class Processor implements RequestHandler<Object, String> {

	private static final String URL = "https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=temperature_2m,wind_speed_10m&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m";
	private static final String TABLE_NAME = "cmtr-024ba94e-Weather-test";

	public String handleRequest(Object request, Context context) {
		context.getLogger().log("Hello from lambda");
		WeatherForecast weatherForecast = new WeatherForecast();
		String forecastStr = weatherForecast.getWeatherForecast(URL);
		context.getLogger().log("Weather forecast:" + forecastStr);

		// Create forecast data
		String id = UUID.randomUUID().toString();
		Map<String, AttributeValue> item = new HashMap<>();
		item.put("id", AttributeValue.builder().s(id).build());
		item.put("forecast", AttributeValue.builder().m(convertToAttributeValueMap(forecastStr)).build());
		context.getLogger().log("forecast created : " + item);

		// Save to DynamoDB
		PutItemRequest putItemRequest = PutItemRequest.builder()
				.tableName(TABLE_NAME)
				.item(item)
				.build();
		DynamoDbClient dynamoDbClient = DynamoDbClient.create();
		dynamoDbClient.putItem(putItemRequest);
		context.getLogger().log("forecast saved to the db");

		return forecastStr;
	}

	private Map<String, AttributeValue> convertToAttributeValueMap(String jsonString) {

		Map<String, AttributeValue> attributeValueMap = new HashMap<>();

		ObjectMapper objectMapper = new ObjectMapper();
		Forecast weatherData = new Forecast();
		try {
			weatherData = objectMapper.readValue(jsonString, Forecast.class);

			// Add simple fields
			attributeValueMap.put("elevation", AttributeValue.builder().n(String.valueOf(weatherData.elevation)).build());
			attributeValueMap.put("generationtime_ms", AttributeValue.builder().n(String.valueOf(weatherData.generationtime_ms)).build());
			attributeValueMap.put("latitude", AttributeValue.builder().n(String.valueOf(weatherData.latitude)).build());
			attributeValueMap.put("longitude", AttributeValue.builder().n(String.valueOf(weatherData.longitude)).build());
			attributeValueMap.put("timezone", AttributeValue.builder().s(weatherData.timezone).build());
			attributeValueMap.put("timezone_abbreviation", AttributeValue.builder().s(weatherData.timezone_abbreviation).build());
			attributeValueMap.put("utc_offset_seconds", AttributeValue.builder().n(String.valueOf(weatherData.utc_offset_seconds)).build());

			// Add nested "hourly" fields
			Map<String, AttributeValue> hourly = new HashMap<>();
			List<AttributeValue> hourlyTemps = weatherData.hourly.temperature_2m.stream()
					.map(temp -> AttributeValue.builder().n(String.valueOf(temp)).build())
					.collect(Collectors.toList());
			List<AttributeValue> hourlyTimes = weatherData.hourly.time.stream()
					.map(temp -> AttributeValue.builder().s(String.valueOf(temp)).build())
					.collect(Collectors.toList());
			hourly.put("temperature_2m", AttributeValue.builder().l(hourlyTemps).build());
			hourly.put("time", AttributeValue.builder().l(hourlyTimes).build());
			attributeValueMap.put("hourly", AttributeValue.builder().m(hourly).build());

			hourly.put("temperature_2m", AttributeValue.builder().s(weatherData.hourlyUnits.temperature_2m).build());
			hourly.put("time", AttributeValue.builder().s(weatherData.hourlyUnits.time).build());
			attributeValueMap.put("hourly_units", AttributeValue.builder().m(hourly).build());

			return attributeValueMap;

		} catch (Exception e) {
			e.printStackTrace();
		}
		return attributeValueMap;
	}
}

