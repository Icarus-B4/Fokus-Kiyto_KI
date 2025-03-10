import os
from PIL import Image, ImageDraw

# Größen für verschiedene Auflösungen
icon_sizes = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192
}

# Pfade zu den mipmap-Ordnern
mipmap_folders = {
    "mdpi": "app/src/main/res/mipmap-mdpi",
    "hdpi": "app/src/main/res/mipmap-hdpi",
    "xhdpi": "app/src/main/res/mipmap-xhdpi",
    "xxhdpi": "app/src/main/res/mipmap-xxhdpi",
    "xxxhdpi": "app/src/main/res/mipmap-xxxhdpi"
}

def create_target_icon(size):
    """Erstellt ein Zielscheiben-Icon mit der angegebenen Größe"""
    # Transparentes Bild erstellen
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Mittelpunkt
    center = size // 2
    
    # Größen der Kreise relativ zur Gesamtgröße
    outer_radius = int(size * 0.35)
    middle_radius = int(size * 0.25)
    inner_radius = int(size * 0.15)
    center_radius = int(size * 0.05)
    
    # Äußerer Kreis (grau)
    draw.ellipse(
        (center - outer_radius, center - outer_radius, 
         center + outer_radius, center + outer_radius), 
        fill=(156, 156, 156, 180)  # #B8BA9C9C (grau mit Transparenz)
    )
    
    # Mittlerer Kreis (dunkelrot)
    draw.ellipse(
        (center - middle_radius, center - middle_radius, 
         center + middle_radius, center + middle_radius), 
        fill=(135, 30, 0, 128)  # #80871E00 (dunkelrot mit Transparenz)
    )
    
    # Innerer Kreis (weiß)
    draw.ellipse(
        (center - inner_radius, center - inner_radius, 
         center + inner_radius, center + inner_radius), 
        fill=(255, 255, 255, 188)  # #BCFFFFFF (weiß mit Transparenz)
    )
    
    # Fadenkreuz (weiß)
    line_width = max(1, size // 108)  # Mindestens 1 Pixel breit
    
    # Vertikale Linie
    draw.line(
        (center, center - outer_radius * 1.5, center, center + outer_radius * 1.5), 
        fill=(255, 255, 255), width=line_width
    )
    
    # Horizontale Linie
    draw.line(
        (center - outer_radius * 1.5, center, center + outer_radius * 1.5, center), 
        fill=(255, 255, 255), width=line_width
    )
    
    # Mittelpunkt (rot)
    draw.ellipse(
        (center - center_radius, center - center_radius, 
         center + center_radius, center + center_radius), 
        fill=(255, 82, 82)  # #FF5252 (rot)
    )
    
    return img

# Icons für alle Auflösungen erstellen
for density, size in icon_sizes.items():
    folder = mipmap_folders[density]
    png_file = os.path.join(folder, "ic_launcher_round.png")
    
    try:
        print(f"Erstelle Icon für {density} ({size}x{size}px)")
        img = create_target_icon(size)
        img.save(png_file, "PNG")
        print(f"Erfolgreich erstellt: {png_file}")
    except Exception as e:
        print(f"Fehler beim Erstellen von {png_file}: {e}")

print("Icon-Erstellung abgeschlossen!") 