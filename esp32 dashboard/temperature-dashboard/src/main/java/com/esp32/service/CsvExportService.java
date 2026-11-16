package com.esp32.service;

import com.esp32.model.TemperatureReading;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CsvExportService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_LOCAL_TIME;

    public void export(Path destination, List<TemperatureReading> readings) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            writer.write("date,time,temperature_celsius");
            writer.newLine();
            for (TemperatureReading reading : readings) {
                writer.write(reading.timestamp().toLocalDate().format(DATE));
                writer.write(',');
                writer.write(reading.timestamp().toLocalTime().format(TIME));
                writer.write(',');
                writer.write(Double.toString(reading.temperatureCelsius()));
                writer.newLine();
            }
        }
    }
}
