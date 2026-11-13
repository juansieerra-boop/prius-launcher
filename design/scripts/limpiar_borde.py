# Quita la orilla blanca (halo) que deja el recorte alrededor del carro: en fondo oscuro se veía
# como una línea delgada clara. Medido: afuera de la línea negra del dibujo hay pixeles blancos (255)
# semitransparentes (Prius: 1 px; Jeep: hasta 8 px de brillo). Se borran solo esos pixeles:
# los que están afuera de la línea negra, son casi blancos y no son opacos. La línea negra y la carrocería no se tocan.
# Uso: python3 limpiar_borde.py entrada salida
import sys, numpy as np
from PIL import Image
from scipy import ndimage
src, dst = sys.argv[1], sys.argv[2]
a = np.asarray(Image.open(src).convert('RGBA')).copy()
al = a[..., 3].astype(int)
L = a[..., :3].astype(int).mean(-1)
halo = ((al < 235) & (L >= 225)) | ((al < 200) & (L >= 150)) | ((al < 150) & (L >= 80))
# solo el halo conectado al exterior (no toca reflejos blancos adentro del carro)
outside = (al == 0) | halo
lab, _ = ndimage.label(outside)
ext = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
halo &= np.isin(lab, list(ext))
a[..., 3][halo] = 0
# orilla que queda (semitransparente): toma el color del pixel opaco más cercano si es más oscuro
# (así la orilla es la misma línea del dibujo y no una raya gris)
al2 = a[..., 3].astype(int)
semi = (al2 > 0) & (al2 < 250)
_, (iy, ix) = ndimage.distance_transform_edt(al2 < 250, return_indices=True)
near = a[..., :3][iy, ix].astype(int)
cur = a[..., :3].astype(int)
darker = semi & (near.mean(-1) < cur.mean(-1))
a[..., :3][darker] = near[darker].astype(np.uint8)
Image.fromarray(a, 'RGBA').save(dst, lossless=True) if dst.endswith('.webp') else Image.fromarray(a, 'RGBA').save(dst)
print('ok', dst, int(halo.sum()), 'px de halo')
