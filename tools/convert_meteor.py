"""Convert Blockbench cuboid OBJ exports to Java 1.21.11 models (no mesh approximation)."""
import argparse, itertools, json, math
from pathlib import Path

def sub(a,b): return [x-y for x,y in zip(a,b)]
def dot(a,b): return sum(x*y for x,y in zip(a,b))
def length(a): return math.sqrt(dot(a,a))
def convert(source):
    vertices=[]; uv=[]; groups=[]; group=None
    for line in Path(source).read_text().splitlines():
        words=line.split()
        if not words: continue
        if words[0]=='o':
            group={'vertices':[], 'faces':[]};groups.append(group)
        elif words[0]=='v':
            vertices.append(list(map(float,words[1:4])));group['vertices'].append(len(vertices)-1)
        elif words[0]=='vt': uv.append([float(words[1])*16,(1-float(words[2]))*16])
        elif words[0]=='f': group['faces'].append([tuple(int(x)-1 for x in w.split('/')) for w in words[1:]])
    center=[(min(v[i] for v in vertices)+max(v[i] for v in vertices))/2 for i in range(3)]
    # Normalize the largest extent to 32 model units (2 blocks).
    scale=32/max(max(v[i] for v in vertices)-min(v[i] for v in vertices) for i in range(3))
    elements=[];max_error=0
    # Face vertices in vanilla UV order: top-left, bottom-left, bottom-right, top-right.
    corners={'north':[2,3,1,0], 'south':[5,4,6,7], 'west':[5,0,1,4],
             'east':[2,7,6,3], 'up':[5,7,2,0], 'down':[1,3,6,4]}
    for g in groups:
        assert len(g['vertices'])==8 and len(g['faces'])==12, 'Not a cuboid export'
        v=[vertices[i] for i in g['vertices']]
        edges=[sub(v[2],v[0]),sub(v[0],v[1]),sub(v[5],v[0])]
        dims=[length(e) for e in edges];assert min(dims)>0
        axes=[[c/d for c in e] for e,d in zip(edges,dims)]
        assert max(abs(dot(axes[i],axes[j])) for i,j in [(0,1),(0,2),(1,2)])<1e-5, 'Sheared cube'
        matrix=[[axes[j][i] for j in range(3)] for i in range(3)]
        y=math.asin(max(-1,min(1,-matrix[2][0])))
        if abs(math.cos(y))>1e-6:
            x=math.atan2(matrix[2][1],matrix[2][2]);z=math.atan2(matrix[1][0],matrix[0][0])
        else: x=math.atan2(-matrix[1][2],matrix[1][1]);z=0
        origin=[(sum(a[i] for a in v)/8-center[i])*scale+8 for i in range(3)]
        half=[d*scale/2 for d in dims]
        element={'from':[origin[i]-half[i] for i in range(3)],'to':[origin[i]+half[i] for i in range(3)],
                 'rotation':{'origin':origin,'x':math.degrees(x),'y':math.degrees(y),'z':math.degrees(z)}, 'faces':{}}
        # Verify all reconstructed corners against source geometry.
        for point in v:
            local=[dot(sub(point, [sum(a[i] for a in v)/8 for i in range(3)]),axis) for axis in axes]
            error=max(abs(abs(local[i])-dims[i]/2) for i in range(3))
            max_error=max(error,max_error);assert error<1e-5
        for face_index,(name,order) in enumerate(corners.items()):
            mapping={index:uv[texture] for triangle in g['faces'][face_index*2:face_index*2+2] for index,texture,*_ in triangle}
            wanted=[mapping[g['vertices'][i]] for i in order]
            us=[p[0] for p in wanted];vs=[p[1] for p in wanted]
            found=None
            for flip_u,flip_v,rotation in itertools.product([False,True],[False,True],range(4)):
                u0,u1=(max(us),min(us)) if flip_u else (min(us),max(us))
                v0,v1=(max(vs),min(vs)) if flip_v else (min(vs),max(vs))
                base=[[u0,v0],[u0,v1],[u1,v1],[u1,v0]]
                if all(max(abs(base[(i+rotation)%4][k]-wanted[i][k]) for k in range(2))<1e-5 for i in range(4)):
                    found={'uv':[u0,v0,u1,v1],'texture':'#meteor','rotation':rotation*90};break
            assert found is not None, 'Non-rectangular UV mapping'
            element['faces'][name]=found
        elements.append(element)
    return {'credit':'Converted from supplied meteor.obj', 'textures':{'meteor':'skysignals:item/meteor','particle':'skysignals:item/meteor'},'elements':elements}, max_error

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('source');parser.add_argument('output');args=parser.parse_args()
    model,error=convert(args.source);target=Path(args.output);target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps(model,indent=2)+'\n')
    print(f"Converted {len(model['elements'])} cuboids; maximum geometry error: {error:.10g}")
