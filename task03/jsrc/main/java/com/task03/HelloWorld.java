package com.task03;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaLayer;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.Architecture;
import com.syndicate.deployment.model.ArtifactExtension;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.HashMap;


@LambdaHandler(
		lambdaName = "hello_world",
		roleName = "hello_world-role",
		isPublishVersion = true,
		aliasName = "${lambdas_alias_name}",
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)

public class HelloWorld implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

	@Override
	public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent requestEvent, Context context) {
		System.out.println("---Hello from lambda---");
		String method = requestEvent.getRequestContext().getHttp().getMethod();
		String path = requestEvent.getRawPath();
		System.out.println("Method --> " + method);
		System.out.println("Path --> " + path);

		if ("/hello".equals(path) && "GET".equalsIgnoreCase(method)) {
			return APIGatewayV2HTTPResponse.builder()
					.withStatusCode(200)
					.withBody("{\"statusCode\": 200, \"message\": \"Hello from Lambda\"}")
					.build();
		}

		return APIGatewayV2HTTPResponse.builder()
				.withStatusCode(400)
				.withBody(String.format("{\"statusCode\": 400, \"message\": \"Bad request syntax or unsupported method. Request path: %s. HTTP method: %s\"}", path, method))
				.build();
	}
}
