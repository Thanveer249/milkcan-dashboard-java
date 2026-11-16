package com.esp32.model;

import java.time.LocalDateTime;

public record TemperatureReading(LocalDateTime timestamp, double temperatureCelsius) {
}
