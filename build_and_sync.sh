#!/usr/bin/env bash
set -e

echo "=== Rozpoczynanie procesu budowania i synchronizacji APK FitKuchnia ==="

# 1. Pobranie wersji z app/build.gradle.kts
VERSION=$(grep 'versionName = ' app/build.gradle.kts | head -n 1 | sed 's/.*versionName = "\(.*\)".*/\1/')
if [ -z "$VERSION" ]; then
  VERSION="1.3.2"
fi

echo "-> Wykryta wersja aplikacji: v${VERSION}"

# 2. Czysta kompilacja APK
echo "-> Uruchamianie kompilacji: gradle assembleDebug..."
gradle assembleDebug

APK_SRC="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_SRC" ]; then
  echo "BŁĄD: Plik wyjściowy $APK_SRC nie istnieje!"
  exit 1
fi

APK_SIZE=$(stat -c%s "$APK_SRC" 2>/dev/null || stat -f%z "$APK_SRC" 2>/dev/null || echo "ok")
echo "-> Skompilowano pomyślnie! Rozmiar APK: $APK_SIZE bajtów"

# 3. Kopiowanie do ścieżek pobierania
mkdir -p public

echo "-> Usuwanie starych wersji APK z katalogu głównego..."
find . -maxdepth 1 -name "FitKuchnia-v*.apk" ! -name "FitKuchnia-v${VERSION}.apk" -exec rm -f {} +
find ./public -maxdepth 1 -name "FitKuchnia-v*.apk" ! -name "FitKuchnia-v${VERSION}.apk" -exec rm -f {} +

echo "-> Kopiowanie do ścieżek pobierania..."
cp -f "$APK_SRC" "./FitKuchnia.apk"
cp -f "$APK_SRC" "./FitKuchnia-v${VERSION}.apk"
cp -f "$APK_SRC" "./public/FitKuchnia.apk"
cp -f "$APK_SRC" "./public/app-debug.apk"
cp -f "$APK_SRC" "./public/FitKuchnia-v${VERSION}.apk"

# 4. Aktualizacja strony pobierania /public/index.html z aktualną wersją
BUILD_DATE=$(date "+%Y-%m-%d %H:%M:%S")

cat <<EOF > public/index.html
<!DOCTYPE html>
<html lang="pl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>FitKuchnia v${VERSION} - Pobierz najnowszy APK</title>
    <style>
        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        }
        body {
            background-color: #0F1416;
            color: #F0F4F5;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            padding: 24px;
            text-align: center;
        }
        .card {
            background-color: #171F22;
            border: 1.5px solid rgba(0, 230, 118, 0.4);
            border-radius: 24px;
            padding: 32px 24px;
            max-width: 460px;
            width: 100%;
            box-shadow: 0 16px 40px rgba(0, 0, 0, 0.6);
        }
        .icon {
            font-size: 56px;
            margin-bottom: 16px;
            display: inline-block;
        }
        h1 {
            font-size: 26px;
            font-weight: 800;
            color: #FFFFFF;
            margin-bottom: 8px;
        }
        .badge {
            display: inline-block;
            background-color: rgba(0, 230, 118, 0.15);
            color: #00E676;
            border: 1px solid #00E676;
            border-radius: 999px;
            padding: 6px 14px;
            font-size: 13px;
            font-weight: 700;
            margin-bottom: 16px;
        }
        p {
            color: #9BA3A7;
            font-size: 15px;
            line-height: 1.5;
            margin-bottom: 24px;
        }
        .btn {
            display: block;
            width: 100%;
            background-color: #00E676;
            color: #0F1416;
            text-decoration: none;
            font-weight: 800;
            font-size: 16px;
            padding: 15px 20px;
            border-radius: 14px;
            margin-bottom: 10px;
            box-shadow: 0 4px 16px rgba(0, 230, 118, 0.3);
            transition: all 0.2s ease;
        }
        .btn:hover, .btn:active {
            background-color: #00C853;
            transform: translateY(-2px);
        }
        .btn-alt {
            background-color: transparent;
            color: #00E676;
            border: 1.5px solid rgba(0, 230, 118, 0.5);
            font-weight: 600;
            font-size: 14px;
            padding: 12px 16px;
            box-shadow: none;
        }
        .meta {
            margin-top: 24px;
            font-size: 12px;
            color: #6C757D;
            border-top: 1px solid rgba(255, 255, 255, 0.08);
            padding-top: 16px;
            line-height: 1.6;
        }
    </style>
</head>
<body>
    <div class="card">
        <div class="icon">🥗</div>
        <h1>FitKuchnia</h1>
        <div class="badge">Najnowszy plik APK: v${VERSION}</div>
        <p>Pobierz i zainstaluj najnowszą wersję aplikacji bezpośrednio na telefonie Android. Wszelkie zmiany i moduły są wbudowane w ten plik.</p>
        
        <a href="/FitKuchnia-v${VERSION}.apk" class="btn" download>Pobierz FitKuchnia v${VERSION} (APK)</a>
        <a href="/FitKuchnia.apk" class="btn btn-alt" download>Pobierz FitKuchnia.apk</a>
        <a href="/app-debug.apk" class="btn btn-alt" download>Alternatywny link (app-debug.apk)</a>
        
        <div class="meta">
            Wersja: <strong>${VERSION}</strong><br>
            Data i czas kompilacji: <strong>${BUILD_DATE}</strong><br>
            Przed instalacją odinstaluj starą wersję z telefonu (aby usunąć stary cache).
        </div>
    </div>
</body>
</html>
EOF

echo "=== Zakończono! Wszystkie pliki APK zostały pomyślnie zaktualizowane do wersji v${VERSION} ==="
ls -lh ./FitKuchnia*.apk ./public/*.apk
