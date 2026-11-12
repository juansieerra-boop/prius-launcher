# Vidrios polarizados para el carro (909x454). Uso: python3 polarizado.py entrada.png salida.png [VLT]
# Detecta cada vidrio rellenando desde un punto adentro hasta las líneas negras del marco,
# toma su contorno (casco convexo, encogido 1 px para no pintar el marco) y lo oscurece.
import sys, numpy as np
from collections import deque
from PIL import Image, ImageFilter, ImageDraw
src, dst = sys.argv[1], sys.argv[2]
VLT = float(sys.argv[3]) if len(sys.argv) > 3 else 0.22   # luz que pasa (0.22 ≈ polarizado oscuro)
im = Image.open(src).convert('RGBA'); a = np.asarray(im).astype(float)
L = a[..., :3].mean(-1); H, W = L.shape

def region(seed, thr):
    dk = np.asarray(Image.fromarray(((L < thr) | (a[..., 3] < 128)).astype('uint8') * 255).filter(ImageFilter.MaxFilter(3))) > 0
    sx, sy = seed; seen = np.zeros((H, W), bool); seen[sy, sx] = True; q = deque([seed])
    while q:
        x, y = q.popleft()
        for X, Y in ((x+1, y), (x-1, y), (x, y+1), (x, y-1)):
            if 0 <= X < W and 0 <= Y < H and not seen[Y, X] and not dk[Y, X]:
                seen[Y, X] = True; q.append((X, Y))
    return seen

def hull(pts):
    pts = sorted(set(pts))
    def cross(o, a_, b): return (a_[0]-o[0])*(b[1]-o[1]) - (a_[1]-o[1])*(b[0]-o[0])
    lo, up = [], []
    for p in pts:
        while len(lo) >= 2 and cross(lo[-2], lo[-1], p) <= 0: lo.pop()
        lo.append(p)
    for p in reversed(pts):
        while len(up) >= 2 and cross(up[-2], up[-1], p) <= 0: up.pop()
        up.append(p)
    return lo[:-1] + up[:-1]

# (punto adentro del vidrio, umbral de línea negra) — medidos sobre carro_plateado.png
WINDOWS = {'parabrisas': ((320, 80), 75), 'triangulo_del': ((515, 140), 45), 'puerta_del': ((600, 90), 45), 'puerta_tras': ((750, 75), 45), 'cuarto_tras': ((790, 65), 45)}
mask = Image.new('L', (W, H), 0); d = ImageDraw.Draw(mask)
for name, (seed, thr) in WINDOWS.items():
    r = region(seed, thr); ys, xs = np.nonzero(r)
    hp = hull(list(zip(xs.tolist(), ys.tolist())))
    if name == 'parabrisas':
        ws_mask = Image.new('L', (W, H), 0); ImageDraw.Draw(ws_mask).polygon(hp, fill=255)
    else:
        d.polygon(hp, fill=255)
    print(name, len(xs), 'px', 'x', xs.min(), xs.max(), 'y', ys.min(), ys.max())
# parabrisas: crecer hasta tocar el marco (cubre su orilla clara). La barrera es la línea del marco engrosada,
# y solo se crece el parabrisas, así los otros vidrios no se salen a la carrocería.
frame = np.asarray(Image.fromarray(((L < 80) | (a[..., 3] < 128)).astype('uint8') * 255).filter(ImageFilter.MaxFilter(3))) > 0
ws = np.asarray(ws_mask) > 0
for _ in range(7):
    grown = np.asarray(Image.fromarray(ws.astype('uint8') * 255).filter(ImageFilter.MaxFilter(3))) > 0
    ws = ws | (grown & ~frame)
mk = (np.asarray(mask) > 0) | ws
mask = Image.fromarray(mk.astype('uint8') * 255).filter(ImageFilter.GaussianBlur(0.8))
m = np.asarray(mask).astype(float)[..., None] / 255
rgb = a[..., :3]
tint = np.array([16, 18, 22.0])                      # vidrio humo, un poco frío
dark = rgb * VLT + tint * (1 - VLT)
# brillo suave de reflejo: más claro arriba de cada vidrio, como cielo reflejado
yy = np.linspace(0, 1, H)[:, None, None]
dark = dark + 18 * (1 - yy) ** 2
out = rgb * (1 - m) + dark * m
res = np.dstack([np.clip(out, 0, 255), a[..., 3]]).astype('uint8')
Image.fromarray(res, 'RGBA').save(dst); print('ok', dst)
