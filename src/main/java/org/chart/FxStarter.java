package org.chart;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.chart.chart.ChartFactory;
import org.chart.controller.MainController;
import org.chart.service.LogParserService;

public class FxStarter extends Application {

    private MainController controller;

    @Override
    public void start(Stage stage) {
        controller = new MainController(new LogParserService(), new ChartFactory());
        Scene scene = new Scene(controller.getView());
        stage.setTitle("Tank Level Visualizer");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
