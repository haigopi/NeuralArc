from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


def load_font(path: str, size: int):
    try:
        return ImageFont.truetype(path, size)
    except Exception:
        return ImageFont.load_default()


def main() -> None:
    project = Path(__file__).resolve().parents[1]
    docs = project / "docs"

    out = docs / "og-image.png"
    logo_path = docs / "logo.png"

    w, h = 1200, 630
    img = Image.new("RGB", (w, h), "#ffb000")
    pixels = img.load()
    for y in range(h):
        for x in range(w):
            nx = x / (w - 1)
            ny = y / (h - 1)
            orange = (255, 132, 31)
            yellow = (255, 230, 81)
            purple = (129, 62, 255)
            magenta = (255, 64, 171)
            base = tuple(int(orange[i] * (1 - nx) + purple[i] * nx) for i in range(3))
            glow = tuple(int(yellow[i] * (1 - ny) + magenta[i] * ny) for i in range(3))
            pixels[x, y] = tuple(int(base[i] * 0.58 + glow[i] * 0.42) for i in range(3))

    draw = ImageDraw.Draw(img)
    draw.ellipse((-160, -180, 520, 500), fill="#ffe959")
    draw.ellipse((780, -160, 1370, 430), fill="#8a3dff")
    draw.ellipse((610, 330, 1320, 850), fill="#ff7a1f")
    draw.rounded_rectangle((36, 36, 1164, 594), radius=42, fill="#101323", outline="#ffe26a", width=3)
    draw.rounded_rectangle((58, 58, 1142, 572), radius=32, outline="#ff8a3d", width=2)

    logo_box = (88, 178, 318, 408)
    draw.rounded_rectangle(logo_box, radius=46, fill="#181a35", outline="#ffcf4a", width=4)
    if logo_path.exists():
        logo = Image.open(logo_path).convert("RGBA").resize((164, 164))
        img.paste(logo, (121, 211), logo)

    f_title = load_font("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 82)
    f_tagline = load_font("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 31)
    f_desc = load_font("/System/Library/Fonts/Supplemental/Arial.ttf", 29)

    text_x = 360
    draw.text((text_x, 185), "NeuralArc", fill="#edf6ff", font=f_title)
    draw.text((text_x, 286), "Privacy First | No Cloud | Desktop Trading", fill="#ffe26a", font=f_tagline)
    draw.line((text_x, 342, 1074, 342), fill="#ff8a3d", width=3)
    draw.text(
        (text_x, 378),
        "Turn your machine into an autonomous trader,",
        fill="#c9d9e7",
        font=f_desc,
    )
    draw.text((text_x, 416), "with every guardrail you need in place.", fill="#c9d9e7", font=f_desc)

    img.save(out, format="PNG")
    print(f"Generated {out}")


if __name__ == "__main__":
    main()
