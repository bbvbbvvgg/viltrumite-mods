"""Minimal fast NBT codec and Anvil region reader/writer (no dependencies).

Values are decoded to tagged Python objects:
  compound -> Compound (dict subclass)
  list     -> List (list subclass with .tag = element type id)
  arrays   -> ByteArray/IntArray/LongArray wrappers over raw big-endian bytes
  scalars  -> Byte/Short/Int/Long/Float/Double (int/float subclasses)
  string   -> str
Round-trip is byte exact for unmodified data (key order preserved).
"""
import struct, zlib, os

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTEARR, STRING, LIST, COMPOUND, INTARR, LONGARR = range(13)

class Byte(int): tag = BYTE
class Short(int): tag = SHORT
class Int(int): tag = INT
class Long(int): tag = LONG
class Float(float): tag = FLOAT
class Double(float): tag = DOUBLE

class _Arr:
    __slots__ = ('raw',)
    width = 1
    fmt = 'b'
    def __init__(self, raw=b''):
        self.raw = bytes(raw)
    def __len__(self):
        return len(self.raw) // self.width
    def values(self):
        return struct.unpack('>%d%s' % (len(self), self.fmt), self.raw)
    @classmethod
    def of(cls, vals):
        return cls(struct.pack('>%d%s' % (len(vals), cls.fmt), *vals))
    def __eq__(self, o):
        return type(self) is type(o) and self.raw == o.raw
    def __repr__(self):
        return '%s[%d]' % (type(self).__name__, len(self))

class ByteArray(_Arr): tag = BYTEARR; width = 1; fmt = 'b'
class IntArray(_Arr): tag = INTARR; width = 4; fmt = 'i'
class LongArray(_Arr): tag = LONGARR; width = 8; fmt = 'q'

class List(list):
    def __init__(self, it=(), tag=END):
        super().__init__(it)
        self.tag = tag

class Compound(dict):
    pass

def tag_of(v):
    if isinstance(v, List): return LIST
    t = getattr(v, 'tag', None)
    if t is not None: return t
    if isinstance(v, dict): return COMPOUND
    if isinstance(v, str): return STRING
    if isinstance(v, list): return LIST
    if isinstance(v, bool): return BYTE
    if isinstance(v, int): return INT
    if isinstance(v, float): return DOUBLE
    raise TypeError(type(v))

_u = struct.unpack_from

def _read(buf, pos, t):
    if t == COMPOUND:
        out = Compound()
        while True:
            ct = buf[pos]; pos += 1
            if ct == 0:
                return out, pos
            ln = (buf[pos] << 8) | buf[pos + 1]; pos += 2
            name = _dec(buf[pos:pos + ln]); pos += ln
            out[name], pos = _read(buf, pos, ct)
    if t == STRING:
        ln = (buf[pos] << 8) | buf[pos + 1]; pos += 2
        return _dec(buf[pos:pos + ln]), pos + ln
    if t == INT: return Int(_u('>i', buf, pos)[0]), pos + 4
    if t == BYTE: return Byte(_u('>b', buf, pos)[0]), pos + 1
    if t == LIST:
        et = buf[pos]; n = _u('>i', buf, pos + 1)[0]; pos += 5
        out = List(tag=et)
        for _ in range(n):
            v, pos = _read(buf, pos, et); out.append(v)
        return out, pos
    if t == LONGARR:
        n = _u('>i', buf, pos)[0]; pos += 4
        return LongArray(buf[pos:pos + 8 * n]), pos + 8 * n
    if t == SHORT: return Short(_u('>h', buf, pos)[0]), pos + 2
    if t == LONG: return Long(_u('>q', buf, pos)[0]), pos + 8
    if t == FLOAT: return Float(_u('>f', buf, pos)[0]), pos + 4
    if t == DOUBLE: return Double(_u('>d', buf, pos)[0]), pos + 8
    if t == BYTEARR:
        n = _u('>i', buf, pos)[0]; pos += 4
        return ByteArray(buf[pos:pos + n]), pos + n
    if t == INTARR:
        n = _u('>i', buf, pos)[0]; pos += 4
        return IntArray(buf[pos:pos + 4 * n]), pos + 4 * n
    raise ValueError('bad tag %d at %d' % (t, pos))

def _dec(b):
    try:
        return b.decode('utf-8')
    except UnicodeDecodeError:
        # Java modified UTF-8: keep surrogates so the string round-trips
        out = []; i = 0
        while i < len(b):
            c = b[i]
            if c < 0x80: out.append(c); i += 1
            elif c & 0xE0 == 0xC0: out.append(((c & 0x1F) << 6) | (b[i+1] & 0x3F)); i += 2
            else: out.append(((c & 0x0F) << 12) | ((b[i+1] & 0x3F) << 6) | (b[i+2] & 0x3F)); i += 3
        return ''.join(map(chr, out))

def _enc_str(s):
    try:
        b = s.encode('utf-8')
        if b'\x00' not in b and all(ord(ch) <= 0xFFFF for ch in s):
            return struct.pack('>H', len(b)) + b
    except UnicodeEncodeError:
        pass
    out = bytearray()
    for ch in s:
        cp = ord(ch)
        units = [cp] if cp <= 0xFFFF else [0xD800 + ((cp - 0x10000) >> 10), 0xDC00 + ((cp - 0x10000) & 0x3FF)]
        for u in units:
            if 0 < u < 0x80: out.append(u)
            elif u < 0x800: out += bytes((0xC0 | (u >> 6), 0x80 | (u & 0x3F)))
            else: out += bytes((0xE0 | (u >> 12), 0x80 | ((u >> 6) & 0x3F), 0x80 | (u & 0x3F)))
    return struct.pack('>H', len(out)) + bytes(out)

def _write(out, v, t):
    if t == COMPOUND:
        for k, cv in v.items():
            ct = tag_of(cv)
            out.append(bytes((ct,))); out.append(_enc_str(k)); _write(out, cv, ct)
        out.append(b'\x00')
    elif t == STRING: out.append(_enc_str(v))
    elif t == INT: out.append(struct.pack('>i', v))
    elif t == BYTE: out.append(struct.pack('>b', v))
    elif t == LIST:
        et = v.tag if isinstance(v, List) else END
        if v and et == END: et = tag_of(v[0])
        out.append(struct.pack('>bi', et, len(v)))
        for e in v: _write(out, e, et)
    elif t in (LONGARR, BYTEARR, INTARR):
        out.append(struct.pack('>i', len(v))); out.append(v.raw)
    elif t == SHORT: out.append(struct.pack('>h', v))
    elif t == LONG: out.append(struct.pack('>q', v))
    elif t == FLOAT: out.append(struct.pack('>f', v))
    elif t == DOUBLE: out.append(struct.pack('>d', v))
    else: raise ValueError(t)

def loads(data):
    """Parse an uncompressed root compound. Returns (name, Compound)."""
    assert data[0] == COMPOUND, data[0]
    ln = (data[1] << 8) | data[2]
    v, _ = _read(data, 3 + ln, COMPOUND)
    return _dec(data[3:3 + ln]), v

def dumps(v, name=''):
    out = [b'\x0a', _enc_str(name)]
    _write(out, v, COMPOUND)
    return b''.join(out)

def load_file(path):
    import gzip
    with open(path, 'rb') as f:
        data = f.read()
    if data[:2] == b'\x1f\x8b':
        data = gzip.decompress(data)
    return loads(data)[1]

def save_file(path, v, gz=True):
    import gzip
    data = dumps(v)
    if gz:
        data = gzip.compress(data, mtime=0)
    tmp = path + '.tmp'
    with open(tmp, 'wb') as f:
        f.write(data)
    os.replace(tmp, path)

# ---------------- Anvil regions ----------------

def read_region(path):
    """Yield (local_index, timestamp, raw_uncompressed_nbt) for every chunk."""
    with open(path, 'rb') as f:
        data = f.read()
    if len(data) < 8192:
        return
    for i in range(1024):
        off = (data[i*4] << 16) | (data[i*4+1] << 8) | data[i*4+2]
        cnt = data[i*4+3]
        if off == 0 or cnt == 0:
            continue
        ts = _u('>I', data, 4096 + i*4)[0]
        p = off * 4096
        if p + 5 > len(data):
            continue
        ln = _u('>I', data, p)[0]
        comp = data[p + 4]
        payload = data[p + 5:p + 4 + ln]
        if comp & 0x80:
            raise ValueError('external .mcc chunk not supported: %s %d' % (path, i))
        if comp == 2: raw = zlib.decompress(payload)
        elif comp == 1:
            import gzip; raw = gzip.decompress(payload)
        elif comp == 3: raw = payload
        else:
            raise ValueError('compression %d in %s' % (comp, path))
        yield i, ts, raw

def write_region(path, chunks, level=6):
    """chunks: iterable of (local_index, timestamp, raw_uncompressed_nbt)."""
    header = bytearray(8192)
    body = bytearray()
    sector = 2
    for i, ts, raw in sorted(chunks, key=lambda c: c[0]):
        comp = zlib.compress(raw, level)
        blob = struct.pack('>IB', len(comp) + 1, 2) + comp
        blob += b'\x00' * ((-len(blob)) % 4096)
        n = len(blob) // 4096
        if n > 255:
            raise ValueError('chunk %d too large (%d sectors)' % (i, n))
        struct.pack_into('>I', header, i * 4, (sector << 8) | n)
        struct.pack_into('>I', header, 4096 + i * 4, ts)
        body += blob
        sector += n
    tmp = path + '.tmp'
    with open(tmp, 'wb') as f:
        f.write(header); f.write(body)
    os.replace(tmp, path)
