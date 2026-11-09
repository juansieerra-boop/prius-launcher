import numpy as np, cv2, math
R=256; C=R  # canvas 512, circle space
def spokes_layer(angle):
    """Capa que gira: rayos dobles, centro y birlos."""
    img=np.zeros((2*R,2*R,4),np.float32)
    def bar(r0,a0,r1,a1,w0,w1,col,hl):
        A0=math.radians(a0); A1=math.radians(a1)
        p0=(C+math.cos(A0)*R*r0, C+math.sin(A0)*R*r0); p1=(C+math.cos(A1)*R*r1, C+math.sin(A1)*R*r1)
        d=(p1[0]-p0[0],p1[1]-p0[1]); L=math.hypot(*d); n=(-d[1]/L,d[0]/L)
        poly=np.array([(p0[0]+n[0]*w0,p0[1]+n[1]*w0),(p1[0]+n[0]*w1,p1[1]+n[1]*w1),(p1[0]-n[0]*w1,p1[1]-n[1]*w1),(p0[0]-n[0]*w0,p0[1]-n[1]*w0)],np.int32)
        cv2.fillPoly(img,[poly],col,cv2.LINE_AA)
        cv2.line(img,(int(p0[0]+n[0]*w0),int(p0[1]+n[1]*w0)),(int(p1[0]+n[0]*w1),int(p1[1]+n[1]*w1)),hl,4,cv2.LINE_AA)
    for i in range(5):
        base=angle+i*72
        bar(0.2,base,0.52,base,R*0.085,R*0.07,(64,61,59,255),(96,92,90,255))
        for dv in (-11,11):
            bar(0.48,base,0.9,base+dv,R*0.05,R*0.045,(64,61,59,255),(96,92,90,255))
    cv2.circle(img,(C,C),int(R*0.24),(58,55,53,255),-1,cv2.LINE_AA)
    cv2.circle(img,(C,C),int(R*0.24),(90,86,84,255),3,cv2.LINE_AA)
    cv2.circle(img,(C,C),int(R*0.10),(40,38,37,255),-1,cv2.LINE_AA)
    for i in range(5):
        a=math.radians(angle+36+i*72)
        cv2.circle(img,(int(C+math.cos(a)*R*0.165),int(C+math.sin(a)*R*0.165)),int(R*0.03),(150,146,144,255),-1,cv2.LINE_AA)
    return img
yy,xx=np.mgrid[0:2*R,0:2*R]; rr=np.hypot(xx-C,yy-C)/R
# capa fija: fondo del rin (oscuro), labio exterior y luz fija arriba-izquierda
base=np.zeros((2*R,2*R,4),np.float32)
base[...,:3]=np.where((rr<0.9)[...,None],(16,15,15),(0,0,0))
lip=((rr>=0.9)&(rr<1.0))
base[...,:3]=np.where(lip[...,None],(34,33,32),base[...,:3])
edge=((rr>=0.9)&(rr<0.925))
base[...,:3]=np.where(edge[...,None],(70,68,66),base[...,:3])
base[...,3]=np.where(rr<1.0,255,0)
light=np.clip(1.0+0.35*(-(xx-C)-(yy-C))/(R*1.4),0.75,1.35)[...,None]
def rim(angle,blur=0.0):
    s=spokes_layer(angle)
    if blur>0:
        n=7; acc=np.zeros_like(s)
        for j in range(n): acc+=spokes_layer(angle-blur*j/(n-1))/n
        s=acc
    a=s[...,3:]/255
    out=base.copy(); out[...,:3]=base[...,:3]*(1-a)+s[...,:3]*a
    out[...,:3]*=light
    out[...,3]=np.where(rr<1.0,255,0)
    out[...,3]=cv2.GaussianBlur(out[...,3],(0,0),1.0)
    return out
np.save('/home/claude/rim/light.npy',light)
