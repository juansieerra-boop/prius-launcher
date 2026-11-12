# Render v4 — mover widgets: modo editar (mantener presionado y arrastrar) + ejemplo reacomodado
# Uso: python3 design/v4/render_v4_mover.py   (desde la raíz del repo)
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from render_v4 import TH, A, ICON, css, mapsvg, sun, swap_card, car, ROOT, showroom, theme_toggle, lights_img, CAR_NAME

# Espacios fijos de la pantalla (x, y, w, h). La barra de música queda fija arriba.
SLOTS = {'L': (10, 78, 286, 512), 'C': (306, 78, 455, 512), 'R1': (771, 78, 286, 248), 'R2': (771, 336, 286, 254)}
box = lambda b: f'left:{b[0]}px;top:{b[1]}px;width:{b[2]}px;height:{b[3]}px'

def w_map(t, b, extra=''):
    return f'''<div class="card" style="{box(b)};{extra}"><img style="position:absolute;inset:0" src="data:image/svg+xml;base64,{mapsvg(t, b[2], b[3])}">
 <div class="pill">{ICON["nav"]}{"Navegación" if b[3] > 300 else "Jamboree Rd · Irvine"}</div>
 {'<div class="loc"><b>Jamboree Rd</b><span>Irvine, CA · NE</span></div>' if b[3] > 300 else ''}
 <div class="go"><div><span class="d" style="background:#33CCFF"></span>Waze</div><div><span class="d" style="background:#34A853"></span>Google Maps</div></div></div>'''

def w_car(t, b, extra=''):
    iw = b[2] - 28; ih = iw * 454 / 909
    bgh, top = showroom(t, b[2], b[3], iw)
    return f'''<div class="card" style="{box(b)};{extra}">{bgh}<img style="position:absolute;left:14px;top:{top:.1f}px;width:{iw}px" src="data:image/png;base64,{car}">{lights_img(t, f"left:14px;top:{top:.1f}px;width:{iw}px")}
 <div style="position:absolute;left:0;right:0;bottom:{30 if b[3] > 300 else 16}px;text-align:center;font-size:12px;font-weight:600;letter-spacing:3px;color:{t["faint"]}">{CAR_NAME}</div></div>'''

def w_wx(t, b, extra=''):
    hours = [('4 PM', '73°'), ('5 PM', '71°'), ('6 PM', '68°'), ('7 PM', '65°')]
    hrs = ''.join(f'<div style="text-align:center;flex:1"><div style="font-size:10px;color:{t["faint"]};font-weight:500">{h}</div><div style="width:9px;height:9px;border-radius:50%;background:#FFD84A;margin:6px auto"></div><div style="font-size:12px;font-weight:600">{v}</div></div>' for h, v in hours)
    days = ''
    if b[3] > 300:  # versión alta: agrega los próximos días
        rows = [('Jueves', '75°', '57°'), ('Viernes', '78°', '59°'), ('Sábado', '74°', '56°'), ('Domingo', '70°', '55°'), ('Lunes', '69°', '54°')]
        days = f'<div style="position:absolute;left:18px;right:18px;top:240px;height:1px;background:{t["line"]}"></div>' + \
            f'<div style="position:absolute;left:18px;top:256px;font-size:12px;color:{t["faint"]};font-weight:600">Próximos días</div>' + \
            ''.join(f'<div style="position:absolute;left:18px;right:18px;top:{284+i*42}px;display:flex;align-items:center;font-size:13px;font-weight:600"><span style="flex:1">{d}</span><span style="width:9px;height:9px;border-radius:50%;background:#FFD84A;margin-right:20px"></span><span style="width:40px;text-align:right">{hi}</span><span style="width:40px;text-align:right;color:{t["faint"]}">{lo}</span></div>' for i, (d, hi, lo) in enumerate(rows))
    return f'''<div class="card" style="{box(b)};{extra}"><div class="hd"><span>Clima</span></div>{sun(30, 30)}
 <div style="position:absolute;left:18px;top:46px;font-size:12px;color:{t["faint"]};font-weight:500">Irvine</div>
 <div style="position:absolute;left:16px;top:60px;font-size:52px;font-weight:300">72°</div>
 <div style="position:absolute;left:18px;top:126px;font-size:14px;font-weight:600">Despejado <span style="color:{t["faint"]};font-weight:500;font-size:12px;margin-left:6px">Máx 76° · Mín 58°</span></div>
 <div style="position:absolute;left:18px;right:18px;top:156px;height:1px;background:{t["line"]}"></div>
 <div style="position:absolute;left:12px;right:12px;top:168px;display:flex">{hrs}</div>{days}</div>'''

def w_swap(t, b, extra=''):
    return swap_card('vel', t, f'{box(b)};{extra}')

W = {'map': w_map, 'car': w_car, 'wx': w_wx, 'swap': w_swap}

def bar(t, editing=False):
    if editing:
        return f'''<div class="card bar"><div style="position:absolute;left:20px;top:19px;font-size:16px;font-weight:600">Editar pantalla</div>
 <div style="position:absolute;right:122px;top:11px;height:36px;padding:0 16px;border-radius:12px;background:{t["btn"]};display:flex;align-items:center;font-size:13px;font-weight:600">Restablecer</div>
 <div style="position:absolute;right:12px;top:11px;height:36px;width:100px;border-radius:12px;background:{A};color:#fff;display:flex;align-items:center;justify-content:center;font-size:14px;font-weight:600">Listo</div></div>'''
    return f'''<div class="card bar"><span class="apps">{ICON["apps"]}</span><span class="sep"></span><div class="art"></div>
 <div class="ti">Chillin’</div><div class="ar">Spotify · Lofi Beats</div>
 <div class="ctl">{ICON["prev"]}{ICON["play"]}{ICON["next"]}</div>
 <div class="pr"><b></b></div><div class="tm"><span>1:12</span><span>-1:48</span></div>
 {theme_toggle(t)}<div class="st"><b>10:30</b><small>WI-FI · GPS<i></i></small></div></div>'''

EXTRA_CSS = f'''.ed{{outline:2px dashed {A}88;outline-offset:3px}}
.hole{{position:absolute;border-radius:22px;border:2px dashed {A};background:{A}14;display:flex;align-items:center;justify-content:center;color:{A};font-size:13px;font-weight:600}}
.lift{{box-shadow:0 18px 40px rgba(0,0,0,.28);transform:rotate(-1.5deg) scale(1.03);z-index:5}}
.grip{{position:absolute;left:50%;top:6px;width:36px;height:5px;margin-left:-18px;border-radius:3px;background:{A}}}
.dim{{opacity:.55}}'''

def page(t, layout, editing=False):
    cards = ''
    for slot, wid in layout.items():
        b = SLOTS[slot]
        if editing and wid == 'wx':  # el clima va siendo arrastrado hacia el espacio izquierdo
            cards += f'<div class="hole" style="{box(b)}"></div>'
            continue
        html = W[wid](t, b, '').replace('class="card"', f'class="card{" ed" if editing else ""}{" dim" if editing and slot == "L" else ""}"', 1)
        if editing and wid == 'car':  # botón para cambiar la foto del carro
            html = html[:-6] + f'<div style="position:absolute;right:14px;top:14px;height:34px;padding:0 14px;border-radius:12px;background:{t["glass"]};display:flex;align-items:center;gap:8px;font-size:13px;font-weight:600"><svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="1.5" y="3.5" width="13" height="10" rx="2"/><circle cx="8" cy="8.5" r="2.5"/><path d="M5.5 3.5l1-2h3l1 2"/></svg>Imagen</div></div>'
        cards += html
    if editing:
        bl = (120, 150, 286, 248)
        cards += W['wx'](t, bl, '').replace('class="card"', 'class="card lift"', 1).replace('</div>', '</div><div class="grip"></div>', 1)
    return f'<html><head><meta charset="utf-8"><style>{css(t)}{EXTRA_CSS}</style></head><body>{bar(t, editing)}{cards}</body></html>'

if __name__ == '__main__':
    from playwright.sync_api import sync_playwright
    base = {'L': 'map', 'C': 'car', 'R1': 'wx', 'R2': 'swap'}
    swapped = {'L': 'wx', 'C': 'car', 'R1': 'map', 'R2': 'swap'}
    with sync_playwright() as p:
        b = p.chromium.launch(executable_path='/usr/bin/google-chrome')
        for name, lay, ed in [('v4_mover_editando', base, True), ('v4_mover_resultado', swapped, False)]:
            pg = b.new_page(viewport={'width': 1920, 'height': 1080}, device_scale_factor=1)
            pg.set_content(page(TH['dia'], lay, ed)); pg.wait_for_timeout(400); pg.screenshot(path=f'{ROOT}/{name}.png'); pg.close()
        b.close()
    print('ok')
