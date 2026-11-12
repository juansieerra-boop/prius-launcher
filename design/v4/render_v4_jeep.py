# Render v4 para el Jeep Wrangler de la esposa (CARO): mismo diseño del Prius con su foto.
# Uso: python3 design/v4/render_v4_jeep.py   (desde la raíz del repo)
import os, sys, base64
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_v4 as r
A = os.path.join(r.ROOT, '..')
r.car = base64.b64encode(open(os.path.join(A, 'originales', 'jeep_caro_blanco.png'), 'rb').read()).decode()
r.lights = base64.b64encode(open(os.path.join(A, 'assets', 'jeep_caro_luces.png'), 'rb').read()).decode()
r.CAR_NAME = 'WRANGLER'
NAV0 = r.ICON['nav']
# Medidas del dibujo 2087x1219 (mira a la derecha): llanta delantera cercana, trasera cercana y delantera lejana
NF, NR, FF = (1085, 1217), (199, 1007), (1823, 1091)
r.GEOM = dict(w=2087, h=1219, fr=NF, rr=NR, lat=(FF[0] - NF[0], FF[1] - NF[1]), rl_k=.72, fr_w=150, rr_w=100, fl_w=110)

# Paleta oro rosa y cereza (la de su versión anterior), sin blanco ni negro puro
ROSE = {
 'dia':   dict(night=False, bg='#EFE5E2', card='#FCF8F7', card2='#F6EEEC', tx='#2A1C20', sub='#7A646A', faint='#A8949A',
              line='#EADBD8', map='#F1E7E4', road='#FFFFFF', mapTx='#8A7378', btn='#F3E8E5', glass='rgba(255,250,249,.92)'),
 'noche': dict(night=True, bg='#0C090B', card='#1A1417', card2='#141013', tx='#EDE4E6', sub='#A08C93', faint='#6E5E64',
              line='#322A2E', map='#1E171B', road='#3A2C33', mapTx='#A88E97', btn='#2C2328', glass='rgba(30,23,27,.92)'),
}
ROSE_STAGE = {'dia': ('#F6EEEB', '#EDE2DF', '#E6D9D5', '#DACBC7', '#FFFFFF', .85),
              'noche': ('#151013', '#1D171A', '#191316', '#0D0A0C', '#43363C', .55)}

if __name__ == '__main__':
    from playwright.sync_api import sync_playwright
    out = r.ROOT
    with sync_playwright() as p:
        b = p.chromium.launch(executable_path='/usr/bin/google-chrome')
        for style, pal, acc, stage in [('igual', r.TH, '#3E9BFF', None), ('rosa', ROSE, '#C9506E', ROSE_STAGE)]:
            r.A = acc; r.STAGE = stage
            r.ICON['nav'] = NAV0.replace('#3E9BFF', acc)
            for name, th in pal.items():
                pg = b.new_page(viewport={'width': 1920, 'height': 1080}, device_scale_factor=1)
                pg.set_content(r.home(th)); pg.wait_for_timeout(400)
                pg.screenshot(path=f'{out}/jeep_v4_{style}_{name}.png'); pg.close()
        b.close()
    print('ok')

# ---------- Navidad: la foto de Navidad de CARO en el panel central (como "foto de galería") ----------
XMAS = {
 'noche': dict(night=True, bg='#0B0D19', card='#161A2B', card2='#111423', tx='#F2E8EE', sub='#B39FB0', faint='#756C86',
              line='#2A2D44', map='#1A1D30', road='#3A3550', mapTx='#B39FB0', btn='#252940', glass='rgba(24,27,45,.92)'),
 'dia':   dict(night=False, bg='#F6E6EC', card='#FFF8FA', card2='#FBEFF3', tx='#2B1A24', sub='#8A6578', faint='#B496A6',
              line='#F0DAE3', map='#F7EAF0', road='#FFFFFF', mapTx='#9A7488', btn='#F8E9EF', glass='rgba(255,250,252,.92)'),
}

def xmas_showroom(t, w, h, cw):
    img = base64.b64encode(open(os.path.join(A, 'originales', 'jeep_navidad_rosa.jpg'), 'rb').read()).decode()
    # la foto llena el panel; se centra en el Jeep (x≈830 de 2576)
    s = max(w / 2576, h / 1438); bw, bh = 2576 * s, 1438 * s
    bx = min(0, max(w - bw, w / 2 - 830 * s)); by = (h - bh) / 2
    html = f'<img style="position:absolute;left:{bx:.1f}px;top:{by:.1f}px;width:{bw:.1f}px;height:{bh:.1f}px" src="data:image/jpeg;base64,{img}">'
    return html, 0

def render_xmas():
    from playwright.sync_api import sync_playwright
    blank = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4XmNgYGBgAAAABQABGWkDzwAAAABJRU5ErkJggg=='
    saved = (r.car, r.showroom, r.lights_img, r.CAR_NAME, r.A, r.STAGE)
    r.car = blank; r.showroom = xmas_showroom; r.lights_img = lambda t, style: ''; r.CAR_NAME = ''
    r.A = '#E8508A'; r.STAGE = None
    r.ICON['nav'] = NAV0.replace('#3E9BFF', r.A)
    with sync_playwright() as p:
        b = p.chromium.launch(executable_path='/usr/bin/google-chrome')
        for name, th in XMAS.items():
            pg = b.new_page(viewport={'width': 1920, 'height': 1080}, device_scale_factor=1)
            pg.set_content(r.home(th)); pg.wait_for_timeout(400)
            pg.screenshot(path=f'{r.ROOT}/jeep_v4_navidad_{name}.png'); pg.close()
        b.close()
    r.car, r.showroom, r.lights_img, r.CAR_NAME, r.A, r.STAGE = saved

if __name__ == '__main__':
    render_xmas()
    print('navidad ok')
