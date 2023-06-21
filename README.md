![ESUP-DSS-CLIENT](https://github.com/EsupPortail/esup-dss-client/raw/master/src/main/resources/images/logo.jpg)

### Esup-DSS-Client

Esup-DSS-Client est la nouvelle passerelle entre le poste client et DSS Signature (librairie logicielle utilisée par Esup-Signature). Cette application remplace l'utilisation du client NexU qui n'est plus maintenu depuis 2018.

Esup-DSS-Client est à installer sur tous les postes clients des utilisateurs qui ont besoin de signer avec un certificat local ou materiel.

Le code de l’application NexU (sous license EUPL) a été partiellement repris pour coder cette nouvelle application. Les principaux changements par rapport à NexU sont :

* Compatibilté OpenJDK et OpenJFX
* Suppression des dependances non livrées avec NexU qui empéchait la compilation
* Ajout d'un plugin utilisant les capacités d'OpenSC pour se connecter aux périphériques materiel de manière native sous Linux, Windows et macOS
* Pour fonctionner, le module OpenSC doit être installé sur la machine. Ce projet est disponible ici : https://github.com/OpenSC/OpenSC. Comme l'accès au support crypto est natif (pcsc, apdu) il n'est pas nécessaire d'installer un pilote sur la machine cliente.

Le code présent dans ce dépôt permet de générer les installaleurs pour les systèmes d'exploitation Windows, MacOS et Linux

Pour plus d'informations voir : 

https://www.esup-portail.org/wiki/display/SIGN/Esup-DSS-Client
