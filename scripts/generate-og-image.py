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
    img = Image.new("RGB", (w, h), "#081724")
    draw = ImageDraw.Draw(img)

    draw.ellipse((-120, -140, 380, 360), fill="#123a34")
    draw.ellipse((920, -140, 1320, 260), fill="#1b2f4a")
    draw.rounded_rectangle((70, 70, 1130, 560), radius=24, fill="#0d1d2b", outline="#25445c", width=2)

    draw.rounded_rectangle((120, 130, 192, 202), radius=16, fill="#122636", outline="#2b526b", width=2)
    if logo_path.exists():
        logo = Image.open(logo_path).convert("RGBA").resize((52, 52))
        img.paste(logo, (130, 140), logo)

    f_title = load_font("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 44)
    f_sub = load_font("/System/Library/Fonts/Supplemental/Arial.ttf", 21)
    f_badge = load_font("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 22)
    f_line = load_font("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 36)
    f_note = load_font("/System/Library/Fonts/Supplemental/Arial.ttf", 26)
    f_soon = load_font("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 30)

    draw.text((216, 136), "NeuralArc", fill="#ebf4fb", font=f_title)
    draw.text((216, 178), "Privacy-First Desktop Trading", fill="#9eb5c8", font=f_sub)

    draw.rounded_rectangle((120, 240, 885, 294), radius=26, fill="#102230", outline="#2d5c77", width=2)
    draw.text((142, 255), "No Cloud  •  Runs on Your Local  •  Paper First", fill="#7cf6c4", font=f_badge)

    draw.text((120, 345), "Auto Analyze + Proprietary Recommendations", fill="#ebf4fb", font=f_line)
    draw.text((120, 392), "Short-term and long-term signals with local-first control.", fill="#c6d8e7", font=f_note)

    draw.rounded_rectangle((120, 440, 1080, 516), radius=14, fill="#133025", outline="#2b7558", width=2)
    draw.text((142, 462), "Nano Jetson Local Home AI Integration Coming Soon", fill="#7cf6c4", font=f_soon)

    img.save(out, format="PNG")
    print(f"Generated {out}")


if __name__ == "__main__":
    main()

