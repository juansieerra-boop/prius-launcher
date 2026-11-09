import base64,random,math
car=base64.b64encode(open('/home/claude/ns/car_ns.png','rb').read()).decode()
A='#3E9BFF'
F="-apple-system,'Inter','Helvetica Neue','DejaVu Sans',sans-serif"
def t(x,y,s,txt,c='#fff',w=500,anchor='start',op=1): return f'<text x="{x}" y="{y}" font-size="{s}" fill="{c}" font-weight="{w}" text-anchor="{anchor}" font-family="{F}" opacity="{op}">{txt}</text>'
S=[f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="600" viewBox="0 0 1024 600">
<defs>
<linearGradient id="panel" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#16191E"/><stop offset="1" stop-color="#0B0D10"/></linearGradient>
<linearGradient id="card" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#2A2D33"/><stop offset="1" stop-color="#1C1F24"/></linearGradient>
<linearGradient id="refl" x1="0" y1="0" x2="0" y2="1"><stop offset=".74" stop-color="#fff" stop-opacity=".35"/><stop offset=".92" stop-color="#fff" stop-opacity="0"/></linearGradient>
<mask id="rm"><rect x="0" y="0" width="1024" height="600" fill="url(#refl)"/></mask>
<linearGradient id="gZ" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5BE36C"/><stop offset="1" stop-color="#1FAE3B"/></linearGradient>
<linearGradient id="gM" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FF6A7E"/><stop offset="1" stop-color="#F0263F"/></linearGradient>
<linearGradient id="gN" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#6FD0FF"/><stop offset="1" stop-color="#2B7BFF"/></linearGradient>
<linearGradient id="gP" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#7CF08A"/><stop offset="1" stop-color="#29C24A"/></linearGradient>
<linearGradient id="art" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#F2B36A"/><stop offset="1" stop-color="#8A3FD1"/></linearGradient>
<clipPath id="mapc"><rect x="606" y="14" width="404" height="326" rx="22"/></clipPath>
<filter id="gl"><feGaussianBlur stdDeviation="3"/></filter>
<filter id="sh" x="-10%" y="-10%" width="120%" height="130%"><feDropShadow dx="0" dy="4" stdDeviation="6" flood-opacity=".45"/></filter>
</defs>
<rect width="1024" height="600" fill="#000"/>''']
# sidebar
S.append('<rect x="10" y="14" width="66" height="572" rx="20" fill="#1A1C20"/>')
S.append(t(43,52,17,'10:30','#fff',600,'middle'))
S.append(t(43,72,11,'Wi-Fi','#B8BCC4',600,'middle')+t(43,88,11,'GPS','#B8BCC4',600,'middle'))
def icon(y,grad,glyph):
    return f'<g filter="url(#sh)"><rect x="19" y="{y}" width="48" height="48" rx="12" fill="url(#{grad})"/></g>'+glyph
# ZLink: phone-link glyph
S.append(icon(150,'gZ',f'<g fill="none" stroke="#fff" stroke-width="2.6" stroke-linecap="round"><rect x="34" y="161" width="18" height="27" rx="4"/><path d="M39,183 h8"/></g>'))
S.append(t(43,214,10,'ZLink','#B8BCC4',500,'middle'))
S.append(icon(226,'gN','<path d="M31,262 L55,236 L49,265 L43,252 Z" fill="#fff"/>'))
S.append(t(43,290,10,'Mapas','#B8BCC4',500,'middle'))
S.append(icon(302,'gM','<g fill="#fff"><path d="M38,340 V316 L53,312 V334" stroke="#fff" stroke-width="2.6" fill="none"/><circle cx="35" cy="340" r="4.5"/><circle cx="50" cy="335" r="4.5"/></g>'))
S.append(t(43,366,10,'Música','#B8BCC4',500,'middle'))
S.append(icon(378,'gP','<path d="M33,390 h6 l3,7 -3.5,2.5 a14,14 0 0 0 7,7 l2.5,-3.5 7,3 v6 a2.5,2.5 0 0 1 -2.5,2.5 a22,22 0 0 1 -21,-21 a2.5,2.5 0 0 1 2.5,-2.5 z" fill="#fff"/>'))
S.append(t(43,442,10,'Teléfono','#B8BCC4',500,'middle'))
# app grid button
S.append('<g fill="#E9EBEE">'+''.join(f'<rect x="{31+i*12}" y="{538+j*12}" width="9" height="9" rx="2.5"/>' for i in range(2) for j in range(2))+'</g>')
# main car panel
S.append('<rect x="86" y="14" width="510" height="572" rx="24" fill="url(#panel)"/>')
S.append(t(112,104,66,'35','#fff',200)+t(194,104,17,'mph','#8E939B',500)+t(574,50,12,'GPS','#8E939B',600,'end')+'<circle cx="582" cy="46" r="3.5" fill="#34C759"/>')
# reflection
S.append('<defs><filter id="sb" x="-30%" y="-200%" width="160%" height="500%"><feGaussianBlur stdDeviation="9"/></filter><filter id="sc" x="-50%" y="-300%" width="200%" height="700%"><feGaussianBlur stdDeviation="3"/></filter></defs>')
S.append(f'<image href="data:image/png;base64,{car}" x="96" y="150" width="490" height="300" preserveAspectRatio="xMidYMax meet"/>')
# faint hud lines
S.append('')
S.append(t(341,556,12,'PRIUS','#5E636B',600,'middle'))
# nav card with map
S.append('<g clip-path="url(#mapc)"><rect x="606" y="14" width="404" height="326" fill="#17312A"/>')
random.seed(7)
for i in range(14):
    x=600+random.randint(0,420); S.append(f'<path d="M{x},10 C{x+random.randint(-80,80)},120 {x+random.randint(-80,80)},220 {x+random.randint(-60,60)},350" stroke="#2C4A42" stroke-width="{random.choice([2,3,5])}" fill="none"/>')
for i in range(8):
    y=20+i*45+random.randint(-10,10); S.append(f'<path d="M600,{y} C700,{y+random.randint(-30,30)} 850,{y+random.randint(-30,30)} 1015,{y+random.randint(-20,20)}" stroke="#2C4A42" stroke-width="3" fill="none"/>')
route='M970,330 C960,290 960,260 930,240 C880,205 790,230 780,190 C770,150 850,140 900,110 C940,85 950,60 1000,40'
S.append(f'<path d="{route}" stroke="{A}" stroke-width="16" stroke-opacity=".3" fill="none" stroke-linecap="round"/><path d="{route}" stroke="{A}" stroke-width="8" fill="none" stroke-linecap="round"/>')
S.append(t(860,170,11,'Peters Canyon','#9DB5AE',500,'start'))
S.append(f'<circle cx="955" cy="275" r="17" fill="#fff"/><circle cx="955" cy="275" r="13" fill="{A}"/><path d="M955,265 L962,283 L955,279 L948,283 Z" fill="#fff"/>')
S.append('</g>')
S.append('<rect x="618" y="26" width="128" height="32" rx="16" fill="#1E2126" fill-opacity=".85"/><path d="M636,49 L646,33 L644,45 L651,45 Z" fill="#3E9BFF"/>'+t(658,47,14,'Navegación','#fff',600))
S.append('<rect x="618" y="262" width="150" height="64" rx="14" fill="#1E2126" fill-opacity=".9"/>'+t(632,290,22,'12 min','#fff',700)+t(632,312,12,'al destino','#B8BCC4',500))
# music card
S.append('<rect x="606" y="352" width="200" height="234" rx="22" fill="url(#card)"/>')
S.append('<rect x="620" y="366" width="22" height="22" rx="6" fill="url(#gM)"/>'+t(650,383,14,'Música','#fff',600))
S.append('<rect x="620" y="402" width="64" height="64" rx="10" fill="url(#art)"/>'+t(694,428,17,'Chillin’','#fff',600)+t(694,448,13,'Spotify','#9AA0A8',500))
S.append('<g fill="#fff"><path d="M628,512 l12,-8 v16 z M642,512 l12,-8 v16 z"/><path d="M698,500 l18,12 -18,12 z"/><path d="M760,504 l12,8 -12,8 z M774,504 l12,8 -12,8 z"/></g>')
S.append('<rect x="620" y="552" width="172" height="4" rx="2" fill="#3A3E45"/><rect x="620" y="552" width="70" height="4" rx="2" fill="#fff"/>')
# weather card
S.append('<rect x="818" y="352" width="192" height="234" rx="22" fill="url(#card)"/>')
S.append('<defs><linearGradient id="gW" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5AB8FF"/><stop offset="1" stop-color="#2F7BEA"/></linearGradient></defs>')
S.append('<rect x="832" y="366" width="22" height="22" rx="6" fill="url(#gW)"/><circle cx="843" cy="377" r="5" fill="#FFD84A"/>'+t(862,383,14,'Clima','#fff',600))
S.append(t(832,420,12,'Irvine','#9AA0A8',500))
S.append(t(832,470,46,'72°','#fff',300))
S.append('<g><circle cx="968" cy="440" r="15" fill="#FFD84A"/>'+''.join(f'<line x1="{968+21*math.cos(math.radians(a))}" y1="{440+21*math.sin(math.radians(a))}" x2="{968+27*math.cos(math.radians(a))}" y2="{440+27*math.sin(math.radians(a))}" stroke="#FFD84A" stroke-width="3" stroke-linecap="round"/>' for a in range(0,360,45))+'</g>')
S.append(t(832,494,14,'Despejado','#fff',600)+t(832,513,12,'Máx 76°  Mín 58°','#9AA0A8',500))
S.append('<line x1="832" y1="528" x2="996" y2="528" stroke="#3A3E45"/>')
for i,(h,tmp) in enumerate([('4 PM','73°'),('5 PM','71°'),('6 PM','68°'),('7 PM','65°')]):
    x=846+i*44; S.append(t(x,548,10,h,'#9AA0A8',500,'middle')+f'<circle cx="{x}" cy="560" r="5" fill="#FFD84A"/>'+t(x,580,12,tmp,'#fff',600,'middle'))
S.append('</svg>')
open('design/scripts/carplay.svg','w').write(''.join(S))
from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    b=p.chromium.launch(executable_path='/usr/bin/google-chrome'); pg=b.new_page(viewport={'width':1024,'height':600},device_scale_factor=1.5)
    pg.goto('file://design/scripts/carplay.svg'); pg.wait_for_timeout(800); pg.screenshot(path='/home/claude/cp/carplay_noche.png'); b.close()
