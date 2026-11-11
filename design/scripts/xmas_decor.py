import math,random
P='#FF6FAE'; P2='#FFB3D1'; RED='#E3123B'
def cherry(x,y,s=1.0,leaf=True):
    g=f'<g transform="translate({x},{y}) scale({s})">'
    g+='<path d="M-9,10 C-8,-4 -2,-14 6,-20 M9,12 C8,-2 6,-12 6,-20" stroke="#3E7A2E" stroke-width="2.4" fill="none" stroke-linecap="round"/>'
    if leaf: g+='<path d="M6,-20 C14,-28 24,-24 26,-18 C18,-14 10,-15 6,-20 Z" fill="#4FA83A"/>'
    for cx,cy in ((-9,14),(9,16)):
        g+=f'<circle cx="{cx}" cy="{cy}" r="9" fill="{RED}"/><circle cx="{cx}" cy="{cy}" r="9" fill="url(#chg)"/><ellipse cx="{cx-3}" cy="{cy-3.5}" rx="2.6" ry="1.8" fill="#fff" opacity=".85"/>'
    return g+'</g>'
def flake(x,y,r,op,c='#fff'):
    s=f'<g stroke="{c}" stroke-width="1.4" stroke-linecap="round" opacity="{op}">'
    for a in range(0,180,60):
        dx=r*math.cos(math.radians(a)); dy=r*math.sin(math.radians(a))
        s+=f'<line x1="{x-dx:.1f}" y1="{y-dy:.1f}" x2="{x+dx:.1f}" y2="{y+dy:.1f}"/>'
    return s+'</g>'
def decor(night=True):
    D=['<defs><radialGradient id="chg" cx=".35" cy=".3" r=".8"><stop offset="0" stop-color="#FF5A7A"/><stop offset="1" stop-color="#8E0022" stop-opacity=".9"/></radialGradient>'
       '<filter id="bulb" x="-200%" y="-200%" width="500%" height="500%"><feGaussianBlur stdDeviation="3.5" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>']
    # copos en el panel del carro
    random.seed(12)
    for _ in range(26):
        x=random.uniform(110,580); y=random.uniform(130,300)
        D.append(flake(x,y,random.uniform(2.5,5.5),random.uniform(.18,.45),'#fff' if night else '#E58AB4'))
    # guirnalda de luces arriba del panel del carro
    x0,x1,y0,sag=232,540,24,18
    pts=[(x0+(x1-x0)*t/40, y0+sag*4*(t/40)*(1-t/40)) for t in range(41)]
    D.append('<path d="M'+' L'.join(f'{x:.1f},{y:.1f}' for x,y in pts)+f'" stroke="{"#5B6B4A" if night else "#7A9A5E"}" stroke-width="1.6" fill="none"/>')
    cols=[P,'#FFFFFF',RED,P2]
    for i,t in enumerate(range(2,40,3)):
        x,y=pts[t]; c=cols[i%4]
        D.append(f'<rect x="{x-1.6:.1f}" y="{y:.1f}" width="3.2" height="3" fill="#6E7A60"/><ellipse cx="{x:.1f}" cy="{y+7:.1f}" rx="3.4" ry="4.6" fill="{c}" {"filter=\"url(#bulb)\"" if night else ""}/>')
    # cerezas colgando de la guirnalda
    for t,L in ((10,34),(22,52),(33,30)):
        x,y=pts[t]
        D.append(f'<line x1="{x:.1f}" y1="{y:.1f}" x2="{x:.1f}" y2="{y+L}" stroke="{P2}" stroke-width="1" opacity=".8"/>')
        D.append(cherry(x+1,y+L+18,0.9))
    # moñito rosa en la tarjeta de música y cerezas en el clima
    D.append(f'<g transform="translate(786,372)"><ellipse cx="-8" cy="-2" rx="9" ry="5.5" transform="rotate(-25 -8 -2)" fill="none" stroke="{P}" stroke-width="3.2"/><ellipse cx="8" cy="-2" rx="9" ry="5.5" transform="rotate(25 8 -2)" fill="none" stroke="{P}" stroke-width="3.2"/><path d="M-2,2 L-8,15 M2,2 L8,15" stroke="{P}" stroke-width="3.2" stroke-linecap="round"/><circle cx="0" cy="0" r="3.6" fill="{P2}"/></g>')
    D.append(cherry(988,384,0.62,False))
    # copos en el mapa
    for _ in range(10):
        D.append(flake(random.uniform(620,1000),random.uniform(80,240),random.uniform(2.5,4.5),.35,'#fff' if night else '#E58AB4'))
    return ''.join(D)
