# Render: menú de apps estilo CarPlay (íconos grandes, 5 x 2, páginas). En la app salen los íconos reales.
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_v4 as r
from playwright.sync_api import sync_playwright

APPS = [('ZLink', '#29C24A', 'Z'), ('Waze', '#33CCFF', 'W'), ('Organic Maps', '#2E7D32', 'O'), ('Spotify', '#1DB954', 'S'), ('Teléfono', '#34C759', 'T'),
        ('YouTube', '#FF3B30', 'Y'), ('Radio', '#FF9500', 'R'), ('Google Maps', '#4285F4', 'G'), ('Cámara', '#8E8E93', 'C'), ('Ajustes', '#636366', '⚙')]

def page(t):
    bar = r.home(t)
    i0 = bar.index('<div class="card map">')
    head = bar[:i0]
    cells = ''.join(f'''<div style="display:flex;flex-direction:column;align-items:center;gap:12px">
      <div style="width:112px;height:112px;border-radius:26px;background:linear-gradient(160deg,{c}, {c}CC);display:flex;align-items:center;justify-content:center;color:#fff;font-size:44px;font-weight:600;box-shadow:0 6px 18px rgba(0,0,0,{.35 if t["night"] else .12})">{g}</div>
      <div style="font-size:15px;font-weight:600;color:{t["tx"]}">{n}</div></div>''' for n, c, g in APPS)
    dots = f'<div style="position:absolute;left:0;right:0;bottom:22px;display:flex;gap:8px;justify-content:center"><i style="width:8px;height:8px;border-radius:4px;background:{t["sub"]}"></i><i style="width:8px;height:8px;border-radius:4px;background:{t["line"]}"></i></div>'
    body = f'''<div class="card" style="left:10px;top:78px;width:1047px;height:512px">
      <div style="position:absolute;left:0;right:0;top:56px;display:grid;grid-template-columns:repeat(5,1fr);row-gap:40px;padding:0 60px">{cells}</div>{dots}</div></body></html>'''
    return head + body

with sync_playwright() as p:
    b = p.chromium.launch(executable_path='/usr/bin/google-chrome')
    for name, th in r.TH.items():
        pg = b.new_page(viewport={'width': 1920, 'height': 1080}, device_scale_factor=1)
        pg.set_content(page(th)); pg.wait_for_timeout(400); pg.screenshot(path=f'{r.ROOT}/v4_menu_apps_{name}.png'); pg.close()
    b.close()
print('ok')
