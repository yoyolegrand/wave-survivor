import math, random
# Palmier d'oasis v2 (forme d'arbre « Oasis »), visuel validé avant test en jeu.
# Règles importantes pour l'implémentation Java :
#  - Feuilles PERSISTANTES (jungle_leaves[persistent=true]) : sinon elles tombent (trop loin du tronc).
#  - Les palmes doivent être CONTINUES : chaque feuille touche la suivante par une FACE (jamais seulement par un coin),
#    d'où les « blocs de liaison » dans les diagonales et les « marches » quand une palme descend.
#  - Les feuilles ne doivent JAMAIS remplacer un bloc de tronc (ne poser une feuille que si la case est vide).
#  - Noix de coco = cabosses de cacao mûres (cocoa[age=2]) collées au DERNIER bloc de tronc, juste sous la couronne ;
#    « facing » = direction du tronc vu depuis la cabosse (cabosse au sud du tronc -> facing=north).
LEAF='minecraft:jungle_leaves[persistent=true]'
def palm(put, x, y, z, rng, height=None, lean=None):
    """Palmier d'oasis v2 : tronc courbé, couronne fournie, 8 palmes CONTINUES, cabosses de cacao (noix de coco)."""
    h=height or rng.randint(7,10)
    lx,lz=lean or rng.choice(((1,0),(-1,0),(0,1),(0,-1)))
    cx,cz=x,z
    for i in range(h):
        if i>=h//2 and (i-h//2)%2==0 and i>0: cx+=lx; cz+=lz          # s'incline dans la moitié haute
        put(cx,y+i,cz,'minecraft:stripped_jungle_log' if i%3==2 else 'minecraft:jungle_log')
    ty=y+h                                                          # niveau de la couronne
    # cœur de la couronne : 3 x 3 + sommet
    for dx in (-1,0,1):
        for dz in (-1,0,1): put(cx+dx,ty,cz+dz,LEAF)
    put(cx,ty+1,cz,LEAF)
    # 8 palmes : 4 droites (longueur 4) et 4 diagonales (longueur 3), qui retombent
    for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
        prof=[0,0,0,0,-1]                                          # hauteur relative à la distance 1..4
        for s in range(2,5):
            px,pz,py=cx+dx*s,cz+dz*s,ty+prof[s]
            put(px,py,pz,LEAF)
            if prof[s]<prof[s-1]: put(px,py+1,pz,LEAF)             # marche : on garde le contact par une face
        put(cx+dx*4,ty-2,cz+dz*4,LEAF)                              # pointe qui pend (1 feuille)
    for dx,dz in ((1,1),(-1,-1),(1,-1),(-1,1)):
        prof=[0,0,0,-1]
        for s in range(2,4):
            px,pz,py=cx+dx*s,cz+dz*s,ty+prof[s]
            put(px,py,pz,LEAF)
            put(px-dx,py,pz,LEAF)                                   # bloc de liaison (sinon contact par le coin)
            if prof[s]<prof[s-1]: put(px,py+1,pz,LEAF)
        put(cx+dx*3,ty-2,cz+dz*3,LEAF)
    # noix de coco : cabosses de cacao mûres accrochées au tronc, sous la couronne
    for (dx,dz,f) in ((0,1,'north'),(1,0,'west'),(0,-1,'south'),(-1,0,'east')):
        if rng.random()<0.7: put(cx+dx,ty-1,cz+dz,'minecraft:cocoa[age=2,facing=%s]'%f)
    return (cx,ty,cz)
