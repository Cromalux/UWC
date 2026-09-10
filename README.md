# UWC — Underwater Camera (Pixel 9 Pro)

Application caméra Android pensée pour le snorkeling avec un téléphone en étui étanche :
une fois les réglages faits, on **verrouille l'écran** (l'eau rend le tactile inutilisable)
et on pilote tout aux **boutons de volume**.

## Ce qu'elle fait

- **Photo RAW** (DNG), **RAW+JPEG** ou JPEG — via CameraX 1.6 `OUTPUT_FORMAT_RAW_JPEG`.
- **Vidéo HLG10** (Hybrid Log-Gamma, 10 bits) ou SDR — la voie « log » réellement exposée par Android.
  Courbe de tonemap « flat » additionnelle si le HAL du téléphone l'autorise (sondé au démarrage).
- **Focus peaking** (laplacien sur la luminance, overlay coloré) activable/désactivable, seuil et couleur réglables.
- **Mise au point** : AF continu, *figé au verrouillage*, ou manuel avec distances réelles sous l'eau
  (réfraction du hublot plat corrigée).
- **Balance des blancs manuelle** : fige les gains AWB courants et pousse le rouge (absorbé par l'eau).
- **Verrouillage** : tous les événements tactiles sont avalés, mode immersif, exclusion des gestes de bord,
  et **épinglage d'application** (Lock Task) pour bloquer barre de navigation et volet de notifications.
- Retour haptique sur chaque action, écran noir optionnel pour la batterie, diagnostic des capacités copiable.

## Boutons (une fois verrouillé)

| Geste | Action |
|---|---|
| Vol+ (court) | Photo |
| Vol+ maintenu 1,5 s | Verrouiller / déverrouiller l'écran |
| Vol− (court) | Démarrer / arrêter la vidéo |
| Vol− maintenu 1 s | Objectif suivant (0,5× · 1× · 2× · 5×) |

Photo et vidéo sont armées en permanence : il n'y a pas de « mode » à basculer. L'écran principal ne
comporte qu'une roue crantée (réglages) et une pastille de zoom — tout le reste passe par les boutons.

## Build

Prérequis : JDK 21, Android SDK (API 36). `local.properties` doit pointer sur le SDK.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Première utilisation : activer **Paramètres › Sécurité › Épinglage d'applications** pour que le
verrouillage bloque aussi la navigation système.

## Architecture

- `camera/CameraController` — façade CameraX : Preview + ImageCapture + VideoCapture (+ ImageAnalysis pour le
  peaking) armés ensemble, avec cascade de repli si le HAL refuse une combinaison ; réglages à chaud via
  `Camera2CameraControl`.
- `camera/CameraCapabilities` — sonde ce que le téléphone sait faire (formats, plages dynamiques, focus…).
- `peaking/PeakingAnalyzer` — masque de netteté calculé sur le plan Y, double-buffered.
- `lock/VolumeKeyHandler` — machine à états des boutons volume (court / long / combo).
- `ui/` — Compose : HUD déverrouillé, panneau de réglages, HUD verrouillé, overlay de peaking.
