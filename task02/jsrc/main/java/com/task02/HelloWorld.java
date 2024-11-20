package com.task02;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;

import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
    lambdaName = "hello_world",
	roleName = "hello_world-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
public class HelloWorld implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
		System.out.println("Hello from lambda");

		String path = (String) event.get("rawPath");
		System.out.println("Endpoint: " + path);

		Map<String, Object> requestContext = (Map<String, Object>) event.get("requestContext");
		Map<String, String> http = (Map<String, String>) requestContext.get("http");
		String method = http.get("method");
		System.out.println("Method: " + method);

		Map<String, Object> resultMap = new HashMap<String, Object>();
		if ("/hello".equals(path) && "GET".equalsIgnoreCase(method)) {
			resultMap.put("statusCode", 200);
			resultMap.put("message", "Hello from Lambda");
		} else {
			resultMap.put("statusCode", 400);
			resultMap.put("message", "Bad request syntax or unsupported method. Request path: "
					+ path + ". HTTP method: " + method);
		}
		return resultMap;
	}
}
