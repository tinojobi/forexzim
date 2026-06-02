"""
Image Text Overlay Tool for ZimRate
Stamps clean rendered text onto AI-generated images.
Usage: python3 text_overlay.py <input_image> <output_path>
Reads overlay config from stdin JSON.
"""

import sys, json
from PIL import Image, ImageDraw, ImageFont
import textwrap

# Find a clean font
import subprocess
FONTS = subprocess.check_output("fc-list :lang=en -f '%{file}\n' | grep -i 'sans' | head -5", shell=True, text=True).strip().split('\n')

def get_font(size, bold=False):
    for f in FONTS:
        if bold and 'Bold' in f:
            return ImageFont.truetype(f.strip(), size)
    for f in FONTS:
        if not bold:
            return ImageFont.truetype(f.strip(), size)
    return ImageFont.load_default()

def overlay_text(image_path, output_path, config):
    """
    config: {
        "elements": [
            {"type": "headline", "text": "...", "x": "center", "y": 80, "size": 48, "color": "#ffffff", "max_width": 1000},
            {"type": "stat", "text": "US$2.09", "x": "center", "y": 400, "size": 120, "color": "#facc15", "bold": true},
            {"type": "caption", "text": "per litre", "x": "center", "y": 480, "size": 36, "color": "#ffffffaa"},
            {"type": "badge", "text": "DIESEL", "x": 100, "y": 100, "size": 28, "color": "#ffffff", "bg": "#166534"}
        ]
    }
    """
    img = Image.open(image_path).convert('RGBA')
    overlay = Image.new('RGBA', img.size, (0,0,0,0))
    draw = ImageDraw.Draw(overlay)
    w, h = img.size

    for el in config.get('elements', []):
        text = el['text']
        size = el.get('size', 36)
        color = el.get('color', '#ffffff')
        bold = el.get('bold', False)
        max_width = el.get('max_width', w - 100)
        bg = el.get('bg')

        font = get_font(size, bold)

        # Handle x positioning
        x = el.get('x', 'center')
        y = el.get('y', 100)

        # Word wrap for long text
        if max_width:
            lines = textwrap.wrap(text, width=max_width // (size // 2))
        else:
            lines = [text]

        # Calculate total text height
        line_spacing = size * 1.3
        total_h = len(lines) * line_spacing

        for i, line in enumerate(lines):
            bbox = draw.textbbox((0, 0), line, font=font)
            line_w = bbox[2] - bbox[0]
            line_h = bbox[3] - bbox[1]

            if x == 'center':
                lx = (w - line_w) // 2
            elif x == 'right':
                lx = w - line_w - 100
            else:
                lx = x

            ly = y + i * line_spacing

            # Draw background if needed
            if bg:
                pad = 12
                draw.rounded_rectangle(
                    [lx - pad, ly - 4, lx + line_w + pad, ly + line_h + pad],
                    radius=8, fill=bg
                )

            draw.text((lx, ly), line, fill=color, font=font)

    # Composite and save
    result = Image.alpha_composite(img, overlay)
    result = result.convert('RGB')
    result.save(output_path, quality=92)
    print(f'Saved to {output_path}')

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print(json.dumps({"error": "Usage: text_overlay.py <input> <output>"}))
        sys.exit(1)

    input_path = sys.argv[1]
    output_path = sys.argv[2]
    config = json.load(sys.stdin)

    overlay_text(input_path, output_path, config)
