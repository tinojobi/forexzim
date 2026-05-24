#!/usr/bin/env python3
from PIL import Image, ImageDraw, ImageFont, ImageFilter
from pathlib import Path
import math, random

W, H = 1200, 630
OUT = Path('/opt/forexzim/blog/drafts/2026-05-24-zimbabwe-counterfeit-goods-law-hero.png')
random.seed(7)

# Palette: consumer-protection official notice meets market-stall evidence board.
cream = (246, 238, 215)
paper = (252, 247, 232)
charcoal = (31, 38, 34)
forest = (22, 83, 45)
deep_green = (12, 58, 37)
amber = (199, 139, 48)
muted_red = (142, 45, 38)
ink_faint = (31, 38, 34, 35)
grey = (92, 102, 94)

img = Image.new('RGB', (W, H), cream)
d = ImageDraw.Draw(img)

# Subtle paper grain
pix = img.load()
for y in range(H):
    for x in range(W):
        n = random.randint(-5, 5)
        r, g, b = pix[x, y]
        pix[x, y] = (max(0, min(255, r+n)), max(0, min(255, g+n)), max(0, min(255, b+n)))

def font(name, size):
    return ImageFont.truetype(name, size)
serif_bold = '/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf'
serif = '/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf'
sans_bold = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
sans = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
mono = '/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf'

# Background ledger grid, deliberately imperfect
for x in range(40, W, 80):
    d.line([(x, 30), (x, H-36)], fill=(219, 205, 170), width=1)
for y in range(50, H, 58):
    d.line([(24, y), (W-24, y)], fill=(224, 211, 180), width=1)

# Diagonal seized-tape band
band = Image.new('RGBA', (W, H), (0,0,0,0))
bd = ImageDraw.Draw(band)
for offset in [-80, 0, 80]:
    bd.polygon([(-80, 500+offset), (W+160, 210+offset), (W+160, 262+offset), (-80, 552+offset)], fill=(229, 177, 67, 210))
    bd.line([(-80, 500+offset), (W+160, 210+offset)], fill=(112, 76, 26, 110), width=2)
band = band.filter(ImageFilter.GaussianBlur(0.3))
img = Image.alpha_composite(img.convert('RGBA'), band)
d = ImageDraw.Draw(img)

# Left editorial text block
margin = 74
# Masthead
small_caps = font(sans_bold, 22)
d.text((margin, 50), 'ZIMRATE FIELD BRIEF', font=small_caps, fill=forest)
d.line((margin, 83, 430, 83), fill=amber, width=4)
d.text((margin, 96), 'CONSUMER PROTECTION • MAY 2026', font=font(mono, 18), fill=grey)

# Main headline
headline = ['THE FAKE', 'GOODS', 'CRACKDOWN']
y = 145
for i, line in enumerate(headline):
    size = 80 if line == 'THE FAKE' else 108 if line == 'GOODS' else 66
    f = font(serif_bold, size)
    d.text((margin, y), line, font=f, fill=charcoal if i != 1 else deep_green)
    y += int(size * (0.86 if line != 'CRACKDOWN' else 0.78))

# Pull quote slab
quote_x, quote_y = margin, 418
d.rounded_rectangle((quote_x, quote_y, 557, 506), radius=0, fill=(244, 231, 197), outline=(186, 147, 80), width=2)
d.text((quote_x+22, quote_y+15), '50% FAILED', font=font(sans_bold, 36), fill=muted_red)
d.text((quote_x+24, quote_y+57), 'sampled shelf standards', font=font(sans, 20), fill=charcoal)

# Evidence board on right
board_x, board_y, board_w, board_h = 690, 58, 430, 420
# shadow
d.rounded_rectangle((board_x+14, board_y+16, board_x+board_w+14, board_y+board_h+16), radius=16, fill=(92, 77, 45, 55))
d.rounded_rectangle((board_x, board_y, board_x+board_w, board_y+board_h), radius=16, fill=paper, outline=(59, 76, 61), width=3)
# top clip
d.rounded_rectangle((board_x+168, board_y-24, board_x+300, board_y+26), radius=10, fill=(171, 174, 157), outline=(90, 94, 83), width=2)
d.ellipse((board_x+217, board_y-9, board_x+251, board_y+25), fill=(93, 98, 88))

# Red stamp rotated
stamp = Image.new('RGBA', (270, 100), (0,0,0,0))
sd = ImageDraw.Draw(stamp)
sd.rounded_rectangle((8, 14, 262, 86), radius=10, outline=muted_red+(210,), width=5)
sd.text((34, 27), 'SEIZED', font=font(sans_bold, 43), fill=muted_red+(210,))
sd.text((57, 70), 'SUBSTANDARD GOODS', font=font(mono, 13), fill=muted_red+(190,))
stamp = stamp.rotate(-10, expand=True)
img.alpha_composite(stamp, (board_x+105, board_y+74))
d = ImageDraw.Draw(img)

# Evidence/product silhouettes on board
items = [
    ('TOOTHPASTE', board_x+58, board_y+230, 150, 40, (231,235,224), (42,95,65)),
    ('FLOUR', board_x+248, board_y+220, 105, 118, (238,223,188), (143,93,45)),
    ('RICE', board_x+78, board_y+318, 108, 84, (245,244,229), (95,92,74)),
    ('VASELINE', board_x+242, board_y+350, 138, 46, (228,233,236), (39,89,116)),
]
for label, x, y0, w, h, fill, accent in items:
    d.rounded_rectangle((x, y0, x+w, y0+h), radius=9, fill=fill, outline=(112,116,102), width=2)
    d.rectangle((x+8, y0+8, x+w-8, y0+min(30,h-8)), fill=accent)
    d.text((x+14, y0+12), label, font=font(sans_bold, 13), fill=(255,252,238))
    d.line((x-10, y0-10, x+w+10, y0+h+8), fill=muted_red, width=4)

# Checklist labels on evidence board
check_f = font(mono, 18)
d.text((board_x+40, board_y+36), 'INSPECTION FILE', font=font(sans_bold, 24), fill=forest)
for i, t in enumerate(['5,087 businesses inspected', '560+ prosecutions finalised', '6,189 seizures effected']):
    yy = board_y + 142 + i*28
    d.rectangle((board_x+45, yy+4, board_x+59, yy+18), outline=forest, width=2)
    d.line((board_x+47, yy+11, board_x+53, yy+17), fill=forest, width=2)
    d.line((board_x+53, yy+17, board_x+63, yy+3), fill=forest, width=2)
    d.text((board_x+74, yy), t, font=check_f, fill=charcoal)

# Bottom editorial band
band_y = 538
d.rectangle((0, band_y, W, H), fill=deep_green)
d.line((0, band_y, W, band_y), fill=amber, width=5)
footer = [
    ('LAW ON TABLE', 'Counterfeit Act'),
    ('MARKET IMPACT', 'Formal retailers'),
    ('HIDDEN COST', 'Households'),
]
for i, (lab, val) in enumerate(footer):
    x = 78 + i*360
    d.text((x, band_y+22), lab, font=font(mono, 17), fill=(220, 196, 132))
    d.text((x, band_y+48), val, font=font(serif_bold, 28), fill=(255, 250, 231))

# Brand mark small
brand = 'ZimRate'
tw = d.textbbox((0,0), brand, font=font(sans_bold, 24))[2]
d.text((W-42-tw, 48), brand, font=font(sans_bold, 24), fill=forest)
d.text((W-157, 80), 'USD/ZiG', font=font(mono, 14), fill=grey)

# Slight vignette
v = Image.new('L', (W, H), 0)
vd = ImageDraw.Draw(v)
vd.rectangle((0,0,W,H), fill=0)
for i in range(80):
    vd.rectangle((i, i, W-i, H-i), outline=int(i*1.4))
v = v.filter(ImageFilter.GaussianBlur(24))
overlay = Image.new('RGBA', (W,H), (0,0,0,0))
overlay.putalpha(v.point(lambda p: min(55, p//4)))
img = Image.alpha_composite(img, overlay)

OUT.parent.mkdir(parents=True, exist_ok=True)
img.convert('RGB').save(OUT, quality=95)
print(OUT)
