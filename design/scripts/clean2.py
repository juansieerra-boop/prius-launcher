import numpy as np, cv2
def cutmask(shape, lines, tires):
    H,W=shape
    m=np.ones((H,W),np.uint8)
    for pts in lines:
        poly=np.array(pts+[(pts[-1][0],H+5),(pts[0][0],H+5)],np.int32)
        cv2.fillPoly(m,[poly],0)
    for (cx,cy,rx,ry) in tires: cv2.ellipse(m,(cx,cy),(rx,ry),0,0,360,1,-1)
    return m
def apply(src,dst,m,fix_rgb):
    im=cv2.imread(src,cv2.IMREAD_UNCHANGED).astype(np.float32)
    a=im[...,3]*m
    a=cv2.erode(a,np.ones((3,3),np.uint8))
    a=cv2.GaussianBlur(a,(0,0),0.7)
    if fix_rgb:
        # darken light fringe on the outer edge
        band=(cv2.dilate((a<250).astype(np.uint8),np.ones((7,7),np.uint8))>0)&(a>0)
        mn=cv2.erode(im[...,:3],np.ones((5,5),np.uint8))
        im[...,:3]=np.where(band[...,None],np.minimum(im[...,:3],mn*0.5+im[...,:3]*0.5),im[...,:3])
    im[...,3]=a
    cv2.imwrite(dst,im.clip(0,255).astype(np.uint8))
    return im
car_lines=[[(-5,612),(40,625),(200,652),(380,677),(500,692),(600,700),(700,704)],[(860,598),(1000,572),(1240,522),(1300,520)]]
car_tires=[(757,600,104,158),(1297,410,74,121)]
an_lines=[[(840,628),(1000,592),(1240,532),(1300,530)]]
an_tires=[(765,600,108,156),(1300,412,74,121)]
mc=cutmask((757,1400),car_lines,car_tires)
ma=cutmask((754,1400),an_lines,an_tires)
for s,d,m,f in [('/home/claude/caro/car_dia.png','car_day.png',mc,True),('/home/claude/caro/car_noche_cian.png','car_night.png',mc,True),
                ('/home/claude/anat/anat_dia.png','anat_day.png',ma,True),('/home/claude/anat/anat_noche.png','anat_night.png',ma,True),('/home/claude/anat/anat_pipes.png','anat_pipes.png',ma,False)]:
    apply(s,d,m,f)
for n in ['car_day','anat_day']:
    im=cv2.imread(n+'.png',cv2.IMREAD_UNCHANGED).astype(np.float32); al=im[...,3:]/255
    bg=np.full(im.shape[:2]+(3,),(243,242,242),np.float32)
    cv2.imwrite(n+'_prev.png',(im[...,:3]*al+bg*(1-al)).astype(np.uint8))
