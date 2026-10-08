module com.cafe.kapibara.kapibara {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;

    opens com.cafe.kapibara.kapibara to javafx.fxml;
    exports com.cafe.kapibara.kapibara;
    exports com.cafe.kapibara.kapibara.controller.id;
    opens com.cafe.kapibara.kapibara.controller.id to javafx.fxml;
}