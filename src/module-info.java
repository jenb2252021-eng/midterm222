module com.mycompany.app {
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.media;
    requires javafx.web;
    requires javafx.fxml;

    requires java.rmi;
    requires java.net.http;
    requires jdk.jsobject;

    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;

    exports app;
    exports client_GUI;
    exports Server;
    exports Model;
    exports JSONUtil;
    exports storage;

    opens app to javafx.graphics;
    opens client_GUI to javafx.web, com.fasterxml.jackson.databind, jdk.jsobject;
    opens Model to com.fasterxml.jackson.databind, java.rmi;
    opens JSONUtil to com.fasterxml.jackson.databind;
    opens Server to java.rmi;
}
