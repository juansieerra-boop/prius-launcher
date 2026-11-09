import base64,math,random,re
A='#5CFF6C'; A2='#2FA83C'; A3='#143318'; BG='#050806'
car=base64.b64encode(open(__import__('sys').argv[1],'rb').read()).decode()
def t(x,y,s,txt,c=A,w=700,anchor='start',fam='DejaVu Sans Mono',ls=0,op=1): return f'<text x="{x}" y="{y}" font-size="{s}" fill="{c}" font-weight="{w}" text-anchor="{anchor}" font-family="{fam}" letter-spacing="{ls}" opacity="{op}">{txt}</text>'
def brk(x,y,w,h,l=14):  # corner brackets only
    c=A2; return ''.join(f'<path d="{d}" stroke="{c}" stroke-width="1.5" fill="none"/>' for d in [f'M{x},{y+l} V{y} H{x+l}',f'M{x+w-l},{y} H{x+w} V{y+l}',f'M{x},{y+h-l} V{y+h} H{x+l}',f'M{x+w-l},{y+h} H{x+w} V{y+h-l}'])
S=[f'<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="600"><rect width="1024" height="600" fill="{BG}"/>',
'<defs><filter id="g" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="2" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>']
# top line
S.append(t(32,50,30,'3:50',A,700)+t(124,50,14,'PM',A2)+t(170,50,14,'72°F  ☀',A2,700,fam='DejaVu Sans'))
S.append(t(992,48,14,'GPS ●  WIFI ●  BT ●',A2,700,'end'))
# speed
S.append(t(32,150,84,'35',A,700)+t(36,178,15,'MPH',A2,700,ls=2))
# car
S.append(f'<ellipse cx="380" cy="420" rx="250" ry="16" fill="{A}" opacity=".10" filter="url(#g)"/>')
S.append(f'<image href="data:image/png;base64,{car}" x="80" y="110" width="620" height="330" preserveAspectRatio="xMidYMid meet"/>')
S.append(t(32,476,13,'12.4 MI   ·   0:24   ·   MÁX 68',A2,700,ls=1))
# map card
mx,my,mw,mh=730,80,262,210
S.append(brk(mx,my,mw,mh))
random.seed(4); cx,cy=mx+mw/2,my+mh/2+6
S.append(f'<defs><clipPath id="mc"><rect x="{mx+6}" y="{my+6}" width="{mw-12}" height="{mh-12}" rx="4"/></clipPath></defs><g clip-path="url(#mc)">')
for i in range(8): S.append(f'<path d="M{mx},{my+i*30+random.randint(-6,6)} L{mx+mw},{my+i*30+random.randint(-14,14)}" stroke="{A3}" stroke-width="2.5"/>')
for i in range(9): S.append(f'<path d="M{mx+i*32+random.randint(-6,6)},{my} L{mx+i*32+random.randint(-14,14)},{my+mh}" stroke="{A3}" stroke-width="2.5"/>')
S.append(f'<path d="M{cx-4},{my+mh} C{cx-8},{cy+40} {cx},{cy+20} {cx},{cy} S{cx+40},{cy-36} {cx+70},{cy-70}" stroke="{A}" stroke-width="3" fill="none" opacity=".9"/></g>')
S.append(f'<path d="M{cx},{cy-11} L{cx+8},{cy+9} L{cx},{cy+5} L{cx-8},{cy+9} Z" fill="{A}" filter="url(#g)"/>')
S.append(t(mx,my+mh+26,15,'↗ 500 FT',A,700)+t(mx+mw,my+mh+26,13,'MAIN ST',A2,700,'end'))
# media
y0=356
S.append(brk(mx,y0,mw,104))
S.append(f'<rect x="{mx+14}" y="{y0+14}" width="54" height="54" rx="4" fill="{A3}"/>'+t(mx+41,y0+50,22,'♪',A,700,'middle','DejaVu Sans'))
S.append(t(mx+80,y0+36,16,'Chillin’',A,700)+t(mx+80,y0+56,12,'Spotify',A2,700))
S.append(f'<rect x="{mx+14}" y="{y0+82}" width="{mw-28}" height="2" fill="{A3}"/><rect x="{mx+14}" y="{y0+82}" width="90" height="2" fill="{A}"/>')
S.append(t(mx+mw-14,y0+56,15,'⏮  ⏸  ⏭',A,700,'end','DejaVu Sans'))
# dock line
S.append(f'<line x1="32" y1="510" x2="992" y2="510" stroke="{A3}" stroke-width="1"/>')
d='/home/claude/prius-launcher/app/src/main/res/drawable/'
def P(n): return re.search(r'pathData="([^"]+)"',open(d+n+'.xml').read()).group(1)
items=[('s','M4,11 L12,4 L20,11 V20 H14 V14 H10 V20 H4 Z'),('s','M3,11 L21,3 L13,21 L11,13 Z'),('f',P('ic_music')),('f',P('ic_phone')),('p','M12,5 V19 M5,12 H19'),('f',P('ic_gear'))]
for i,(k,v) in enumerate(items):
    x=40+i*72
    if k=='f': S.append(f'<g transform="translate({x},{534}) scale(1.1)"><path d="{v}" fill="{A}"/></g>')
    else: S.append(f'<g transform="translate({x},{534}) scale(1.1)"><path d="{v}" fill="none" stroke="{A if i<2 else A2}" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/></g>')
S.append('</svg>')
open(f'design/scripts/hud_min_'+__import__('os').path.basename(__import__('sys').argv[2])+'.svg','w').write(''.join(S))
from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    b=p.chromium.launch(executable_path='/usr/bin/google-chrome'); pg=b.new_page(viewport={'width':1024,'height':600},device_scale_factor=1.5)
    pg.goto('file://design/scripts/hud_min_'+__import__('os').path.basename(__import__('sys').argv[2])+'.svg'); pg.wait_for_timeout(800); pg.screenshot(path=__import__('sys').argv[2]); b.close()
