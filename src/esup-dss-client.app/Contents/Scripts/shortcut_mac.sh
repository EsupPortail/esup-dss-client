#!/usr/bin/env bash
# Chemin vers le fichier ICNS de l'icône
ICON_PATH="/Applications/esup-dss-client.app/Contents/Resources/esupdssclient.icns"

# Vérifier si le fichier ICNS existe
if [ ! -f "$ICON_PATH" ]; then
  echo "Le fichier ICNS n'existe pas : $ICON_PATH"
  exit 1
fi

# Chemin et nom du fichier .app
APP_FILE="/Applications/esup-dss-client.app/"

sudo chmod -R +x /Applications/esup-dss-client.app/Contents/Resources/jdkmac/zulu-17.jre/Contents/Home/bin

# Créer le fichier exécutable du .app

sudo chmod +x "$APP_FILE/Contents/MacOS/esup-dss-client"

# Rendre le .app exécutable
chmod +x "$APP_FILE"

# Chemin vers le fichier .app
APP_PATH="/Applications/esup-dss-client.app"

# Vérifier si le fichier .app existe
if [ ! -d "$APP_PATH" ]; then
  echo "Le fichier .app n'existe pas : $APP_PATH"
  exit 1
fi

# Chemin vers le fichier .plist
PLIST_FILE="$HOME/Library/LaunchAgents/org.esupportail.esupdssclient.esup-dss-client.plist"

# Créer le fichier .plist
cat > "$PLIST_FILE" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>org.esupportail.esupdssclient</string>
    <key>ProgramArguments</key>
    <array>
        <string>$APP_PATH/Contents/MacOS/esup-dss-client</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
</dict>
</plist>
EOF

# Charger le fichier de configuration de lancement avec launchctl bootstrap
launchctl bootstrap gui/$(id -u "$USER") "$PLIST_FILE"

echo "L'application esup-dss-client a été configurée pour se lancer automatiquement au démarrage."

exit 0
