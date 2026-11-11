import base64,math,random,sys
night=sys.argv[1]=='night'; out=sys.argv[2]
F="-apple-system,'Inter','Helvetica Neue','DejaVu Sans',sans-serif"
if night:
    P=dict(bg0='#05060C',bg1='#0B1022',panel0='#0E1326',panel1='#070A16',card0='#141A30',card1='#0C1022',side='#0D1122',
           line='#3A2E3A',text='#F6E9E4',muted='#A48E95',rg='#E8A598',rg2='#C98B7B',wine='#7A1F3D',ch='#E0244E',map='#0A1020',road='#1C2440',chip='#141A30')
else:
    P=dict(bg0='#F8EEE8',bg1='#F3E2DA',panel0='#FFF9F6',panel1='#F8ECE6',card0='#FFFAF7',card1='#F7EAE3',side='#FFF8F4',
           line='#E8CFC6',text='#4A1A28',muted='#9C7480',rg='#C98B7B',rg2='#B5705F',wine='#6E1A33',ch='#B3123A',map='#FBF1EC',road='#FFFFFF',chip='#FFF8F4')
def t(x,y,s,txt,c=None,w=500,anchor='start',op=1,ls=0): return f'<text x="{x}" y="{y}" font-size="{s}" fill="{c or P["text"]}" font-weight="{w}" text-anchor="{anchor}" font-family="{F}" opacity="{op}" letter-spacing="{ls}">{txt}</text>'
def card(x,y,w,h,r=22):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="url(#card)" stroke="url(#rgline)" stroke-width="1.2"/>'
def cherry(x,y,s=1):
    return (f'<g transform="translate({x},{y}) scale({s})"><path d="M0,0 C-2,8 -6,14 -8,20 M0,0 C3,9 6,15 8,21" stroke="{P["rg2"]}" stroke-width="1.8" fill="none" stroke-linecap="round"/>'
            f'<path d="M0,0 C6,-7 14,-7 18,-2 C12,2 5,2 0,0Z" fill="{P["rg"]}"/>'
            +''.join(f'<circle cx="{cx}" cy="{cy}" r="6.5" fill="url(#chg)"/><ellipse cx="{cx-2}" cy="{cy-2.5}" rx="1.7" ry="1.1" fill="#fff" opacity=".8"/>' for cx,cy in ((-8,24),(8,25)))+'</g>')
S=[f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="600" viewBox="0 0 1024 600"><defs>
<linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{P['bg0']}"/><stop offset="1" stop-color="{P['bg1']}"/></linearGradient>
<linearGradient id="panel" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{P['panel0']}"/><stop offset="1" stop-color="{P['panel1']}"/></linearGradient>
<linearGradient id="card" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{P['card0']}"/><stop offset="1" stop-color="{P['card1']}"/></linearGradient>
<linearGradient id="rgline" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{P['rg']}" stop-opacity=".9"/><stop offset=".5" stop-color="{P['rg']}" stop-opacity=".15"/><stop offset="1" stop-color="{P['rg']}" stop-opacity=".7"/></linearGradient>
<linearGradient id="rgfill" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{P['rg']}"/><stop offset="1" stop-color="{P['rg2']}"/></linearGradient>
<radialGradient id="chg" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="#F0567A"/><stop offset="1" stop-color="{P['wine']}"/></radialGradient>
<linearGradient id="art" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{P['rg']}"/><stop offset="1" stop-color="{P['wine']}"/></linearGradient>
<clipPath id="mapc"><rect x="606" y="14" width="404" height="326" rx="22"/></clipPath>
<clipPath id="carc"><rect x="86" y="14" width="510" height="572" rx="24"/></clipPath>
<filter id="glow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="3" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
<filter id="soft"><feGaussianBlur stdDeviation="2.2"/></filter>
</defs><rect width="1024" height="600" fill="url(#bg)"/>''']
random.seed(5)
if night:  # luces de ciudad desenfocadas y nieve digital apenas visible
    for _ in range(40):
        S.append(f'<circle cx="{random.uniform(0,1024):.0f}" cy="{random.uniform(0,600):.0f}" r="{random.uniform(1.5,5):.1f}" fill="{random.choice([P["rg"],"#8FA6FF",P["ch"]])}" opacity="{random.uniform(.05,.16):.2f}" filter="url(#soft)"/>')
# barra lateral
S.append(f'<rect x="10" y="14" width="66" height="572" rx="20" fill="{P["side"]}" stroke="url(#rgline)" stroke-width="1"/>')
S.append(cherry(43,26,.75))
S.append(t(43,78,15,'10:30',None,600,'middle')+t(43,94,9.5,'Wi-Fi · GPS',P['muted'],600,'middle'))
glyphs={'ZLink':'<g fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round"><rect x="-9" y="-13" width="18" height="26" rx="4"/><path d="M-4,8 h8"/></g>',
        'Mapas':'<path d="M-11,1 L12,-10 L3,13 L1,3 Z" fill="#fff"/>',
        'Música':'<g fill="#fff"><path d="M-4,8 V-9 L9,-12 V5" stroke="#fff" stroke-width="2.4" fill="none"/><circle cx="-7" cy="8" r="4.2"/><circle cx="6" cy="5" r="4.2"/></g>',
        'Teléfono':'<path d="M-10,-11 h5 l3,6 -3,2 a12,12 0 0 0 6,6 l2,-3 6,3 v5 a2,2 0 0 1 -2,2 a20,20 0 0 1 -19,-19 a2,2 0 0 1 2,-2 z" fill="#fff"/>'}
for i,(name,g) in enumerate(glyphs.items()):
    y=150+i*76
    S.append(f'<rect x="19" y="{y}" width="48" height="48" rx="13" fill="url(#rgfill)" stroke="#fff" stroke-opacity=".35"/><g transform="translate(43,{y+24})">{g}</g>'+t(43,y+64,10,name,P['muted'],500,'middle'))
S.append(f'<g fill="{P["text"]}">'+''.join(f'<rect x="{34+i*11}" y="{540+j*11}" width="8" height="8" rx="2.2"/>' for i in range(2) for j in range(2))+'</g>')
# panel del carro
S.append(f'<rect x="86" y="14" width="510" height="572" rx="24" fill="url(#panel)" stroke="url(#rgline)" stroke-width="1.2"/>')
if night:
    for _ in range(55):  # nieve digital (puntitos tenues, como radar)
        x=random.uniform(100,580); y=random.uniform(150,330)
        S.append(f'<circle cx="{x:.0f}" cy="{y:.0f}" r="{random.uniform(.8,1.8):.1f}" fill="#fff" opacity="{random.uniform(.12,.35):.2f}"/>')
else:
    for _ in range(30):
        x=random.uniform(100,580); y=random.uniform(150,330)
        S.append(f'<circle cx="{x:.0f}" cy="{y:.0f}" r="{random.uniform(1,2.4):.1f}" fill="{P["rg"]}" opacity="{random.uniform(.15,.4):.2f}"/>')
S.append(t(112,104,66,'35',None,200)+t(194,104,17,'mph',P['muted'],500))
S.append(cherry(470,58,.62)+t(488,64,12,'Feliz Navidad',P['rg'],600,ls=.5))
S.append(t(574,96,11,'GPS',P['muted'],600,'end')+f'<circle cx="582" cy="92" r="3.3" fill="{P["ch"]}"/>')
# mapa
S.append(f'<g clip-path="url(#mapc)"><rect x="606" y="14" width="404" height="326" fill="{P["map"]}"/>')
random.seed(7)
for i in range(14):
    x=600+random.randint(0,420); S.append(f'<path d="M{x},10 C{x+random.randint(-80,80)},120 {x+random.randint(-80,80)},220 {x+random.randint(-60,60)},350" stroke="{P["road"]}" stroke-width="{random.choice([2,3,5])}" fill="none" opacity="{.9 if not night else .7}"/>')
for i in range(8):
    y=20+i*45+random.randint(-10,10); S.append(f'<path d="M600,{y} C700,{y+random.randint(-30,30)} 850,{y+random.randint(-30,30)} 1015,{y+random.randint(-20,20)}" stroke="{P["road"]}" stroke-width="3" fill="none" opacity="{.9 if not night else .7}"/>')
route='M970,330 C960,290 960,260 930,240 C880,205 790,230 780,190 C770,150 850,140 900,110 C940,85 950,60 1000,40'
S.append(f'<path d="{route}" stroke="{P["rg"]}" stroke-width="16" stroke-opacity=".25" fill="none" stroke-linecap="round"/><path d="{route}" stroke="url(#rgfill)" stroke-width="7" fill="none" stroke-linecap="round" {"filter=\"url(#glow)\"" if night else ""}/>')
S.append(t(860,170,11,'Peters Canyon',P['muted'],500))
S.append(f'<circle cx="955" cy="275" r="17" fill="{P["chip"]}"/><circle cx="955" cy="275" r="13" fill="{P["ch"]}"/><path d="M955,265 L962,283 L955,279 L948,283 Z" fill="#fff"/></g>')
S.append(f'<rect x="606" y="14" width="404" height="326" rx="22" fill="none" stroke="url(#rgline)" stroke-width="1.2"/>')
S.append(f'<rect x="618" y="26" width="128" height="32" rx="16" fill="{P["chip"]}" fill-opacity=".92" stroke="url(#rgline)"/><path d="M636,49 L646,33 L644,45 L651,45 Z" fill="{P["ch"]}"/>'+t(658,47,14,'Navegación',None,600))
S.append(f'<rect x="618" y="262" width="150" height="64" rx="14" fill="{P["chip"]}" fill-opacity=".94" stroke="url(#rgline)"/>'+t(632,290,22,'12 min',None,700)+t(632,312,12,'al destino',P['muted'],500))
# música
S.append(card(606,352,200,234))
S.append(f'<rect x="620" y="366" width="22" height="22" rx="6" fill="url(#rgfill)"/>'+t(650,383,14,'Música',None,600)+cherry(784,364,.5))
S.append(f'<rect x="620" y="402" width="64" height="64" rx="10" fill="url(#art)"/>'+cherry(652,416,.75))
S.append(t(694,428,15,'Navidad',None,600)+t(694,448,12,'Playlist',P['muted'],500))
S.append(f'<g fill="{P["text"]}"><rect x="639" y="505" width="3" height="14" rx="1"/><path d="M654,505 v14 l-11,-7 z"/><path d="M758,505 v14 l11,-7 z"/><rect x="770" y="505" width="3" height="14" rx="1"/></g>')
S.append(f'<circle cx="706" cy="512" r="23" fill="url(#chg)" {"filter=\"url(#glow)\"" if night else ""}/><path d="M700,502 l15,10 -15,10 z" fill="#fff"/>')
S.append(f'<rect x="620" y="556" width="172" height="4" rx="2" fill="{P["line"]}"/><rect x="620" y="556" width="70" height="4" rx="2" fill="url(#rgfill)"/>')
# clima
S.append(card(818,352,192,234))
S.append(f'<rect x="832" y="366" width="22" height="22" rx="6" fill="url(#rgfill)"/>'+''.join(f'<line x1="843" y1="371" x2="843" y2="383" stroke="#fff" stroke-width="1.6" transform="rotate({a} 843 377)"/>' for a in (0,60,120))+t(862,383,14,'Clima',None,600))
S.append(t(832,420,12,'Irvine',P['muted'],500)+t(832,470,46,'58°',None,300))
S.append('<g transform="translate(968,440)">'+''.join(f'<g transform="rotate({a})"><line x1="0" y1="-16" x2="0" y2="16" stroke="{P["rg"]}" stroke-width="2.4" stroke-linecap="round"/><path d="M-4,-12 L0,-8 L4,-12 M-4,12 L0,8 L4,12" stroke="{P["rg"]}" stroke-width="1.8" fill="none"/></g>' for a in (0,60,120))+'</g>')
S.append(t(832,494,14,'Fresco y despejado',None,600)+t(832,513,12,'Máx 64°  Mín 49°',P['muted'],500))
S.append(f'<line x1="832" y1="528" x2="996" y2="528" stroke="{P["line"]}"/>')
for i,(h,tmp) in enumerate([('6 PM','56°'),('7 PM','54°'),('8 PM','52°'),('9 PM','51°')]):
    x=846+i*44; S.append(t(x,548,10,h,P['muted'],500,'middle')+f'<circle cx="{x}" cy="560" r="4" fill="{P["rg"]}"/>'+t(x,580,12,tmp,None,600,'middle'))
S.append('</svg>')
svg=''.join(S); open(out.replace('.png','.svg'),'w').write(svg)
from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    b=p.chromium.launch(executable_path='/usr/bin/google-chrome'); pg=b.new_page(viewport={'width':1024,'height':600},device_scale_factor=1.5)
    pg.set_content(f'<html><body style="margin:0">{svg}</body></html>'); pg.wait_for_timeout(300); pg.screenshot(path=out); b.close()
