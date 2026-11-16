package com.esp32;

import com.esp32.controller.DashboardController;
import javafx.application.Application;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        new DashboardController().show(stage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
