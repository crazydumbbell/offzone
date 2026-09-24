"""Lossless geometry conversion of the eight existing iOS SVG assets to VectorDrawable."""
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[2]
NS = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', NS)
for source in (ROOT/'RoomDNS/Assets.xcassets').glob('OffzoneKiwi-*.imageset/*.svg'):
    vector = ET.Element('vector', {f'{{{NS}}}{k}':v for k,v in {'width':'240dp','height':'240dp','viewportWidth':'240','viewportHeight':'240'}.items()})
    def walk(element, inherited):
        attrs = inherited | element.attrib
        tag = element.tag.split('}')[-1]
        if tag in ('svg','g'):
            for child in element: walk(child,attrs)
            return
        if tag == 'path': data = attrs['d']
        elif tag in ('circle','ellipse'):
            cx,cy = float(attrs['cx']),float(attrs['cy'])
            rx,ry = (float(attrs['r']),)*2 if tag=='circle' else (float(attrs['rx']),float(attrs['ry']))
            data=f'M {cx-rx},{cy} a {rx},{ry} 0 1,0 {2*rx},0 a {rx},{ry} 0 1,0 {-2*rx},0 Z'
        else: raise ValueError(tag)
        props={'pathData':data,'fillColor':attrs.get('fill','#000000')}
        if props['fillColor']=='none': props['fillColor']='#00000000'
        if attrs.get('stroke','none')!='none':
            props.update(strokeColor=attrs['stroke'],strokeWidth=attrs.get('stroke-width','1'),strokeLineCap=attrs.get('stroke-linecap','butt'),strokeLineJoin=attrs.get('stroke-linejoin','miter'))
        ET.SubElement(vector,'path',{f'{{{NS}}}{k}':v for k,v in props.items()})
    walk(ET.parse(source).getroot(),{})
    target=ROOT/'android/app/src/main/res/drawable'/('kiwi_'+source.stem.replace('-','_')+'.xml')
    ET.indent(vector); ET.ElementTree(vector).write(target,encoding='utf-8',xml_declaration=True)
    print(target.name)
