# Render v4 — Ajustes (botón ⚙ de la barra o mantener presionado el carro). Toda la configuración del launcher.
# Uso: python3 design/v4/render_v4_ajustes.py   (desde la raíz del repo)
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import render_v4 as _r
from render_v4 import TH, home, ROOT
MAP_APP = 1

def sheet(t):
    A = _r.A; CAR_NAME = _r.CAR_NAME
    lab = lambda x, y, txt: f'<div style="position:absolute;left:{x}px;top:{y}px;font-size:11px;font-weight:700;color:{t["faint"]};letter-spacing:.8px">{txt}</div>'
    def seg(x, y, w, items, on):
        cells = ''.join(f'<div style="flex:1;border-radius:10px;display:flex;flex-direction:column;align-items:center;justify-content:center;font-size:13px;font-weight:600;'
                        f'{"background:"+t["card"]+";box-shadow:0 1px 3px rgba(0,0,0,.14);color:"+t["tx"] if i == on else "color:"+t["sub"]}">{a}'
                        f'{f"<span style=\'font-size:10px;font-weight:500;color:{t["faint"]};margin-top:1px\'>{b}</span>" if b else ""}</div>' for i, (a, b) in enumerate(items))
        return f'<div style="position:absolute;left:{x}px;top:{y}px;width:{w}px;height:44px;border-radius:13px;background:{t["btn"]};padding:4px;display:flex;gap:4px">{cells}</div>'
    def toggle(on):
        return (f'<div style="width:44px;height:26px;border-radius:13px;background:{"#34C759" if on else t["line"]};position:relative">'
                f'<div style="position:absolute;top:3px;{"right" if on else "left"}:3px;width:20px;height:20px;border-radius:10px;background:#fff;box-shadow:0 1px 2px rgba(0,0,0,.25)"></div></div>')
    def group(x, y, w, rows):
        r = ''
        for i, (icon, name, val, right) in enumerate(rows):
            sep = f'border-top:1px solid {t["line"]};' if i else ''
            r += (f'<div style="height:46px;display:flex;align-items:center;gap:12px;padding:0 14px;{sep}">'
                  f'<span style="width:20px;display:flex;justify-content:center;color:{t["sub"]}">{icon}</span>'
                  f'<span style="flex:1;font-size:14px;font-weight:600">{name}</span>'
                  f'<span style="font-size:12px;color:{t["faint"]};font-weight:500">{val}</span>{right}</div>')
        return f'<div style="position:absolute;left:{x}px;top:{y}px;width:{w}px;border-radius:13px;background:{t["btn"]};overflow:hidden">{r}</div>'
    wifi = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M2 8.5a15 15 0 0 1 20 0M5 12a10 10 0 0 1 14 0M8.5 15.5a5 5 0 0 1 7 0"/><circle cx="12" cy="19" r="1.2" fill="currentColor"/></svg>'
    bt = '<svg width="16" height="18" viewBox="0 0 20 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 7l11 10-5 5V2l5 5L4 17"/></svg>'
    grid = '<svg width="16" height="16" viewBox="0 0 20 20" fill="currentColor"><rect x="2" y="2" width="7" height="7" rx="2"/><rect x="11" y="2" width="7" height="7" rx="2"/><rect x="2" y="11" width="7" height="7" rx="2"/><rect x="11" y="11" width="7" height="7" rx="2"/></svg>'
    reset = '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/></svg>'
    chev = f'<span style="color:{t["faint"]};font-size:18px;margin-left:4px">›</span>'
    pencil = f'<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="{t["sub"]}" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/></svg>'
    L, R, CW = 24, 428, 368
    return f'''<div style="position:absolute;inset:0;background:rgba(0,0,0,.40)"></div>
<div style="position:absolute;left:123px;top:30px;width:820px;height:540px;border-radius:26px;background:{t["card"]};box-shadow:0 24px 60px rgba(0,0,0,.3)">
 <div style="position:absolute;left:{L}px;top:22px;font-size:20px;font-weight:700">Ajustes</div>
 <div style="position:absolute;right:20px;top:18px;width:32px;height:32px;border-radius:16px;background:{t["btn"]};display:flex;align-items:center;justify-content:center;color:{t["sub"]};font-size:15px">✕</div>
 {lab(L, 74, 'VEHÍCULO')}
 <div style="position:absolute;left:{L}px;top:94px;width:{CW}px;height:44px;border-radius:13px;background:{t["btn"]};display:flex;align-items:center;padding:0 14px;gap:10px">
  <span style="font-size:12px;color:{t["faint"]};font-weight:600;width:56px">Nombre</span><span style="flex:1;font-size:15px;font-weight:600;letter-spacing:2px">{CAR_NAME}</span>{pencil}</div>
 {lab(L, 158, 'PANEL CENTRAL')}
 {seg(L, 178, CW, [('Vehículo', ''), ('Galería', ''), ('Widget', '')], 0)}
 {lab(L, 242, 'LUCES')}
 {seg(L, 262, CW, [('Auto', '5 PM – 6 AM'), ('Siempre', ''), ('Apagadas', '')], 0)}
 {lab(L, 326, 'TEMA')}
 {seg(L, 346, CW, [('Auto', ''), ('Claro', ''), ('Oscuro', '')], 0)}
 {lab(L, 410, 'APP DEL MAPA')}
 {seg(L, 430, CW, [('Google Maps', ''), ('Waze', ''), ('Otra app', '')], MAP_APP)}
 {lab(R, 74, 'IDIOMA')}
 {seg(R, 94, CW, [('Español', ''), ('English', '')], 0)}
 {lab(R, 158, 'CONEXIONES')}
 {group(R, 178, CW, [(wifi, 'Wi-Fi', 'Conectado', toggle(True)), (bt, 'Bluetooth', 'Activado', toggle(True))])}
 {lab(R, 302, 'PANTALLA')}
 {group(R, 322, CW, [(grid, 'Editar widgets', '', chev), (reset, 'Restablecer diseño', '', chev)])}
 <div style="position:absolute;right:24px;bottom:24px;width:180px;height:46px;border-radius:14px;background:{A};color:#fff;display:flex;align-items:center;justify-content:center;font-size:15px;font-weight:600">Guardar</div>
 <div style="position:absolute;left:{L}px;bottom:38px;font-size:12px;color:{t["faint"]};font-weight:500">CarGO · v4</div>
</div>'''

if __name__ == '__main__':
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        b = p.chromium.launch(executable_path='/usr/bin/google-chrome')
        for name, t in TH.items():
            html = home(t).replace('</body>', sheet(t) + '</body>')
            pg = b.new_page(viewport={'width': 1920, 'height': 1080}, device_scale_factor=1)
            pg.set_content(html); pg.wait_for_timeout(400); pg.screenshot(path=f'{ROOT}/v4_ajustes_{name}.png'); pg.close()
        b.close()
    print('ok')
