# Render: mapa en vivo nuevo (sin botones) y carro sin la orilla clara — Prius de noche y día, Jeep oro rosa
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_v4 as r
from playwright.sync_api import sync_playwright
r.LIVE = True
with sync_playwright() as p:
    b = p.chromium.launch(executable_path='/usr/bin/google-chrome')
    def shot(html, name):
        pg = b.new_page(viewport={'width': 1920, 'height': 1080}, device_scale_factor=1)
        pg.set_content(html); pg.wait_for_timeout(400); pg.screenshot(path=f'{r.ROOT}/{name}.png'); pg.close()
    shot(r.home(r.TH['noche']), 'v4_mapa_vivo_noche')
    shot(r.home(r.TH['dia']), 'v4_mapa_vivo_dia')
    r.NAV = dict(app='Organic Maps', color='#3E9BFF', dist='300 ft', turn='Gira a la derecha', street='Jamboree Rd', eta='10:42 PM', left='12 min · 3.4 mi')
    shot(r.home(r.TH['noche']), 'v4_mapa_vivo_navegando')
    r.NAV = None
    import render_v4_jeep as j
    r.A = '#C9506E'; r.STAGE = j.ROSE_STAGE; r.ICON['nav'] = j.NAV0.replace('#3E9BFF', r.A)
    shot(r.home(j.ROSE['noche']), 'jeep_v4_mapa_vivo_noche')
    b.close()
print('ok')
