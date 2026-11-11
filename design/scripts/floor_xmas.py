import numpy as np, cv2, os, sys
S='/tmp/claude-0/-home-claude/97c03cd1-24de-528e-97cf-67eac5af2060/scratchpad'
sys.path.insert(0,S)
from rim import rim, R as RR
mode=sys.argv[1]; out=sys.argv[2]; FRAMES=[int(x) for x in sys.argv[3].split(',')] if len(sys.argv)>3 else range(90)
car0=cv2.imread('/home/claude/assets2/jeep_day.png',cv2.IMREAD_UNCHANGED).astype(np.float32)
if mode=='sport':
    bg=cv2.imread(S+'/bg_sport_nocar.png').astype(np.float32); cw=930; ox=120; oy=181
    clip=(0,90,1092,762,0); fadec=(520,700,560,190)
    grid=(40,90,30); lane=(108,255,92); dash=(108,255,92); arrow=(108,255,92); add=True
else:
    bg=cv2.imread(S+('/bg_xmas_night.png' if mode=='night' else '/bg_xmas_day.png')).astype(np.float32); cw=735; ox=144; oy=None
    clip=(129,21,894,879,36); fadec=(520,690,470,200)
    if mode=='night': grid=(110,45,120); lane=(174,111,255); dash=(80,40,240); arrow=(220,200,255); add=True
    else: grid=(215,190,235); lane=(160,90,245); dash=(70,30,215); arrow=(150,110,240); add=False
sc=cw/car0.shape[1]; car=cv2.resize(car0,(cw,int(round(car0.shape[0]*sc))),interpolation=cv2.INTER_AREA)
Hc,Wc=car.shape[:2]
if oy is None: oy=int(round(450*1.5-Hc))
Hh,Ww=bg.shape[:2]
A=np.array([323,-86.]); A/=np.linalg.norm(A); B=np.array([365,57.]); B/=np.linalg.norm(B)
P0=np.array([ox+600*sc, oy+723*sc]); k=cw/735
x0,y0,x1,y1,r=clip
panel=np.zeros((Hh,Ww),np.uint8)
if r: 
    cv2.rectangle(panel,(x0+r,y0),(x1-r,y1),1,-1); cv2.rectangle(panel,(x0,y0+r),(x1,y1-r),1,-1)
    for cx,cy in [(x0+r,y0+r),(x1-r,y0+r),(x0+r,y1-r),(x1-r,y1-r)]: cv2.circle(panel,(cx,cy),r,1,-1,cv2.LINE_AA)
else: cv2.rectangle(panel,(x0,y0),(x1,y1),1,-1)
fade=np.zeros((Hh,Ww),np.float32); fx,fy,fa,fb=fadec; cv2.ellipse(fade,(fx,fy),(fa,fb),-8,0,360,1,-1); fade=cv2.GaussianBlur(fade,(0,0),60); fade=fade/fade.max()*panel
def L(img,p,d,col,w,a0=-1500,a1=1500):
    cv2.line(img,tuple((p+d*a0).astype(int)),tuple((p+d*a1).astype(int)),col,w,cv2.LINE_AA)
RIMS=[]
os.makedirs(out,exist_ok=True)
# Luces: faros, faro lejano y faro de niebla (posiciones medidas en el dibujo)
LAMPS=[(137,320,30,34,1.3),(377,333,28,34,1.3),(103,484,14,16,0.9),(225,490,14,16,0.9)]
lampL=np.zeros((Hc,Wc),np.float32)
for x,y,a,b,w in LAMPS:
    g=np.zeros((Hc,Wc),np.float32); cv2.ellipse(g,(int(x*sc),int(y*sc)),(max(2,int(a*sc)),max(2,int(b*sc))),0,0,360,1,-1)
    lampL+=(cv2.GaussianBlur(g,(0,0),3)*1.6+cv2.GaussianBlur(g,(0,0),12)*1.1+cv2.GaussianBlur(g,(0,0),30)*0.5)*w
lampL=np.clip(lampL,0,1.8)
# Haz de luz sobre la carretera, hacia adelante
beam=np.zeros((Hh,Ww),np.float32)
Pf=np.array([ox+260*sc, oy+700*sc])
pts=np.array([Pf-30*k*B, Pf+60*k*B, Pf+90*k*B-420*k*A, Pf-120*k*B-420*k*A],np.int32)
cv2.fillConvexPoly(beam,pts,1.0); beam=cv2.GaussianBlur(beam,(0,0),40*k)*panel
LCOL=np.array((150,215,255),np.float32)
sp=6.0*k
for i in FRAMES:
    off=(i*sp)%(40*k)
    fl=np.zeros((Hh,Ww,3),np.float32); al=np.zeros((Hh,Ww),np.float32)
    def LL(p,d,col,w,a0=-1500,a1=1500):
        L(fl,p,d,col,w,a0,a1); L(al,p,d,1.0,w,a0,a1)
    for j in range(-30,31): LL(P0+j*40*k*B,A,grid,1)
    for j in range(-40,41): LL(P0+(j*40*k+off)*A,B,grid,1)
    for lat in (60,-350): LL(P0+lat*k*B,A,lane,4)
    ds=(i*sp*1.6)%(120*k)
    for j in range(-12,13):
        LL(P0+190*k*B+(j*120*k+ds)*A,A,dash,5,0,55*k); LL(P0-560*k*B+(j*120*k+ds)*A,A,dash,5,0,55*k)
    for j in range(-3,4):
        c=P0+330*k*B+(j*260*k+ds*2.0)*A; tip=c-38*k*A; l=c+20*k*A-26*k*B; rr=c+20*k*A+26*k*B
        poly=np.array([tip,l,c,rr],np.int32); cv2.fillPoly(fl,[poly],arrow,cv2.LINE_AA); cv2.fillPoly(al,[poly],1.0,cv2.LINE_AA)
    if add:
        glow=cv2.GaussianBlur(fl,(0,0),6); f=bg+(fl+glow*1.4)*fade[...,None]*0.9
    else:
        g=cv2.GaussianBlur(al,(0,0),4)*0.35; a=np.clip(al*0.85+g,0,1)*fade
        f=bg*(1-a[...,None])+fl/np.maximum(al[...,None],1e-3)*a[...,None]
    lv=0.0 if i<20 else min(1.0,(i-20)/15)
    if lv>0:
        f+=beam[...,None]*LCOL*(0.22 if add else 0.10)*lv
    roi=f[oy:oy+Hc,ox:ox+Wc]; a=car[...,3:]/255; roi[:]=roi*(1-a)+car[...,:3]*a
    rg=rim(-i*7.0,-6.0)
    for cx,cy,rx,ry,t in RIMS:
        th=np.radians(t); c_,s_=np.cos(th),np.sin(th)
        M=np.array([[1,0,cx],[0,1,cy],[0,0,1]])@np.array([[c_,-s_,0],[s_,c_,0],[0,0,1]])@np.diag([rx/RR,ry/RR,1])@np.array([[1,0,-RR],[0,1,-RR],[0,0,1]])
        w=cv2.warpAffine(rg,M[:2],(Wc,Hc),flags=cv2.INTER_AREA); aw=w[...,3:]/255; roi[:]=roi*(1-aw)+w[...,:3]*aw
    if lv>0:
        # Faro: la mica se pinta amarilla cálida (en un carro blanco el blanco no se nota) y un halo sale del carro
        core=np.clip(lampL*0.75,0,1)[...,None]*lv
        roi[:]=roi*(1-core)+np.array((150,225,255),np.float32)*core
        halo=cv2.GaussianBlur(lampL,(0,0),14)*lv*(0.9 if add else 0.5)
        roi+=halo[...,None]*np.array((140,200,255),np.float32)*(1-car[...,3:]/255*0.6)
    cv2.imwrite(f'{out}/{i:03d}.png',np.clip(f,0,255).astype(np.uint8))
