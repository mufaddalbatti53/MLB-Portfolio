#!/usr/bin/env python3
from pathlib import Path
from io import BytesIO
import cairosvg
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parents[1] / 'frontend' / 'assets'
root.mkdir(exist_ok=True)
paths = '''<path d="M20 72 V24 L42 50 L64 24 V72" fill="none" stroke="url(#brand)" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M83 24 V72 H106" fill="none" stroke="url(#brand)" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>
<path d="M119 72 V24 H141 C157 24 160 42 144 47 H119 M119 47 H144 C162 47 162 72 140 72 H119" fill="none" stroke="url(#brand)" stroke-width="11" stroke-linecap="round" stroke-linejoin="round"/>'''
base = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 180 96" role="img" aria-labelledby="logo-title"><title id="logo-title">MLB monogram</title><defs><linearGradient id="brand" x1="0%" y1="0%" x2="100%" y2="70%"><stop offset="0%" stop-color="#3B66F5"/><stop offset="53%" stop-color="#7A5AF8"/><stop offset="100%" stop-color="#C84B9E"/></linearGradient></defs>'''+paths+'</svg>'
(root / 'logo.svg').write_text(base + '\n', encoding='utf-8')
icon = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 192 192" role="img" aria-labelledby="icon-title"><title id="icon-title">MLB favicon</title><defs><linearGradient id="brand" x1="0%" y1="0%" x2="100%" y2="70%"><stop offset="0%" stop-color="#3B66F5"/><stop offset="53%" stop-color="#7A5AF8"/><stop offset="100%" stop-color="#C84B9E"/></linearGradient></defs><rect x="2" y="2" width="188" height="188" rx="40" fill="#0B0F19"/><g transform="translate(5 48)">'''+paths+'</g></svg>'
(root / 'favicon.svg').write_text(icon + '\n', encoding='utf-8')
for size, name in [(32,'favicon-32x32.png'),(192,'icon-192.png'),(512,'icon-512.png'),(180,'apple-touch-icon.png')]:
    data=cairosvg.svg2png(bytestring=icon.encode(),output_width=size,output_height=size)
    (root / name).write_bytes(data)
Image.open(BytesIO(cairosvg.svg2png(bytestring=icon.encode(),output_width=64,output_height=64))).save(root/'favicon.ico',format='ICO',sizes=[(16,16),(32,32),(48,48),(64,64)])
w,h=1200,630
im=Image.new('RGB',(w,h),(11,15,25));d=ImageDraw.Draw(im)
for x in range(w):
    r=int(59+(200-59)*x/w);g=int(102+(75-102)*x/w);b=int(245+(158-245)*x/w)
    d.line([(x,0),(x,10)],fill=(r,g,b),width=1)
d.rounded_rectangle((80,105,1120,530),radius=40,fill=(19,26,42),outline=(45,56,82),width=2)
logo=Image.open(BytesIO(cairosvg.svg2png(bytestring=icon.encode(),output_width=270,output_height=270))).convert('RGBA')
im.paste(logo,(130,172),logo)
try:
    title=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',75)
    body=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',33)
except OSError:
    title=ImageFont.load_default();body=ImageFont.load_default()
d.text((465,208),'MLB Portfolio',font=title,fill=(248,250,252))
d.text((465,331),'Mufaddal Battiwala',font=body,fill=(148,163,184))
d.text((465,387),'Java  •  Spring Boot  •  Web',font=body,fill=(171,159,255))
im.save(root/'og-preview.png')
print('Generated crisp SVG logo and 6 raster assets')
