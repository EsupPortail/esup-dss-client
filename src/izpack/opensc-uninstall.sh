#!/bin/bash

tar xfvz opensc-*.tar.gz
cd opensc-*
./bootstrap
./configure --prefix=/usr --sysconfdir=/etc/opensc
make
sudo make uninstall
