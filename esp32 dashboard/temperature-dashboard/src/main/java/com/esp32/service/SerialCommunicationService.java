package com.esp32.service;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortInvalidPortException;
import com.fazecast.jSerialComm.SerialPortTimeoutException;
import com.esp32.model.TemperatureReading;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SerialCommunicationService implements AutoCloseable {
    private static final Pattern TEMPERATURE_PATTERN =
            Pattern.compile("(?i)(?:temperature|temp|t)\\s*[:=]?\\s*(-?\\d+(?:\\.\\d+)?)\\s*(?:°?\\s*C)?");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<![\\w.])-?\\d+(?:\\.\\d+)?(?![\\w.])");

    private volatile SerialPort port;
    private volatile ExecutorService executor;
    private volatile boolean stopping;
    private Consumer<TemperatureReading> readingListener = reading -> { };
    private Consumer<String> statusListener = status -> { };
    private Consumer<Throwable> errorListener = error -> { };

    public static List<SerialPort> availablePorts() {
        return Arrays.asList(SerialPort.getCommPorts());
    }

    public synchronized boolean connect(String systemPortName, int baudRate) {
        if (isOpen()) {
            statusListener.accept("Connected (already connected)");
            return true;
        }
        disconnect();
        statusListener.accept("Connecting to " + systemPortName);
        try {
            port = SerialPort.getCommPort(systemPortName);
            port.setBaudRate(baudRate);
            port.setNumDataBits(8);
            port.setNumStopBits(SerialPort.ONE_STOP_BIT);
            port.setParity(SerialPort.NO_PARITY);
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1000, 0);
            if (!port.openPort()) {
                statusListener.accept("Connection failed");
                errorListener.accept(new IllegalStateException("Unable to open " + systemPortName));
                return false;
            }

            stopping = false;
            SerialPort connectedPort = port;
            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "esp32-serial-reader");
                thread.setDaemon(true);
                return thread;
            });
            executor.submit(() -> readLoop(connectedPort));
            statusListener.accept("Connected to " + systemPortName);
            return true;
        } catch (SerialPortInvalidPortException | IllegalArgumentException exception) {
            statusListener.accept("Connection failed");
            errorListener.accept(exception);
            disconnect();
            return false;
        }
    }

    private void readLoop(SerialPort connectedPort) {
        StringBuilder buffer = new StringBuilder();
        try {
            while (!stopping && connectedPort.isOpen()) {
                int value;
                try {
                    value = connectedPort.getInputStream().read();
                } catch (SerialPortTimeoutException timeout) {
                    // A quiet interval is expected between ESP32 readings.
                    continue;
                }
                if (value < 0) {
                    break;
                }
                if (value == '\n' || value == '\r') {
                    processLine(buffer.toString());
                    buffer.setLength(0);
                } else {
                    buffer.append((char) value);
                }
            }
        } catch (Exception exception) {
            if (!stopping && isCurrentPort(connectedPort)) {
                errorListener.accept(exception);
            }
        } finally {
            if (isCurrentPort(connectedPort)) {
                connectedPort.closePort();
                port = null;
                statusListener.accept("ESP32 disconnected");
            }
        }
    }

    private void processLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        Double temperature = parseTemperature(trimmed);
        if (temperature != null && temperature >= -100 && temperature <= 150) {
            readingListener.accept(new TemperatureReading(LocalDateTime.now(), temperature));
        }
    }

    private Double parseTemperature(String line) {
        Matcher labelled = TEMPERATURE_PATTERN.matcher(line);
        if (labelled.find()) {
            return Double.valueOf(labelled.group(1));
        }
        if (line.matches("\\s*-?\\d+(?:\\.\\d+)?\\s*(?:°?\\s*C)?\\s*")) {
            Matcher number = NUMBER_PATTERN.matcher(line);
            return number.find() ? Double.valueOf(number.group()) : null;
        }
        return null;
    }

    public synchronized boolean isOpen() {
        return port != null && port.isOpen();
    }

    public synchronized void disconnect() {
        stopping = true;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (port != null) {
            port.closePort();
            port = null;
        }
        statusListener.accept("Disconnected");
    }

    private synchronized boolean isCurrentPort(SerialPort candidate) {
        return port == candidate && candidate.isOpen();
    }

    public void setReadingListener(Consumer<TemperatureReading> listener) {
        readingListener = listener == null ? reading -> { } : listener;
    }

    public void setStatusListener(Consumer<String> listener) {
        statusListener = listener == null ? status -> { } : listener;
    }

    public void setErrorListener(Consumer<Throwable> listener) {
        errorListener = listener == null ? error -> { } : listener;
    }

    @Override
    public void close() {
        disconnect();
    }
}
