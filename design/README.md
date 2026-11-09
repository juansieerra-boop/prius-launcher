# Diseño del launcher

Todo lo necesario para seguir actualizando el diseño.

- `originales/` — las imágenes que mandó Juan Diego (Prius plateado, Anatomy oscuro, Jeep, referencias).
- `assets/` — los carros ya recortados (909x454, fondo transparente). Son la fuente de
  `app/src/main/res/drawable-nodpi/car_main.webp` y `anat_main.webp`.
- `renders/` — los renders y videos aprobados (estilo Apple día/noche, Sport, carretera, rines).
- `scripts/` — los scripts de Python que hicieron los renders:
  - `carplay.py` / `carplay_day.py`: pantalla estilo Apple (noche / día).
  - `floor2.py <night|day|sport> <carpeta>`: carretera HUD + rines + luces, cuadro por cuadro.
  - `rim.py`: el rin dibujado (5 rayos en Y) que gira.
  - `hud_min2.py`: pantalla Sport minimalista.

Medidas del carro (en el dibujo de 909x454):
- Rines (cx, cy, rx, ry, inclinación): (444,364,41,61,6) y (844,275,24,46,6)
- Faros (cx, cy, rx, ry): (306,250,14,12) (258,256,18,10) (36,210,7,14) niebla (290,343,12,12)
- Piso bajo la llanta delantera (carretera): (440,446)

Estado (8 nov 2026): versión 3 del Prius entregada (estilo Apple + Sport). El Jeep todavía no tiene el diseño nuevo.
