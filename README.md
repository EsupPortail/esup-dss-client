![ESUP-DSS-CLIENT](https://github.com/EsupPortail/esup-dss-client/raw/master/src/main/resources/images/logo.jpg)

# Esup-DSS-Client

Esup-DSS-Client est la nouvelle passerelle entre le poste client et DSS Signature (librairie logicielle utilisée par Esup-Signature). Cette application remplace l’utilisation du client NexU https://github.com/nowina-solutions/nexu qui n’est plus maintenu depuis 2018. 
Elle peut être utilisée avec l’application Esup-Signature pour effectuer des signatures eIDas (RGS**) : https://www.esup-portail.org/wiki/display/SIGN

Esup-DSS-Client est à installer sur tous les postes clients des utilisateurs qui ont besoin de signer avec un certificat local ou materiel.

Le code de l’application NexU (sous license EUPL) a été partiellement repris pour coder cette nouvelle application. Les principaux changements par rapport à NexU sont :

* Compatibilté OpenJDK et OpenJFX
* Suppression des dépendances non livrées avec NexU qui empêchaient la compilation
* Ajout d’un plugin utilisant les capacités d’OpenSC pour se connecter aux périphériques materiel de manière native sous Linux, Windows et macOS
* Pour fonctionner, le module OpenSC doit être installé sur la machine. Ce projet est disponible ici : https://github.com/OpenSC/OpenSC. Comme l’accès au support crypto est natif (pcsc, apdu) il n’est pas nécessaire d’installer un pilote sur la machine cliente.

Le code présent dans ce dépôt permet de générer les installateurs pour les systèmes d’exploitation Windows, MacOS et Linux

## Compilation / Obtention des installateurs

### Sous Linux

En lancant :

``` mvn clean install ```

Vous obtenez dans ./target les fichiers :

esup-dss-client-installer.jar pour l'installation sous Linux
esup-dss-client-win64.zip pour l'installation sous Windows

Pour obtenir le jar seul :

``` mvn clean package ```

### Sous macOS
Pour obtenir l’installateur PKG, il faut être sous macOS. Voici les prérequis à installer :

git (et donc les outils Xcode)
brew (voir https://brew.sh/index_fr)
maven via brew (brew install maven)

Modifier le code installateur en cas de signature du package dans src/izpack/pkg.sh

``` mvn clean package -Dmac.os=true ```

## Pour plus d'informations voir : 

https://www.esup-portail.org/wiki/display/SIGN/Esup-DSS-Client
