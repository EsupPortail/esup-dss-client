#!/bin/bash

cd %{INSTALL_PATH}
sudo tar xfvz %{INSTALL_PATH}/opensc-$opensc.version.tar.gz
cd %{INSTALL_PATH}/opensc-$opensc.version
sudo ./bootstrap
sudo ./configure --prefix=/usr --sysconfdir=/etc/opensc
sudo make
sudo make install
sudo chmod +x %{INSTALL_PATH}/java/linux/zulu%{jdk.zulu.version}-linux_x64/lib/jspawnhelper
sudo chmod +x %{INSTALL_PATH}/java/linux/zulu%{jdk.zulu.version}-linux_x64/bin/java