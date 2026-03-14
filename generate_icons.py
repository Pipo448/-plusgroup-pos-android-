from PIL import Image
import os, base64

# Logo PLUS GROUP an base64 — nou pral mete l dirèkteman
logo_path = "app/src/main/assets/assets/logo.webp"

sizes = {
    "app/src/main/res/mipmap-mdpi": 48,
    "app/src/main/res/mipmap-hdpi": 72,
    "app/src/main/res/mipmap-xhdpi": 96,
    "app/src/main/res/mipmap-xxhdpi": 144,
    "app/src/main/res/mipmap-xxxhdpi": 192,
}

img = Image.open(logo_path).convert("RGBA")
bg_size = max(img.size)
bg = Image.new("RGBA", (bg_size, bg_size), (255, 255, 255, 255))
bg.paste(img, ((bg_size - img.width) // 2, (bg_size - img.height) // 2), img)

for folder, size in sizes.items():
    os.makedirs(folder, exist_ok=True)
    resized = bg.resize((size, size), Image.LANCZOS)
    resized.convert("RGB").save(f"{folder}/ic_launcher.png")
    resized.convert("RGB").save(f"{folder}/ic_launcher_round.png")
    print(f"✅ {folder} ({size}x{size})")
