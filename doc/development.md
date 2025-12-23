# Building android-taranis-smartport-telemetry

1) Create keystore.properties file in project root with content:
storeFile=[path to keystore]
storePassword=[password for keystore]
keyAlias=[key alias]
keyPassword=[key password]

Replace all [...] with your values.

See https://developer.android.com/studio/publish/app-signing for more information.

2) Please download NDK 16.1.4479499. Other versions will not work!

3) Create local.properties file in project root with content:
sdk.dir=C\:\\Users\\name\\AppData\\Local\\Android\\Sdk
ndk.dir=C\:\\Users\\name\\AppData\\Local\\Android\\Sdk\\ndk\\16.1.4479499

(replace path with your values).

4) **This project requires older version Android Studio:**
 
  **Android Studio 4.2.1 May 13, 2021**  https://developer.android.com/studio/archive
   
Do not upgrade Graddle plugin when recommended by Android Studio.
