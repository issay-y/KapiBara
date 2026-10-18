module com.cafe.kapibara.kapibara {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires java.prefs;

    //DB
    requires com.zaxxer.hikari;
    requires org.mariadb.jdbc;
    requires java.sql;
    requires jbcrypt;

    // Excel export
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;

    exports com.cafe.kapibara.kapibara.config;

    opens com.cafe.kapibara.kapibara to javafx.fxml;
    exports com.cafe.kapibara.kapibara;

    exports com.cafe.kapibara.kapibara.controller.id;
    opens com.cafe.kapibara.kapibara.controller.id to javafx.fxml;

    exports com.cafe.kapibara.kapibara.controller.util;
    opens com.cafe.kapibara.kapibara.controller.util to javafx.fxml;

    opens com.cafe.kapibara.kapibara.images;

    // admin shell + pages
    exports com.cafe.kapibara.kapibara.controller.main.admin;
    opens com.cafe.kapibara.kapibara.controller.main.admin to javafx.fxml;
    opens com.cafe.kapibara.kapibara.controller.main.admin.home to javafx.fxml;
    opens com.cafe.kapibara.kapibara.controller.main.admin.sale to javafx.fxml;
    opens com.cafe.kapibara.kapibara.controller.main.admin.transaction to javafx.fxml;
    opens com.cafe.kapibara.kapibara.controller.main.admin.inventory to javafx.fxml;
    opens com.cafe.kapibara.kapibara.controller.main.admin.account to javafx.fxml;

    //
    exports com.cafe.kapibara.kapibara.controller.main.cashier;
    opens com.cafe.kapibara.kapibara.controller.main.cashier.home to javafx.fxml;
    opens com.cafe.kapibara.kapibara.controller.main.cashier.menu to javafx.fxml;
    opens com.cafe.kapibara.kapibara.controller.main.cashier.order to javafx.fxml;
    opens com.cafe.kapibara.kapibara.controller.main.cashier.receipt to javafx.fxml;
    opens com.cafe.kapibara.kapibara.controller.main.cashier.checkout to javafx.fxml;
    opens com.cafe.kapibara.kapibara.controller.main.cashier.transactions to javafx.fxml;


    opens com.cafe.kapibara.kapibara.css.id;
    opens com.cafe.kapibara.kapibara.css.util;
    opens com.cafe.kapibara.kapibara.css.main.admin;
    opens com.cafe.kapibara.kapibara.css.main.cashier;

    opens com.cafe.kapibara.kapibara.controller.main.cashier to javafx.fxml;
    exports com.cafe.kapibara.kapibara.util;
    opens com.cafe.kapibara.kapibara.util to javafx.fxml;
    exports com.cafe.kapibara.kapibara.controller.main.admin.settings;
    opens com.cafe.kapibara.kapibara.controller.main.admin.settings to javafx.fxml;
}