import os
from PIL import Image

# Pfade zu den mipmap-Ordnern
mipmap_folders = [
    "app/src/main/res/mipmap-hdpi",
    "app/src/main/res/mipmap-mdpi",
    "app/src/main/res/mipmap-xhdpi",
    "app/src/main/res/mipmap-xxhdpi",
    "app/src/main/res/mipmap-xxxhdpi"
]

# Konvertiere WEBP zu PNG
for folder in mipmap_folders:
    webp_file = os.path.join(folder, "ic_launcher_round.webp")
    png_file = os.path.join(folder, "ic_launcher_round.png")
    
    if os.path.exists(webp_file):
        try:
            print(f"Konvertiere {webp_file} zu {png_file}")
            img = Image.open(webp_file)
            img.save(png_file, "PNG")
            print(f"Erfolgreich konvertiert: {png_file}")
        except Exception as e:
            print(f"Fehler beim Konvertieren von {webp_file}: {e}")
    else:
        print(f"Datei nicht gefunden: {webp_file}")

print("Konvertierung abgeschlossen!") 