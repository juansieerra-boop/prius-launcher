import base64,json,sys
from playwright.sync_api import sync_playwright
D='/home/claude/prius-launcher/cargo/src/main/res/drawable-nodpi/'
def b64(f): return base64.b64encode(open(D+f,'rb').read()).decode()
P={ # VehicleAssets.palette
 'prius_night':dict(bg='#000000',panel='#12151A',card='#1C1F24',stroke='#2A2E35',text='#FFFFFF',muted='#9AA0A8',accent='#3E9BFF',accent2='#34C759',grid='#1E558C',lane='#2896FF',dash='#3CFF78',glow=1),
 'prius_day':dict(bg='#E3E6ED',panel='#E3E6ED',card='#ECEFF4',stroke='#D3D8E1',text='#292C33',muted='#6E7480',accent='#C7F063',accent2='#2B7BFF',grid='#C8D5E6',lane='#2B8CF5',dash='#3CC878',glow=0),
 'jeep_night':dict(bg='#05060C',panel='#0E1326',card='#141A30',stroke='#3A2E3A',text='#F6E9E4',muted='#A48E95',accent='#E0244E',accent2='#E8A598',grid='#262D50',lane='#E8A598',dash='#E0244E',glow=1),
 'jeep_day':dict(bg='#F8EEE8',panel='#FFF9F6',card='#FFFAF7',stroke='#E8CFC6',text='#4A1A28',muted='#9C7480',accent='#B3123A',accent2='#C98B7B',grid='#ECD4CD',lane='#C98B7B',dash='#B3123A',glow=0)}
CARS={'prius':('prius_clean.webp',[0.484,0.982],'Toyota Prius 2011'),'jeep':('jeep_cherry.webp',[0.429,0.989],'Jeep Wrangler Sahara 2021')}
tpl=open('tpl.html').read()
with sync_playwright() as p:
    b=p.chromium.launch(executable_path='/usr/bin/google-chrome')
    for key in ['prius_night','jeep_night','prius_day','jeep_day']:
        car=key.split('_')[0]; f,anchor,name=CARS[car]
        cfg=dict(pal=P[key],img='data:image/webp;base64,'+b64(f),anchor=anchor)
        html=tpl.replace('/*CFG*/',json.dumps(cfg))
        pg=b.new_page(viewport={'width':1024,'height':600},device_scale_factor=1.5)
        pg.set_content(html); pg.wait_for_function('window.done===true'); pg.screenshot(path=f'cargo_{key}.png'); pg.close()
    b.close()
