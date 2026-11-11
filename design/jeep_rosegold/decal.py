# Diseño "Cherry & Vine" en los costados del Jeep (oro rosa y vino), integrado como gráfico de fábrica
import base64,math
from playwright.sync_api import sync_playwright
W,H=1400,731
RG1,RG2,WINE,CH='#E2B3A3','#B97A6B','#6E1A33','#B3123A'
def leaf(x,y,ang,s=1):
    return f'<path transform="translate({x},{y}) rotate({ang}) scale({s})" d="M0,0 C6,-9 18,-10 26,0 C18,10 6,9 0,0 Z" fill="url(#rg)" opacity=".95"/><path transform="translate({x},{y}) rotate({ang}) scale({s})" d="M1,0 L22,0" stroke="{WINE}" stroke-width=".8" opacity=".55"/>'
def cherries(x,y,s=1):
    g=f'<g transform="translate({x},{y}) scale({s})">'
    g+=f'<path d="M0,0 C-2,8 -6,14 -8,20 M0,0 C3,9 6,15 8,21" stroke="{WINE}" stroke-width="1.6" fill="none" stroke-linecap="round"/>'
    for cx,cy in ((-8,24),(8,25)):
        g+=f'<circle cx="{cx}" cy="{cy}" r="6.2" fill="url(#ch)"/><ellipse cx="{cx-2}" cy="{cy-2.4}" rx="1.6" ry="1.1" fill="#fff" opacity=".8"/>'
    return g+'</g>'
def vine(pts):
    d='M'+' C'.join([f'{pts[0][0]},{pts[0][1]}']+[f'{a[0]},{a[1]} {b[0]},{b[1]} {c[0]},{c[1]}' for a,b,c in zip(pts[1::3],pts[2::3],pts[3::3])])
    return f'<path d="{d}" stroke="url(#rg)" stroke-width="2.6" fill="none" stroke-linecap="round"/>'
art=[]
# puerta delantera
art.append(vine([(822,452),(870,438),(900,418),(940,412),(975,406),(995,392),(1008,372)]))
art.append(f'<path d="M905,418 C915,400 930,392 948,390" stroke="url(#rg)" stroke-width="1.8" fill="none"/>')
for x,y,a,s in [(850,440,-160,.9),(880,428,-40,.8),(918,414,200,.85),(952,406,-30,.75),(985,398,-120,.7),(948,390,-60,.6)]: art.append(leaf(x,y,a,s))
art+= [cherries(902,418,.95),cherries(968,407,.85),cherries(994,386,.75)]
# puerta trasera
art.append(vine([(1034,438),(1060,430),(1080,412),(1100,404),(1118,398),(1130,384),(1138,366)]))
for x,y,a,s in [(1055,431,-150,.8),(1088,410,-40,.75),(1120,396,-140,.7)]: art.append(leaf(x,y,a,s))
art+= [cherries(1080,412,.85),cherries(1118,384,.7)]
svg=f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}"><defs>
<linearGradient id="rg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{RG1}"/><stop offset="1" stop-color="{RG2}"/></linearGradient>
<radialGradient id="ch" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="#E0476A"/><stop offset="1" stop-color="{WINE}"/></radialGradient>
<clipPath id="doors"><path d="M816,330 L1010,330 L1010,446 L992,462 L832,468 L816,456 Z"/><path d="M1028,330 L1142,330 L1137,420 L1112,445 L1030,448 Z"/></clipPath>
</defs><g clip-path="url(#doors)">{''.join(art)}</g></svg>'''
open('decal.svg','w').write(svg)
with sync_playwright() as p:
    b=p.chromium.launch(executable_path='/usr/bin/google-chrome'); pg=b.new_page(viewport={'width':W,'height':H})
    pg.set_content(f'<html><body style="margin:0;background:transparent">{svg}</body></html>'); pg.screenshot(path='decal.png',omit_background=True); b.close()
import cv2,numpy as np
car=cv2.imread('/home/claude/assets2/jeep_plain.png',cv2.IMREAD_UNCHANGED).astype(np.float32)
d=cv2.imread('decal.png',cv2.IMREAD_UNCHANGED).astype(np.float32)
a=d[...,3:]/255*0.92
# "multiplicar" para que las líneas del dibujo sigan encima del gráfico
mult=car[...,:3]*(d[...,:3]/255)
car[...,:3]=car[...,:3]*(1-a)+mult*a
cv2.imwrite('jeep_cherry.png',car.clip(0,255).astype(np.uint8))
al=car[...,3:]/255; v=(car[...,:3]*al+255*(1-al)).astype(np.uint8)
cv2.imwrite('decal_z.png',cv2.resize(v[300:500,780:1180],None,fx=2,fy=2))
