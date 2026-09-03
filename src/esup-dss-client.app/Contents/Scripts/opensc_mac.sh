#!/usr/bin/env bash

set -euo pipefail

readonly DMG_PATH="/Applications/esup-dss-client.app/Contents/Resources/OpenSC.dmg"
MOUNT_POINT="$(mktemp -d /tmp/esup-dss-opensc.XXXXXX)"

cleanup() {
	hdiutil detach -quiet "${MOUNT_POINT}" >/dev/null 2>&1 || true
	rmdir "${MOUNT_POINT}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

hdiutil attach -nobrowse -quiet -mountpoint "${MOUNT_POINT}" "${DMG_PATH}"

readonly PKG_PATH="${MOUNT_POINT}/OpenSC $opensc.version.pkg"
if [[ ! -f "${PKG_PATH}" ]]; then
	echo "Paquet OpenSC introuvable : ${PKG_PATH}" >&2
	exit 1
fi

/usr/sbin/installer -pkg "${PKG_PATH}" -target /
