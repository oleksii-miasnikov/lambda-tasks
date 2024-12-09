package com.task08.utils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class WeatherForecast {

    private final HttpClient httpClient;

    public WeatherForecast() {
        this.httpClient = HttpClient.newHttpClient();
    }

    public String getWeatherForecast(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        try{
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (IOException | InterruptedException exception) {
            throw new RuntimeException("Failed to get weather data: " + exception.getMessage());
        }
    }
}
