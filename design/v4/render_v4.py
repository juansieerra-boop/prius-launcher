# Render v4 (CarGO): barra de música arriba, mapa | carro | clima + sección intercambiable
# Uso: python3 design/v4/render_v4.py   (desde la raíz del repo)
import base64, math, random, os
ROOT = os.path.dirname(os.path.abspath(__file__))
car = base64.b64encode(open(os.path.join(ROOT, '..', 'assets', 'carro_plateado_polarizado.png'), 'rb').read()).decode()
lights = base64.b64encode(open(os.path.join(ROOT, '..', 'assets', 'luces_encendidas.png'), 'rb').read()).decode()
CAR_NAME = 'PRIUS'   # editable en Ajustes (para cada cliente)
LIGHTS = 'auto'   # 'auto' (5 PM–6 AM = modo noche) · 'siempre' · 'apagadas'
def lights_on(t): return LIGHTS == 'siempre' or (LIGHTS == 'auto' and t['night'])
def lights_img(t, style): return f'<img style="position:absolute;{style};mix-blend-mode:plus-lighter" src="data:image/png;base64,{lights}">' if lights_on(t) else ''

TH = {
 'dia':   dict(night=False, bg='#E6E7EA', card='#FAFAFB', card2='#F3F4F6', tx='#1C1D20', sub='#6B6F76', faint='#9AA0A8',
              line='#E2E4E8', map='#E4EEE6', road='#FFFFFF', mapTx='#6B7F75', btn='#F0F1F4', glass='rgba(255,255,255,.92)'),
 'noche': dict(night=True, bg='#0A0A0C', card='#17181B', card2='#121316', tx='#E4E5E8', sub='#8C9199', faint='#62666D',
              line='#2C2E33', map='#17261F', road='#2C4A3C', mapTx='#7FA391', btn='#2A2C31', glass='rgba(28,29,33,.92)'),
}
A = '#3E9BFF'

def mapsvg(t, w, h):
    random.seed(7); s = [f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" xmlns="http://www.w3.org/2000/svg"><rect width="{w}" height="{h}" fill="{t["map"]}"/>']
    for i in range(10):
        x = random.randint(0, w); s.append(f'<path d="M{x},-10 C{x+random.randint(-70,70)},{h*.35} {x+random.randint(-70,70)},{h*.65} {x+random.randint(-50,50)},{h+10}" stroke="{t["road"]}" stroke-width="{random.choice([2,3,5])}" fill="none"/>')
    for i in range(7):
        y = 20+i*(h/7)+random.randint(-8,8); s.append(f'<path d="M-10,{y} C{w*.3},{y+random.randint(-25,25)} {w*.7},{y+random.randint(-25,25)} {w+10},{y+random.randint(-15,15)}" stroke="{t["road"]}" stroke-width="3" fill="none"/>')
    # avenida principal donde va el carro
    s.append(f'<path d="M{w*.62},{h+10} C{w*.58},{h*.7} {w*.48},{h*.5} {w*.45},-10" stroke="{t["road"]}" stroke-width="9" fill="none"/>')
    s.append(f'<text x="{w*.58}" y="{h*.30}" font-size="11" fill="{t["mapTx"]}" font-family="Inter" font-weight="500">Jamboree Rd</text>')
    cx, cy = w*.535, h*.56
    s.append(f'<circle cx="{cx}" cy="{cy}" r="34" fill="{A}" fill-opacity=".14"/><circle cx="{cx}" cy="{cy}" r="16" fill="#fff"/><circle cx="{cx}" cy="{cy}" r="12" fill="{A}"/><path d="M{cx},{cy-8} L{cx+6},{cy+7} L{cx},{cy+4} L{cx-6},{cy+7} Z" fill="#fff"/>')
    s.append('</svg>'); return base64.b64encode(''.join(s).encode()).decode()

ICON = {
 'apps': '<svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor"><rect x="2" y="2" width="7" height="7" rx="2"/><rect x="11" y="2" width="7" height="7" rx="2"/><rect x="2" y="11" width="7" height="7" rx="2"/><rect x="11" y="11" width="7" height="7" rx="2"/></svg>',
 'prev': '<svg width="22" height="16" viewBox="0 0 22 16" fill="currentColor"><path d="M11 8 21 1v14zM1 8l10-7v14z"/></svg>',
 'play': '<svg width="18" height="20" viewBox="0 0 18 20" fill="currentColor"><path d="M2 1l15 9-15 9z"/></svg>',
 'next': '<svg width="22" height="16" viewBox="0 0 22 16" fill="currentColor"><path d="M11 8 1 1v14zM21 8 11 1v14z"/></svg>',
 'swap': '<svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 5h10l-3-3M13 11H3l3 3"/></svg>',
 'nav':  '<svg width="14" height="14" viewBox="0 0 14 14"><path d="M1 13 7 1l6 12-6-3z" fill="#3E9BFF"/></svg>',
}

def sun(cx, cy, r=15):
    rays = ''.join(f'<line x1="{cx+(r+6)*math.cos(math.radians(a)):.1f}" y1="{cy+(r+6)*math.sin(math.radians(a)):.1f}" x2="{cx+(r+12)*math.cos(math.radians(a)):.1f}" y2="{cy+(r+12)*math.sin(math.radians(a)):.1f}" stroke="#FFD84A" stroke-width="3" stroke-linecap="round"/>' for a in range(0, 360, 45))
    return f'<svg width="{2*cx}" height="{2*cy}" style="position:absolute;right:14px;top:44px"><circle cx="{cx}" cy="{cy}" r="{r}" fill="#FFD84A"/>{rays}</svg>'

def swap_body(kind, t):
    if kind == 'vel':
        ang = 225 - 270*(35/120)  # aguja 35 mph de 0-120
        arc = lambda a0, a1, col, sw: (lambda x0,y0,x1,y1,lg: f'<path d="M{x0:.1f},{y0:.1f} A76,76 0 {lg} 1 {x1:.1f},{y1:.1f}" stroke="{col}" stroke-width="{sw}" fill="none" stroke-linecap="round"/>')(
            130+76*math.cos(math.radians(a0)), 100-76*math.sin(math.radians(a0)), 130+76*math.cos(math.radians(a1)), 100-76*math.sin(math.radians(a1)), 1 if (a0-a1) > 180 else 0)
        return f'''<svg width="260" height="190" style="position:absolute;left:10px;top:40px">{arc(225,-45,t["line"],6)}{arc(225,ang,A,6)}
          <text x="130" y="112" text-anchor="middle" font-family="Inter" font-weight="200" font-size="58" fill="{t["tx"]}">35</text>
          <text x="130" y="136" text-anchor="middle" font-family="Inter" font-weight="500" font-size="13" fill="{t["sub"]}">mph</text>
          <text x="130" y="182" text-anchor="middle" font-family="Inter" font-weight="500" font-size="11" fill="{t["faint"]}">GPS</text></svg>'''
    if kind == 'apps':
        apps = [('ZLink', '#29C24A'), ('Waze', '#33CCFF'), ('Spotify', '#1DB954'), ('Teléfono', '#29C24A'), ('YouTube', '#FF3B30'), ('Ajustes', '#8E8E93')]
        cells = ''.join(f'<div class="ap"><div class="ai" style="background:{c}">{n[0]}</div><span>{n}</span></div>' for n, c in apps)
        return f'<div class="grid">{cells}</div>'
    if kind == 'reloj':
        return f'''<div style="position:absolute;left:18px;top:56px"><div style="font-size:58px;font-weight:200;letter-spacing:-1px">10:30</div>
          <div style="color:{t["sub"]};font-size:14px;font-weight:500;margin-top:2px">Miércoles, 11 de noviembre</div></div>
          <svg width="80" height="80" style="position:absolute;right:16px;bottom:18px"><circle cx="40" cy="40" r="34" fill="none" stroke="{t["line"]}" stroke-width="2"/>
          <g transform="rotate(42 40 40)"><path d="M40 12 47 40 40 36 33 40z" fill="#FF3B30"/><path d="M40 68 47 40 40 44 33 40z" fill="{t["faint"]}"/></g>
          <text x="40" y="9" text-anchor="middle" font-family="Inter" font-size="9" font-weight="700" fill="{t["sub"]}">N</text></svg>
          <div style="position:absolute;left:18px;bottom:26px;font-size:22px;font-weight:600">NE <span style="color:{t["sub"]};font-size:14px;font-weight:500">· 42°</span></div>'''
    if kind == 'idioma':
        return f'''<div style="position:absolute;left:18px;right:18px;top:62px;display:flex;flex-direction:column;gap:10px">
          <div class="opt on"><span>Español</span><b>✓</b></div><div class="opt"><span>English</span></div></div>
'''

TITLES = {'vel': 'Velocidad', 'apps': 'Apps', 'reloj': 'Reloj y brújula', 'idioma': 'Idioma'}
ORDER = ['vel', 'apps', 'reloj', 'idioma']

def swap_card(kind, t, style=''):
    dots = ''.join(f'<i class="{"on" if k == kind else ""}"></i>' for k in ORDER)
    return f'''<div class="card swap" style="{style}"><div class="hd"><span>{TITLES[kind]}</span><span class="sw">{ICON["swap"]}</span></div>
      {swap_body(kind, t)}<div class="dots">{dots}</div></div>'''

def _b64(path):
    return base64.b64encode(open(path, 'rb').read()).decode()
BG = {k: _b64(os.path.join(ROOT, 'fondos', f'estudio_{k}.jpg')) for k in ('dia', 'noche')}
BG_W, BG_H = 1933, 1080   # fondos v2 de Juan Diego (1024x572 agrandados a 1080 de alto)
BG_FLOOR = {'dia': 350 * 1080 / 572, 'noche': 363 * 1080 / 572}   # línea pared/piso medida en cada foto

# Medidas del carro en su dibujo (Prius 909x454). Otro carro (Jeep) cambia este diccionario.
GEOM = dict(w=909, h=454, fr=(444, 425), rr=(844, 321), lat=(-270 * .85, -40 * .85), rl_k=.72, fr_w=40, rr_w=25, fl_w=0)
STAGE = None   # colores del fondo del carro: None = gris neutro (Prius); dict(dia=(...), noche=(...)) para otra paleta

BG_STYLE = 'minimal'   # 'minimal' = fondo liso (actual) · 'foto' = fotos de estudio de Juan Diego

def backdrop_min(w, h, floor, night):
    """Fondo minimalista: pared y piso lisos, sin línea dura entre ellos, y una luz suave detrás del carro."""
    if STAGE:
        wall0, wall1, fl0, fl1, spot, sop = STAGE['noche' if night else 'dia']
    elif night:
        wall0, wall1, fl0, fl1, spot, sop = '#121317', '#1A1C21', '#16171B', '#0C0D10', '#3A3F48', .55
    else:
        wall0, wall1, fl0, fl1, spot, sop = '#ECEDEF', '#E2E4E7', '#DCDEE1', '#CFD2D6', '#FFFFFF', .85
    return (f'<defs><linearGradient id="mw" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{wall0}"/><stop offset="1" stop-color="{wall1}"/></linearGradient>'
            f'<linearGradient id="mf" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{fl0}"/><stop offset="1" stop-color="{fl1}"/></linearGradient>'
            f'<linearGradient id="mc" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{wall1}" stop-opacity="0"/><stop offset=".5" stop-color="{fl0}"/><stop offset="1" stop-color="{fl0}" stop-opacity="0"/></linearGradient>'
            f'<radialGradient id="ms" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="{spot}" stop-opacity="{sop}"/><stop offset="1" stop-color="{spot}" stop-opacity="0"/></radialGradient></defs>'
            f'<rect width="{w}" height="{h}" fill="url(#mw)"/>'
            f'<rect y="{floor:.1f}" width="{w}" height="{h-floor:.1f}" fill="url(#mf)"/>'
            f'<rect y="{floor-45:.1f}" width="{w}" height="90" fill="url(#mc)"/>'
            f'<ellipse cx="{w/2}" cy="{floor-20:.1f}" rx="{w*.62:.1f}" ry="{h*.36:.1f}" fill="url(#ms)"/>')

def shadow_svg(ox, ct, sc, night):
    """Sombra suave bajo el carro: la huella de las 4 llantas en el piso, difuminada, más el contacto de cada llanta visible."""
    P = lambda x, y: (ox + x * sc, ct + y * sc)
    k = sc * GEOM['w'] / 909 / 0.47   # 1.0 con el carro al ancho del render
    FR, RR = GEOM['fr'], GEOM['rr']                  # llantas visibles (medidas en el dibujo del carro)
    lat = GEOM['lat']                                # ancho del carro en pantalla (dirección faro der. → faro izq.)
    FL = (FR[0] + lat[0], FR[1] + lat[1]); RL = (RR[0] + lat[0] * GEOM['rl_k'], RR[1] + lat[1] * GEOM['rl_k'])
    quad = [P(*FR), P(*RR), P(*RL), P(*FL)]
    cx = sum(p[0] for p in quad) / 4; cy = sum(p[1] for p in quad) / 4
    shrink = lambda f: ' '.join(f'{cx + (x - cx) * f:.1f},{cy + (y - cy) * f:.1f}' for x, y in quad)
    o1, o2, o3 = (.70, .60, .85) if night else (.50, .45, .62)
    out = (f'<defs><filter id="sa" x="-30%" y="-60%" width="160%" height="220%"><feGaussianBlur stdDeviation="{9*k:.1f}"/></filter>'
           f'<filter id="sb2" x="-30%" y="-60%" width="160%" height="220%"><feGaussianBlur stdDeviation="{4*k:.1f}"/></filter>'
           f'<filter id="sc2" x="-50%" y="-300%" width="200%" height="700%"><feGaussianBlur stdDeviation="{2*k:.1f}"/></filter></defs>')
    out += f'<polygon points="{shrink(1.15)}" fill="#000" opacity="{o1}" filter="url(#sa)"/>'
    out += f'<polygon points="{shrink(.80)}" fill="#000" opacity="{o2}" filter="url(#sb2)"/>'
    ang = math.degrees(math.atan2(RR[1] - FR[1], RR[0] - FR[0]))
    tires = [(P(*FR), GEOM['fr_w']), (P(*RR), GEOM['rr_w'])] + ([(P(*FL), GEOM['fl_w'])] if GEOM['fl_w'] else [])
    for (x, y), w in tires:
        out += f'<ellipse cx="{x:.1f}" cy="{y:.1f}" rx="{w*sc:.1f}" ry="{4*k:.1f}" transform="rotate({ang:.2f} {x:.1f} {y:.1f})" fill="#000" opacity="{o3}" filter="url(#sc2)"/>'
    return out

def showroom(t, w, h, cw):
    """Fondo de estudio (fotos de Juan Diego) + reflejo del carro. Devuelve (html, top_del_carro)."""
    night = t['night']
    sc = cw / GEOM['w']; ch = GEOM['h'] * sc
    s = max(w / BG_W, h / BG_H); bw, bh = BG_W * s, BG_H * s
    bx, by = (w - bw) / 2, (h - bh) / 2
    floor = (h * .60) if BG_STYLE == 'minimal' else by + BG_FLOOR['noche' if night else 'dia'] * s
    # el carro va centrado, pero la llanta trasera siempre queda sobre el piso (nunca "parada" en la pared)
    ct = max((h - ch) / 2 - 10, floor - GEOM['rr'][1] * sc + 12)
    ox = (w - cw) / 2
    x0, y0 = ox + GEOM['fr'][0] * sc, ct + GEOM['fr'][1] * sc; x1, y1 = ox + GEOM['rr'][0] * sc, ct + GEOM['rr'][1] * sc
    m = (y1 - y0) / (x1 - x0)
    k = 'noche' if night else 'dia'
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><defs>'
           f'<radialGradient id="gw" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="{"#4A4F58" if night else "#FFFFFF"}" stop-opacity="{.35 if night else .55}"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>'
           f'<linearGradient id="ceil" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#000" stop-opacity="{.62 if night else .14}"/><stop offset=".8" stop-color="#000" stop-opacity="{.50 if night else .10}"/><stop offset="1" stop-color="#000" stop-opacity="0"/></linearGradient>'
           f'<filter id="b3"><feGaussianBlur stdDeviation="2.5"/></filter><filter id="b14" x="-50%" y="-200%" width="200%" height="500%"><feGaussianBlur stdDeviation="14"/></filter>'
           f'<linearGradient id="rf" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity="{.40 if night else .38}"/><stop offset=".40" stop-color="#fff" stop-opacity="0"/></linearGradient>'
           f'<mask id="rm"><rect x="0" y="{y1}" width="{w}" height="{h-y1}" fill="url(#rf)"/></mask></defs>'
           + (backdrop_min(w, h, floor, night) if BG_STYLE == 'minimal' else
              f'<image href="data:image/jpeg;base64,{BG[k]}" x="{bx:.1f}" y="{by:.1f}" width="{bw:.1f}" height="{bh:.1f}" preserveAspectRatio="none"/>'
              f'<rect width="{w}" height="{by+300*s:.1f}" fill="url(#ceil)"/>') +
           f'<ellipse cx="{w/2}" cy="{ct+0.374*ch:.1f}" rx="{cw*.58:.1f}" ry="{0.33*ch:.1f}" fill="url(#gw)"/>'
           f'<ellipse cx="{w/2}" cy="{y0+6:.1f}" rx="{cw*.5:.1f}" ry="18" fill="{"#3A3E46" if night else "#FFFFFF"}" opacity="{.40 if night else .5}" filter="url(#b14)"/>'
           # sombra suave de contacto: debajo de cada llanta + una sombra difusa del piso del carro entre ellas
           + shadow_svg(ox, ct, sc, night) +
           ('' if BG_STYLE == 'minimal' else f'<g mask="url(#rm)"><image href="data:image/png;base64,{car}" x="{ox:.1f}" y="{ct:.1f}" width="{cw:.1f}" height="{ch:.1f}" transform="matrix(1,{2*m:.5f},0,-1,0,{2*(y0-m*x0):.2f})" filter="url(#b3)"/></g>') + '</svg>')
    html = f'<img style="position:absolute;inset:0;width:{w}px;height:{h}px" src="data:image/svg+xml;base64,{base64.b64encode(svg.encode()).decode()}">'
    return html, ct

def theme_toggle(t):
    night = t['night']
    sun_i = '<svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><circle cx="8" cy="8" r="3"/><path d="M8 1v1.6M8 13.4V15M1 8h1.6M13.4 8H15M3 3l1.1 1.1M11.9 11.9 13 13M3 13l1.1-1.1M11.9 4.1 13 3"/></svg>'
    moon_i = '<svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><path d="M13.5 10.2A6 6 0 0 1 5.8 2.5a6 6 0 1 0 7.7 7.7z"/></svg>'
    gear = '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/></svg>'
    return f'<div class="gear">{gear}</div>'   # el tema claro/oscuro se cambia en Ajustes

MAP_APP = None   # None = botones Waze y Google Maps · ('Waze', '#33CCFF') = la app elegida en Ajustes
NAV = None   # None = sin navegación · dict(app, color, turn, dist, street, eta, left) = navegando con la app elegida

def map_card(t):
    m = f'<div class="card map"><img src="data:image/svg+xml;base64,{mapsvg(t, 286, 512)}">'
    go = (f'<div class="go"><div><span class="d" style="background:{MAP_APP[1]}"></span>Abrir {MAP_APP[0]}</div></div>' if MAP_APP else
          '<div class="go"><div><span class="d" style="background:#33CCFF"></span>Waze</div><div><span class="d" style="background:#34A853"></span>Google Maps</div></div>')
    if not NAV:
        return m + f'''<div class="pill">{ICON["nav"]}Navegación</div>
 <div class="loc"><b>Jamboree Rd</b><span>Irvine, CA · NE</span></div>
 {go}</div>'''
    n = NAV
    turn = f'<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M7 21V11a4 4 0 0 1 4-4h8"/><path d="M15 3l4 4-4 4"/></svg>'
    return m + f'''<div style="position:absolute;left:12px;right:12px;top:12px;background:{t["glass"]};border-radius:16px;padding:12px;display:flex;gap:12px;align-items:center">
  <div style="width:48px;height:48px;border-radius:12px;background:{A};display:flex;align-items:center;justify-content:center;flex:none">{turn}</div>
  <div><div style="font-size:20px;font-weight:700">{n["dist"]}</div><div style="font-size:13px;font-weight:600;color:{t["sub"]};margin-top:2px">{n["turn"]}</div></div></div>
 <div class="loc"><b>{n["street"]}</b><span>{n["left"]} · Llegada {n["eta"]}</span></div>
 <div class="go"><div><span class="d" style="background:{n["color"]}"></span>Abrir {n["app"]}</div></div></div>'''

def css(t):
    return f'''html{{zoom:1.8}} *{{margin:0;padding:0;box-sizing:border-box}}
body{{width:1067px;height:600px;background:{t["bg"]};font-family:Inter,sans-serif;color:{t["tx"]};overflow:hidden;position:relative}}
.card{{position:absolute;background:linear-gradient(135deg,{t["card"]},{t["card2"]});border-radius:22px;overflow:hidden}}
.hd{{position:absolute;left:18px;right:16px;top:16px;display:flex;justify-content:space-between;align-items:center;font-size:14px;font-weight:600}}
.sw{{color:{t["faint"]};display:flex}}
.bar{{left:10px;top:10px;width:1047px;height:58px;border-radius:18px}}
.bar .art{{position:absolute;left:58px;top:9px;width:40px;height:40px;border-radius:9px;background:linear-gradient(135deg,#F2B36A,#8A3FD1)}}
.bar .apps{{position:absolute;left:18px;top:19px;color:{t["sub"]}}}
.bar .sep{{position:absolute;left:44px;top:14px;width:1px;height:30px;background:{t["line"]}}}
.bar .ti{{position:absolute;left:110px;top:12px;font-size:15px;font-weight:600}}
.bar .ar{{position:absolute;left:110px;top:32px;font-size:12px;font-weight:500;color:{t["sub"]}}}
.bar .ctl{{position:absolute;left:453px;top:17px;display:flex;gap:38px;align-items:center;color:{t["tx"]}}}
.bar .pr{{position:absolute;left:611px;top:28px;width:290px;height:3px;border-radius:2px;background:{t["line"]}}}
.bar .pr b{{display:block;width:40%;height:3px;border-radius:2px;background:{t["tx"]}}}
.bar .tm{{position:absolute;left:611px;top:36px;width:290px;display:flex;justify-content:space-between;font-size:10px;color:{t["faint"]};font-weight:500}}
.bar .st{{position:absolute;right:18px;top:13px;text-align:right}}
.bar .st b{{display:block;font-size:17px;font-weight:600}}
.bar .st small{{font-size:10px;color:{t["sub"]};font-weight:600;letter-spacing:.3px}}
.bar .st small i{{display:inline-block;width:6px;height:6px;border-radius:50%;background:#34C759;margin-left:4px;vertical-align:1px}}
.map{{left:10px;top:78px;width:286px;height:512px}}
.map img{{position:absolute;inset:0}}
.pill{{position:absolute;left:12px;top:12px;background:{t["glass"]};border-radius:16px;padding:7px 12px;display:flex;gap:8px;align-items:center;font-size:13px;font-weight:600}}
.loc{{position:absolute;left:12px;right:12px;bottom:66px;background:{t["glass"]};border-radius:14px;padding:11px 14px}}
.loc b{{font-size:16px;font-weight:700;display:block}} .loc span{{font-size:12px;color:{t["sub"]};font-weight:500}}
.go{{position:absolute;left:12px;right:12px;bottom:12px;display:flex;gap:8px}}
.go div{{flex:1;height:44px;border-radius:13px;display:flex;align-items:center;justify-content:center;gap:8px;font-size:13px;font-weight:600;background:{t["glass"]}}}
.go .d{{width:10px;height:10px;border-radius:50%}}
.carp{{left:306px;top:78px;width:455px;height:512px}}
.carp img.car{{position:absolute;left:14px;width:427px}}
.carp .nm{{position:absolute;left:0;right:0;bottom:30px;text-align:center;font-size:12px;font-weight:600;letter-spacing:3px;color:{t["faint"]}}}
.wx{{left:771px;top:78px;width:286px;height:248px}}
.swap{{left:771px;top:336px;width:286px;height:254px}}
.dots{{position:absolute;left:0;right:0;bottom:10px;display:flex;gap:5px;justify-content:center}}
.dots i{{width:5px;height:5px;border-radius:50%;background:{t["line"]}}} .dots i.on{{background:{t["sub"]}}}
.grid{{position:absolute;left:14px;right:14px;top:52px;display:grid;grid-template-columns:repeat(3,1fr);gap:12px 6px}}
.ap{{display:flex;flex-direction:column;align-items:center;gap:5px;font-size:10px;color:{t["sub"]};font-weight:500}}
.ai{{width:46px;height:46px;border-radius:12px;color:#fff;font-weight:700;font-size:18px;display:flex;align-items:center;justify-content:center}}
.opt{{height:48px;border-radius:13px;background:{t["btn"]};display:flex;justify-content:space-between;align-items:center;padding:0 16px;font-size:15px;font-weight:600}}
.opt.on{{outline:2px solid {A}}} .opt b{{color:{A}}}
.ttl{{font-size:13px;font-weight:600}}
.gear{{position:absolute;left:927px;top:13px;width:32px;height:32px;border-radius:16px;background:{t["btn"]};display:flex;align-items:center;justify-content:center;color:{t["sub"]}}}
.th{{position:absolute;left:871px;top:13px;height:32px;padding:3px;border-radius:16px;background:{t["btn"]};display:flex;gap:2px}}
.th span{{width:38px;height:26px;border-radius:13px;display:flex;align-items:center;justify-content:center;color:{t["faint"]}}}
.th span.on{{background:{t["card"]};color:{t["tx"]};box-shadow:0 1px 3px rgba(0,0,0,.18)}}'''

def home(t, kind='vel'):
    SR = showroom(t, 455, 512, 427)
    hours = [('4 PM', '73°'), ('5 PM', '71°'), ('6 PM', '68°'), ('7 PM', '65°')]
    hrs = ''.join(f'<div style="text-align:center;flex:1"><div style="font-size:10px;color:{t["faint"]};font-weight:500">{h}</div><div style="width:9px;height:9px;border-radius:50%;background:#FFD84A;margin:6px auto"></div><div style="font-size:12px;font-weight:600">{v}</div></div>' for h, v in hours)
    return f'''<html><head><meta charset="utf-8"><style>{css(t)}</style></head><body>
<div class="card bar"><span class="apps">{ICON["apps"]}</span><span class="sep"></span><div class="art"></div>
 <div class="ti">Chillin’</div><div class="ar">Spotify · Lofi Beats</div>
 <div class="ctl">{ICON["prev"]}{ICON["play"]}{ICON["next"]}</div>
 <div class="pr"><b></b></div><div class="tm"><span>1:12</span><span>-1:48</span></div>
 {theme_toggle(t)}<div class="st"><b>10:30</b><small>WI-FI · GPS<i></i></small></div></div>
{map_card(t)}
<div class="card carp">{SR[0]}<img class="car" style="top:{SR[1]:.1f}px" src="data:image/png;base64,{car}">{lights_img(t, f"left:14px;top:{SR[1]:.1f}px;width:427px")}<div class="nm">{CAR_NAME}</div></div>
<div class="card wx"><div class="hd"><span>Clima</span></div>{sun(30, 30)}
 <div style="position:absolute;left:18px;top:46px;font-size:12px;color:{t["faint"]};font-weight:500">Irvine</div>
 <div style="position:absolute;left:16px;top:60px;font-size:52px;font-weight:300">72°</div>
 <div style="position:absolute;left:18px;top:126px;font-size:14px;font-weight:600">Despejado <span style="color:{t["faint"]};font-weight:500;font-size:12px;margin-left:6px">Máx 76° · Mín 58°</span></div>
 <div style="position:absolute;left:18px;right:18px;top:156px;height:1px;background:{t["line"]}"></div>
 <div style="position:absolute;left:12px;right:12px;top:168px;display:flex">{hrs}</div></div>
{swap_card(kind, t)}
</body></html>'''

def variants(t):
    cards = ''.join(swap_card(k, t, f'left:{10+i*252}px;top:46px;width:240px') for i, k in enumerate(ORDER))
    return f'''<html><head><meta charset="utf-8"><style>{css(t)}
.swap{{height:254px}} .grid{{gap:10px 4px}} .ai{{width:40px;height:40px}}</style></head><body style="height:320px">
<div style="position:absolute;left:14px;top:14px;font-size:15px;font-weight:600">Widgets</div>
{cards}</body></html>'''

if __name__ == '__main__':
    from playwright.sync_api import sync_playwright
    out = ROOT
    with sync_playwright() as p:
        b = p.chromium.launch(executable_path='/usr/bin/google-chrome')
        for name, th in TH.items():
            pg = b.new_page(viewport={'width': 1920, 'height': 1080}, device_scale_factor=1)
            pg.set_content(home(th)); pg.wait_for_timeout(400); pg.screenshot(path=f'{out}/v4_{name}.png'); pg.close()
        pg = b.new_page(viewport={'width': 1920, 'height': 576}, device_scale_factor=1)
        pg.set_content(variants(TH['dia'])); pg.wait_for_timeout(400); pg.screenshot(path=f'{out}/v4_seccion_opciones.png'); pg.close()
        b.close()
    print('ok')
