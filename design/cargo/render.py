import base64
car=base64.b64encode(open('/home/claude/ns/car_ns.png','rb').read()).decode()
CW=560; sc=CW/909; CX=40; CY=70  # car position inside preview box
def m(x,y): return CX+x*sc, CY+y*sc
wheels=[(444,364,41,61),(844,275,24,46)]
lamps=[(306,250),(258,256),(36,210),(290,343)]
mk=''
for i,(x,y,rx,ry) in enumerate(wheels):
    X,Y=m(x,y); r=max(rx,ry)*sc
    mk+=f'<div class="wh" style="left:{X-r-6}px;top:{Y-r-6}px;width:{2*r+12}px;height:{2*r+12}px"><span>{"Delantera" if i==0 else "Trasera"}</span></div>'
for x,y in lamps:
    X,Y=m(x,y); mk+=f'<div class="lp" style="left:{X-9}px;top:{Y-9}px"></div>'
html=f'''<html><head><meta charset="utf-8"><style>
*{{margin:0;box-sizing:border-box}} body{{width:1024px;height:600px;background:#000;font-family:-apple-system,Inter,"Helvetica Neue","DejaVu Sans",sans-serif;color:#fff;overflow:hidden}}
.wrap{{display:flex;gap:10px;padding:10px;height:600px}}
.side{{width:250px;background:#1A1C20;border-radius:20px;padding:18px 16px;display:flex;flex-direction:column}}
.logo{{display:flex;align-items:center;gap:10px;margin-bottom:6px}}
.logo .ic{{width:34px;height:34px;border-radius:9px;background:linear-gradient(#3FA2FF,#1C5FE0);display:grid;place-items:center;font-weight:800;font-size:15px}}
.logo b{{font-size:22px;letter-spacing:.5px}} .sub{{color:#8E939B;font-size:12px;margin-bottom:18px}}
.step{{display:flex;gap:12px;align-items:center;padding:11px 10px;border-radius:14px;margin-bottom:6px;color:#9AA0A8;font-size:14px}}
.step .n{{width:26px;height:26px;border-radius:50%;border:1.5px solid #4A4E55;display:grid;place-items:center;font-size:12px;font-weight:700}}
.step.done .n{{background:#34C759;border-color:#34C759;color:#fff}} .step.on{{background:#2A2D33;color:#fff}} .step.on .n{{border-color:#3FA2FF;color:#3FA2FF}}
.step small{{display:block;color:#8E939B;font-size:11px;margin-top:2px}}
.lock{{margin-top:auto;color:#8E939B;font-size:11px;display:flex;gap:8px;align-items:center}}
.main{{flex:1;display:flex;flex-direction:column;gap:10px}}
.prev{{flex:1;position:relative;border-radius:24px;background:linear-gradient(#16191E,#0B0D10);overflow:hidden}}
.prev .car{{position:absolute;left:{CX}px;top:{CY}px;width:{CW}px}}
.hint{{position:absolute;left:20px;top:16px;font-size:15px;font-weight:600}} .hint small{{display:block;color:#9AA0A8;font-weight:400;font-size:12px;margin-top:3px}}
.wh{{position:absolute;border:2.5px solid #3FA2FF;border-radius:50%;box-shadow:0 0 14px #3FA2FF88}}
.wh:before,.wh:after{{content:"";position:absolute;background:#3FA2FF;left:50%;top:50%}} .wh:before{{width:16px;height:2px;margin:-1px 0 0 -8px}} .wh:after{{width:2px;height:16px;margin:-8px 0 0 -1px}}
.wh span{{position:absolute;top:-24px;left:50%;transform:translateX(-50%);background:#3FA2FF;font-size:10px;font-weight:700;padding:2px 7px;border-radius:8px;white-space:nowrap}}
.lp{{position:absolute;width:18px;height:18px;border-radius:50%;background:radial-gradient(#fff,#FFD84A 45%,#FFD84A00 72%);box-shadow:0 0 14px #FFD84A}}
.tools{{position:absolute;right:16px;top:16px;display:flex;gap:8px}}
.chip{{background:#2A2D33;border-radius:14px;padding:7px 12px;font-size:12px;color:#ddd}} .chip.a{{background:#3FA2FF;color:#fff}}
.bar{{height:96px;background:#1A1C20;border-radius:20px;display:flex;align-items:center;gap:14px;padding:0 16px}}
.field{{flex:1}} .field label{{font-size:11px;color:#8E939B}} .field div{{margin-top:4px;background:#2A2D33;border-radius:12px;padding:10px 14px;font-size:18px;letter-spacing:3px;font-weight:300}}
.btn{{border-radius:14px;padding:13px 18px;font-size:14px;font-weight:600;background:#2A2D33}} .btn.p{{background:#34C759}}
</style></head><body><div class="wrap">
<div class="side">
 <div class="logo"><div class="ic">CG</div><b>CarGo</b></div><div class="sub">Modo instalador</div>
 <div class="step done"><div class="n">✓</div><div>Foto del carro<small>carro_cliente.png</small></div></div>
 <div class="step done"><div class="n">✓</div><div>Llantas<small>Delantera y trasera</small></div></div>
 <div class="step on"><div class="n">3</div><div>Luces<small>Toca cada faro · 4 de 4</small></div></div>
 <div class="step"><div class="n">4</div><div>Nombre en pantalla</div></div>
 <div class="step"><div class="n">5</div><div>Vista previa y guardar</div></div>
 <div class="lock">🔒 Protegido con tu código de instalador</div>
</div>
<div class="main">
 <div class="prev">
  <div class="hint">Toca los faros del carro<small>Se encienden solos de 5 PM a 6 AM · mantén presionado para borrar</small></div>
  <div class="tools"><div class="chip">Deshacer</div><div class="chip a">Probar luces</div><div class="chip">Probar llantas</div></div>
  <img class="car" src="data:image/png;base64,{car}">{mk}
 </div>
 <div class="bar">
  <div class="btn">Cambiar foto</div>
  <div class="field"><label>Nombre que sale en la pantalla</label><div>CAMRY</div></div>
  <div class="btn">Atrás</div><div class="btn p">Siguiente</div>
 </div>
</div></div></body></html>'''
open('installer.html','w').write(html)
from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    b=p.chromium.launch(executable_path='/usr/bin/google-chrome'); pg=b.new_page(viewport={'width':1024,'height':600},device_scale_factor=1.5)
    pg.goto('file:///home/claude/cargo/installer.html'); pg.wait_for_timeout(500); pg.screenshot(path='/home/claude/cargo/modo_instalador.png'); b.close()
