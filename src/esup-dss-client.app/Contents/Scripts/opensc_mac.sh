#!/usr/bin/env bash

hdiutil attach -quiet /Applications/esup-dss-client.app/Contents/Resources/OpenSC.dmg

sudo installer -pkg /Volumes/OpenSC/OpenSC\ $opensc.version.pkg -target /

hdiutil detach /Volumes/OpenSC
