package com.esp32.service;

import com.esp32.model.TemperatureReading;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

public class TemperatureService {
    private final List<TemperatureReading> readings = new ArrayList<>();

    public synchronized void add(TemperatureReading reading) {
        readings.add(reading);
    }

    public synchronized List<TemperatureReading> readings() {
        return List.copyOf(readings);
    }

    public synchronized OptionalDouble minimum() {
        return readings.stream().mapToDouble(TemperatureReading::temperatureCelsius).min();
    }

    public synchronized OptionalDouble maximum() {
        return readings.stream().mapToDouble(TemperatureReading::temperatureCelsius).max();
    }

    public synchronized void clear() {
        readings.clear();
    }
}
