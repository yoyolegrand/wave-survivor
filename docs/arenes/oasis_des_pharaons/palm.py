import math, random
# Palmier d'oasis (forme d'arbre « Oasis »). IMPORTANT : les feuilles sont posées PERSISTANTES
# (jungle_leaves[persistent=true]) : les palmes sont longues et le tronc est courbé, des feuilles
# naturelles seraient à plus de 6 blocs d'un tronc et tomberaient (bug vu en jeu).
def palm(put, x, y, z, rng, height=None, lean=None):
    """Palmier d'oasis : tronc courbé en bois de jungle (anneaux écorcés), couronne de 8 palmes tombantes, noix de coco."""
    h=height or rng.randint(6,9)
    lx,lz=lean or rng.choice(((1,0),(-1,0),(0,1),(0,-1)))
    cx,cz=x,z
    for i in range(h):
        if i>=h//2 and i%2==0: cx+=lx; cz+=lz                     # le tronc s'incline vers le haut
        put(cx,y+i,cz,'minecraft:stripped_jungle_log' if i%3==1 else 'minecraft:jungle_log')
    ty=y+h; put(cx,ty,cz,'minecraft:jungle_leaves[persistent=true]')
    for dx,dz in ((1,0),(-1,0),(0,1),(0,-1),(1,1),(-1,-1),(1,-1),(-1,1)):   # 8 palmes : montent puis retombent
        for s in range(1,5):
            px,pz=cx+dx*s,cz+dz*s
            py=ty+(1 if s==1 else 0)-(1 if s>=3 else 0)-(1 if s==4 else 0)
            put(px,py,pz,'minecraft:jungle_leaves[persistent=true]')
    for dx,dz in ((1,0),(0,1),(-1,0)):                             # noix de coco sous la couronne
        put(cx+dx,ty-1,cz+dz,'minecraft:brown_terracotta' if rng.random()<0.6 else 'minecraft:air')
    return (cx,ty,cz)
