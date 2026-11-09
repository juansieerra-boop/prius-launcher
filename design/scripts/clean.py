import numpy as np, cv2, sys
src,out,thr,dil=sys.argv[1],sys.argv[2],int(sys.argv[3]),int(sys.argv[4])
im=cv2.imread(src,cv2.IMREAD_UNCHANGED); a=im[...,3]
g=cv2.cvtColor(im[...,:3],cv2.COLOR_BGR2GRAY)
walls=((g<thr)&(a>128)).astype(np.uint8)
walls=cv2.dilate(walls,np.ones((dil,dil),np.uint8))
free=((walls==0)).astype(np.uint8)
H,W=a.shape
# seeds: transparent pixels
seed=((a<20)&(free>0)).astype(np.uint8)
n,lab=cv2.connectedComponents(free,connectivity=4)
bgl=np.unique(lab[seed>0]); bg=np.isin(lab,bgl[bgl>0])
keep=(~bg)&(a>20)
keep=keep.astype(np.uint8)
# undo dilation shrink: grow keep back into wall band (walls are car)
keep=cv2.morphologyEx(keep,cv2.MORPH_CLOSE,np.ones((5,5),np.uint8))
n,lab,st,_=cv2.connectedComponentsWithStats(keep,8)
big=np.argmax(st[1:,4])+1; keep=(lab==big).astype(np.uint8)
# fill holes
ff=keep.copy(); m=np.zeros((H+2,W+2),np.uint8); cv2.floodFill(ff,m,(0,0),1); keep=keep|(1-ff)
na=(keep*255).astype(np.float32)
na=cv2.erode(na,np.ones((3,3),np.uint8)); na=cv2.GaussianBlur(na,(0,0),0.9)
na=np.minimum(na,a.astype(np.float32))
im[...,3]=na.astype(np.uint8)
cv2.imwrite(out,im)
bg=np.full((H,W,3),(243,242,242),np.float32); al=na[...,None]/255
cv2.imwrite(out.replace('.png','_prev.png'),(im[...,:3]*al+bg*(1-al)).astype(np.uint8))
