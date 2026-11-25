# -*- coding: utf-8 -*-
"""从 Go 二进制（language_server.exe）里提取内嵌的 protobuf 文件描述符，导出成 .proto 风格文本。
不依赖 protobuf 库。用法：python -I dump_proto.py <language_server.exe> <输出目录>"""
import os, re, sys

TYPES = {1: 'double', 2: 'float', 3: 'int64', 4: 'uint64', 5: 'int32', 6: 'fixed64', 7: 'fixed32', 8: 'bool',
         9: 'string', 10: 'group', 11: 'message', 12: 'bytes', 13: 'uint32', 14: 'enum', 15: 'sfixed32',
         16: 'sfixed64', 17: 'sint32', 18: 'sint64'}


def varint(b, i):
    r = s = 0
    while True:
        if i >= len(b):
            raise ValueError('eof')
        c = b[i]; i += 1
        r |= (c & 0x7f) << s; s += 7
        if c < 0x80:
            return r, i


def fields(b):
    """把一段 protobuf 字节解析成 [(field_no, wiretype, value)]，遇到非法数据抛错。"""
    if not isinstance(b, (bytes, bytearray)):
        raise ValueError('not bytes')
    i, out = 0, []
    while i < len(b):
        tag, i = varint(b, i)
        no, wt = tag >> 3, tag & 7
        if no == 0:
            raise ValueError('bad tag')
        if wt == 0:
            v, i = varint(b, i)
        elif wt == 2:
            n, i = varint(b, i)
            if i + n > len(b):
                raise ValueError('overflow')
            v = b[i:i + n]; i += n
        elif wt == 1:
            v = b[i:i + 8]; i += 8
        elif wt == 5:
            v = b[i:i + 4]; i += 4
        else:
            raise ValueError('wiretype')
        out.append((no, wt, v))
    return out


def file_extent(data, start):
    """FileDescriptorProto 顶层字段号 1..14；顺着走，直到碰到不像描述符的数据。"""
    i = start
    while True:
        try:
            tag, j = varint(data, i)
        except ValueError:
            return i
        no, wt = tag >> 3, tag & 7
        if not (1 <= no <= 14 and wt in (0, 2)):
            return i
        if wt == 0:
            _, j = varint(data, j)
        else:
            n, j = varint(data, j)
            j += n
        i = j


def s(v):
    return v.decode('utf-8', 'replace')


def dump_enum(b, ind):
    name, vals = '', []
    for no, wt, v in fields(b):
        if no == 1: name = s(v)
        elif no == 2:
            vn = vnum = None
            for n2, w2, v2 in fields(v):
                if n2 == 1: vn = s(v2)
                elif n2 == 2: vnum = v2 if v2 < 2**63 else v2 - 2**64
            vals.append(f'{ind}  {vn} = {vnum};')
    return [f'{ind}enum {name} {{'] + vals + [f'{ind}}}']


def dump_msg(b, ind=''):
    name, flds, nested, enums, oneofs = '', [], [], [], []
    for no, wt, v in fields(b):
        if no == 1: name = s(v)
        elif no == 2: flds.append(v)
        elif no == 3: nested.append(v)
        elif no == 4: enums.append(v)
        elif no == 8:
            oneofs.append(next((s(v2) for n2, w2, v2 in fields(v) if n2 == 1), '?'))
    lines = [f'{ind}message {name} {{']
    for f in flds:
        d = {}
        for n2, w2, v2 in fields(f):
            d[n2] = v2
        t = TYPES.get(d.get(5), '?')
        if d.get(5) in (11, 14):
            t = s(d.get(6, b'')).lstrip('.')
        lab = 'repeated ' if d.get(4) == 3 else ''
        oo = f'  // oneof {oneofs[d[9]]}' if 9 in d and d[9] < len(oneofs) else ''
        json = s(d[10]) if 10 in d else ''
        lines.append(f'{ind}  {lab}{t} {s(d.get(1, b""))} = {d.get(3)};{oo}' + (f'  // json:{json}' if json else ''))
    for e in enums:
        lines += dump_enum(e, ind + '  ')
    for m in nested:
        lines += dump_msg(m, ind + '  ')
    lines.append(f'{ind}}}')
    return lines


def dump_file(b):
    out, pkg = [], ''
    for no, wt, v in fields(b):
        if no == 1: out.append(f'// file: {s(v)}')
        elif no == 2: pkg = s(v); out.append(f'package {pkg};')
        elif no == 3: out.append(f'import "{s(v)}";')
        elif no == 4: out += dump_msg(v)
        elif no == 5: out += dump_enum(v, '')
        elif no == 6:
            sname, meths = '', []
            for n2, w2, v2 in fields(v):
                if n2 == 1: sname = s(v2)
                elif n2 == 2:
                    m = {n3: v3 for n3, w3, v3 in fields(v2)}
                    cs = 'stream ' if m.get(5) else ''
                    ss = 'stream ' if m.get(6) else ''
                    meths.append(f'  rpc {s(m.get(1, b""))}({cs}{s(m.get(2, b"")).lstrip(".")}) returns ({ss}{s(m.get(3, b"")).lstrip(".")});')
            out += [f'service {sname} {{'] + meths + ['}']
    return out


def main():
    data = open(sys.argv[1], 'rb').read()
    outdir = sys.argv[2]
    os.makedirs(outdir, exist_ok=True)
    n = 0
    for m in re.finditer(rb'\x0a([\x05-\x7f])([a-z0-9_/]+\.proto)\x12', data):
        if m.group(1)[0] != len(m.group(2)):
            continue
        name = m.group(2).decode()
        end = file_extent(data, m.start())
        try:
            text = dump_file(data[m.start():end])
        except ValueError as e:
            print('skip', name, e)
            continue
        p = os.path.join(outdir, name.replace('/', '__') + '.txt')
        open(p, 'w', encoding='utf-8').write('\n'.join(text) + '\n')
        n += 1
    print('dumped', n)


main()
