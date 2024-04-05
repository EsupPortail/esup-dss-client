#!/usr/bin/env bash
sudo codesign --sign "Developer ID Application: ****" --entitlements $macbundle.path/esup-dss-client.app/Contents/Scripts/shortcut_sh.plist $macbundle.path/esup-dss-client.app/Contents/Scripts/shortcut_mac.sh
sudo codesign --sign "Developer ID Application: ****" --entitlements $macbundle.path/esup-dss-client.app/Contents/Scripts/opensc_sh.plist $macbundle.path/esup-dss-client.app/Contents/Scripts/opensc_mac.sh
sudo codesign --sign "Developer ID Application: ****" --entitlements $macbundle.path/esup-dss-client.app/Contents/Scripts/postinstall.plist $macbundle.path/esup-dss-client.app/Contents/Scripts/postinstall
sudo codesign --sign "Developer ID Application: ****" $macbundle.path/esup-dss-client.app
sudo chmod +x $macbundle.path/esup-dss-client.app/Contents/Scripts/*
cd $macbundle.path/esup-dss-client.app
#pkgbuild --version 1.2.1.0 --identifier org.esupportail.esup-dss-client --install-location /Applications/esup-dss-client.app --scripts Contents/Scripts --identifier org.esupportail.esup-dss-client --sign "Developer ID Installer: ****" --root . Distribution.pkg
pkgbuild --version 1.2.1.0 --identifier org.esupportail.esup-dss-client --install-location /Applications/esup-dss-client.app --scripts Contents/Scripts --identifier org.esupportail.esup-dss-client --root . Distribution.pkg
productbuild --synthesize --package Distribution.pkg Distribution.xml
#rm Distribution.pkg
cd ../
#productbuild --distribution esup-dss-client.app/Distribution.xml --sign "Developer ID Installer: ****" --resources esup-dss-client.app/Contents/Resources --package-path esup-dss-client.app esup-dss-client.pkg
productbuild --distribution esup-dss-client.app/Distribution.xml --resources esup-dss-client.app/Contents/Resources --package-path esup-dss-client.app esup-dss-client.pkg