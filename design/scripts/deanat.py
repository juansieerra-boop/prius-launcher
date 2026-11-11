import cv2,numpy as np
S='/tmp/claude-0/-home-claude/97c03cd1-24de-528e-97cf-67eac5af2060/scratchpad'
im=cv2.imread('/home/claude/assets2/jeep_day.png',cv2.IMREAD_UNCHANGED)
H,W=im.shape[:2]; rgb=im[...,:3].copy(); g=cv2.cvtColor(rgb,cv2.COLOR_BGR2GRAY)
R=np.zeros((H,W),np.uint8)
cv2.fillPoly(R,[np.array([(297,247),(330,228),(420,222),(600,224),(690,232),(705,262),(706,300),(690,318),(560,318),(400,316),(398,290),(330,262),(297,260)],np.int32)],1)   # cofre
cv2.fillPoly(R,[np.array([(398,322),(460,312),(560,310),(660,318),(692,332),(682,360),(672,352),(668,372),(620,372),(560,380),(520,395),(500,412),(410,412),(390,400)],np.int32)],1)  # guardafango
cv2.fillPoly(R,[np.array([(408,410),(500,412),(490,440),(478,462),(420,462),(410,440)],np.int32)],1)  # tuberías bajo el guardafango
cv2.fillPoly(R,[np.array([(282,252),(400,252),(400,276),(340,273),(282,264)],np.int32)],1)
dark=(g<110).astype(np.uint8)
inside=dark&R
# Contornos de la carrocería: trazos oscuros que entran desde fuera de la zona
n,lab,st,_=cv2.connectedComponentsWithStats(dark,8)
border=cv2.dilate(R,np.ones((3,3),np.uint8))-cv2.erode(R,np.ones((3,3),np.uint8))
touch=np.unique(lab[(border>0)&(dark>0)])
keep=np.isin(lab,touch[touch>0])&(dark>0)
cv2.imwrite(S+'/keep.png',(keep&(R>0)).astype(np.uint8)*255)
# Piezas del motor pegadas a los contornos: se borran y se redibuja el borde del guardafango
clr=np.zeros((H,W),np.uint8)
cv2.fillPoly(clr,[np.array([(470,302),(706,268),(706,346),(470,352)],np.int32)],1)
cv2.rectangle(clr,(405,413),(492,470),1,-1); cv2.rectangle(clr,(505,338),(672,363),1,-1); cv2.fillPoly(clr,[np.array([(282,252),(400,252),(400,276),(340,273),(282,264)],np.int32)],1)
keep=keep&(clr==0)&(R>0)
# pedacitos sueltos y restos rojos dentro de la zona
n2,lab2,st2,_=cv2.connectedComponentsWithStats(keep.astype(np.uint8),8)
for i in range(1,n2):
    if st2[i,4]<60: keep[lab2==i]=False
b_,g_,r_=[rgb[...,i].astype(int) for i in range(3)]
redish=(r_-np.maximum(g_,b_)>18)
keep=keep&~redish
paint=(R>0)&(~keep)
yy,xx=np.mgrid[0:H,0:W]
base=np.full((H,W),247,np.float32)-np.clip((yy-300)/150,0,1)*8
out=rgb.astype(np.float32)
fill=np.repeat(base[...,None],3,2)
# hueco de la llanta (debajo del guardafango): gris como el del otro lado
well=np.zeros((H,W),np.uint8); cv2.fillPoly(well,[np.array([(405,414),(492,414),(482,440),(470,470),(405,470)],np.int32)],1)
wc=np.median(rgb[395:410,560:600].reshape(-1,3),0).astype(np.float32)
wm=cv2.GaussianBlur(well.astype(np.float32),(0,0),5)[...,None]
fill=fill*(1-wm*0.6)+wc*(wm*0.6)
# fundido suave en la orilla de la zona para que no se note el corte
pm=cv2.GaussianBlur(paint.astype(np.float32),(0,0),1.6)
pm=np.where(paint,1.0,pm*0.0)  # dentro: relleno total
soft=cv2.GaussianBlur((R>0).astype(np.float32),(0,0),2.5)
w=np.where(keep,0.0,np.clip(soft*1.6,0,1))[...,None]
out=out*(1-w)+fill*w
o=np.clip(out,0,255).astype(np.uint8)
ink=(58,58,58)
cv2.polylines(o,[np.array([(400,305),(470,297),(560,287),(640,279),(705,272)],np.int32)],False,ink,2,cv2.LINE_AA)
cv2.polylines(o,[np.array([(460,322),(520,316),(600,310),(650,313),(676,325),(694,342)],np.int32)],False,ink,2,cv2.LINE_AA)
cv2.polylines(o,[np.array([(522,384),(536,370),(556,362),(600,357),(650,355),(670,358),(680,368)],np.int32)],False,ink,2,cv2.LINE_AA)
cv2.imwrite('/home/claude/assets2/jeep_plain.png',np.dstack([o,im[...,3]]))
a=im[...,3:]/255.; p=(o*a+255*(1-a)).astype(np.uint8)
cv2.imwrite(S+'/plain_z.png',cv2.resize(p[150:500,250:850],None,fx=2,fy=2))
