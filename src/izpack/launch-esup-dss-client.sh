#!/bin/bash

%{INSTALL_PATH}/java/linux/zulu%{jdk.zulu.version}-linux_x64/bin/java --add-exports javafx.graphics/com.sun.javafx.application=ALL-UNNAMED --add-exports javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED -jar %{INSTALL_PATH}/esup-dss-client.jar &
