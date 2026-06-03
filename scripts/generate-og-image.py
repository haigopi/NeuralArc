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
    img = Image.new("RGB", (w, h), "#071522")
    draw = ImageDraw.Draw(img)

    draw.ellipse((-170, -160, 430, 440), fill="#0f3a32")
    draw.ellipse((850, -180, 1370, 340), fill="#172f4e")
    draw.rounded_rectangle((70, 70, 1130, 560), radius=34, fill="#0b1b2a", outline="#24445d", width=2)
    draw.rounded_rectangle((96, 96, 1104, 534), radius=26, outline="#17324a", width=1)

    logo_box = (126, 190, 346, 410)
    draw.rounded_rectangle(logo_box, radius=44, fill="#112638", outline="#2b5772", width=3)
    if logo_path.exists():
        logo = Image.open(logo_path).convert("RGBA").resize((164, 164))
        img.paste(logo, (154, 218), logo)

    f_title = load_font("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 82)
    f_tagline = load_font("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 31)
    f_desc = load_font("/System/Library/Fonts/Supplemental/Arial.ttf", 29)

    text_x = 410
    draw.text((text_x, 185), "NeuralArc", fill="#edf6ff", font=f_title)
    draw.text((text_x, 286), "Privacy First | No Cloud | Desktop Trading", fill="#7cf6c4", font=f_tagline)
    draw.line((text_x, 342, 1028, 342), fill="#2b5772", width=2)
    draw.text(
        (text_x, 378),
        "Autonomous insights and automation for a local-first",
        fill="#c9d9e7",
        font=f_desc,
    )
    draw.text((text_x, 416), "desktop trading workflow.", fill="#c9d9e7", font=f_desc)

    img.save(out, format="PNG")
    print(f"Generated {out}")


if __name__ == "__main__":
    main()
