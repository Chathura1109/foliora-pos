import os, re
dir_path = r'e:\Mobile Apps\Foliora\app\src\main\java\com\foliora\pos\data\local\entity'
for filename in os.listdir(dir_path):
    if not filename.endswith('Entity.kt'): continue
    filepath = os.path.join(dir_path, filename)
    with open(filepath, 'r', encoding='utf-8') as f: lines = f.readlines()
    new_lines = []
    for line in lines:
        if re.search(r'^\s*(val|var)\s+', line) and '=' not in line.split('//')[0]:
            m = re.search(r':\s*([A-Za-z0-9_]+)(\?)?', line)
            if m:
                t, q = m.group(1), m.group(2)
                d = 'null' if q else '""' if t=='String' else '0.0' if t=='Double' else '0' if t in ('Int','Long') else 'false' if t=='Boolean' else 'null'
                line = re.sub(r'(:[a-zA-Z0-9_ ?]+)', r'\1 = ' + d, line, count=1)
        new_lines.append(line)
    with open(filepath, 'w', encoding='utf-8') as f: f.writelines(new_lines)
print('Defaults added!')
