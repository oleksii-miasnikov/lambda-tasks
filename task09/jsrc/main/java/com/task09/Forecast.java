package com.task09;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 *      "elevation": number,
 *      "generationtime_ms": number,
 *      "hourly": {
 *          "temperature_2m": [number],
 *          "time": [str]
 *       },
 *       "hourly_units": {
 *          "temperature_2m": str,
 *          "time": str
 *       },
 *       "latitude": number,
 *       "longitude": number,
 *       "timezone": str,
 *       "timezone_abbreviation": str,
 *       "utc_offset_seconds": number
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Forecast {
    public double elevation;
    public double generationtime_ms;
    public double latitude;
    public double longitude;
    public String timezone;
    public String timezone_abbreviation;
    public double utc_offset_seconds;

    @JsonProperty("hourly")
    public Hourly hourly;

    @JsonProperty("hourly_units")
    public HourlyUnits hourlyUnits;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HourlyUnits {
        public String temperature_2m;
        public String time;

        @Override
        public String toString() {
            return "hourly_units{" +
                    "temperature_2m=" + temperature_2m +
                    ", time=" + time +
                    '}';
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Hourly {
        public List<Double> temperature_2m;
        public List<String> time;

        @Override
        public String toString() {
            return "Hourly{" +
                    "temperature_2m=" + temperature_2m +
                    ", time=" + time +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "Forecast{" +
                "elevation=" + elevation +
                ", generationtime_ms=" + generationtime_ms +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", timezone='" + timezone + '\'' +
                ", timezone_abbreviation='" + timezone_abbreviation + '\'' +
                ", utc_offset_seconds=" + utc_offset_seconds +
                ", hourly=" + hourly +
                ", hourlyUnits=" + hourlyUnits +
                '}';
    }
}

