#!/usr/bin/env python3
import os, struct, zipfile, subprocess, shutil, zlib

BASE = os.path.dirname(os.path.abspath(__file__))
ANDROID_JAR = os.path.join(BASE, 'android-35', 'android.jar')
OUT = os.path.join(BASE, 'dist')
BUILD = os.path.join(BASE, 'build')
ANDROID_NS = 'http://schemas.android.com/apk/res/android'

class AXMLWriter:
    def __init__(self):
        self.strings = []
        self.str_map = {}

    def get_str(self, s):
        if s is None: return 0xFFFFFFFF
        if s not in self.str_map:
            self.str_map[s] = len(self.strings)
            self.strings.append(s)
        return self.str_map[s]

    def write_pool(self):
        offsets, data = [], b''
        for s in self.strings:
            offsets.append(len(data))
            enc = s.encode('utf-16-le')
            char_count = len(enc) // 2
            data += struct.pack('<H', char_count) + enc + b'\x00\x00'
        while len(data) % 4:
            data += b'\x00\x00'
        total = 0x1C + len(self.strings) * 4 + len(data)
        ch = struct.pack('<HHI', 0x0001, 0x1C, total)
        ch += struct.pack('<IIIII', len(self.strings), 0, 0x0000,
                          0x1C + len(self.strings) * 4, 0)
        for off in offsets: ch += struct.pack('<I', off)
        ch += data
        return ch

    def write_map(self, ids):
        ch = struct.pack('<HHI', 0x0180, 8, 8 + len(ids) * 4)
        for rid in ids: ch += struct.pack('<I', rid)
        return ch

    def write_tag(self, name, attrs):
        na = len(attrs)
        ch = struct.pack('<HHIii', 0x0102, 16, 36 + na * 20, 0, -1)
        ch += struct.pack('<iI', -1, self.get_str(name))
        ch += struct.pack('<HH', 20, 20) + struct.pack('<H', na)
        ch += struct.pack('<hhh', 0, 0, 0)
        for ns, n, vt, vd, vs in attrs:
            ns_i = 0xFFFFFFFF if ns is None else self.get_str(ns)
            if vt == 0x03:
                str_idx = vs
                ch += struct.pack('<III', ns_i, self.get_str(n), str_idx)
                ch += struct.pack('<HBB', 8, 0, 0x03) + struct.pack('<I', str_idx)
            else:
                vs_i = 0xFFFFFFFF if vs < 0 else vs
                ch += struct.pack('<III', ns_i, self.get_str(n), vs_i)
                ch += struct.pack('<HBB', 8, 0, vt) + struct.pack('<I', vd)
        return ch

    def write_end(self, name):
        return struct.pack('<HHIiiiI', 0x0103, 16, 24, 0, -1, -1, self.get_str(name))

    def write_ns(self, typ, a, b):
        return struct.pack('<HHIiiII', typ, 16, 24, 0, -1, a, b)

    def build(self):
        ATTR_NAMES = ['versionCode', 'versionName', 'compileSdkVersion',
            'compileSdkVersionCodename', 'minSdkVersion', 'targetSdkVersion',
            'allowBackup', 'label', 'name', 'exported',
            'configChanges', 'windowSoftInputMode', 'usesCleartextTraffic', 'icon']
        ATTR_IDS = [0x0101021b, 0x0101021c, 0x01010572, 0x01010573,
                     0x0101020c, 0x01010270, 0x01010280, 0x01010001,
                     0x01010003, 0x01010010, 0x0101009e, 0x010100d3,
                     0x010103ef, 0x01010002]
        for s in ATTR_NAMES: self.get_str(s)
        for s in ['manifest', 'xmlns:android', ANDROID_NS, 'application',
            'activity', 'intent-filter', 'action', 'category',
            'package', 'uses-sdk', 'uses-permission', 'platformBuildVersionCode',
            'platformBuildVersionName',
            '.MainActivity', 'TCC', '1.0.0',
            'android.intent.action.MAIN', 'android.intent.category.LAUNCHER',
            'android', 'orientation|keyboardHidden|screenSize',
            'adjustResize', 'com.tcc', '16',
            'android.permission.INTERNET',
            'android.permission.WRITE_EXTERNAL_STORAGE']: self.get_str(s)

        A = lambda ns, n, vt, vd, vs: (ns, n, vt, vd, vs)
        P = lambda n, vt, vd, vs: A(ANDROID_NS, n, vt, vd, vs)
        B = lambda n, vt, vd, vs: A(None, n, vt, vd, vs)

        pool = self.write_pool()
        rmap = self.write_map(ATTR_IDS)
        ns = self.write_ns(0x0100, self.get_str('android'), self.get_str(ANDROID_NS))
        hdr = (pool + rmap + ns +
            self.write_tag('manifest', [B('package', 0x03, -1, self.get_str('com.tcc')),
                P('versionCode', 0x10, 1, -1),
                P('versionName', 0x03, -1, self.get_str('1.0.0')),
                P('compileSdkVersion', 0x10, 35, -1),
                P('compileSdkVersionCodename', 0x03, -1, self.get_str('16')),
                B('platformBuildVersionCode', 0x10, 35, -1),
                B('platformBuildVersionName', 0x03, -1, self.get_str('16'))]) +
            self.write_tag('uses-sdk', [P('minSdkVersion', 0x10, 26, -1),
                P('targetSdkVersion', 0x10, 28, -1)]) + self.write_end('uses-sdk') +
            self.write_tag('uses-permission', [P('name', 0x03, -1,
                self.get_str('android.permission.INTERNET'))]) + self.write_end('uses-permission') +
            self.write_tag('uses-permission', [P('name', 0x03, -1,
                self.get_str('android.permission.WRITE_EXTERNAL_STORAGE'))]) + self.write_end('uses-permission') +
            self.write_tag('application', [P('allowBackup', 0x12, 0, -1),
                P('label', 0x03, -1, self.get_str('TCC')),
                P('usesCleartextTraffic', 0x12, 0xFFFFFFFF, -1)]) +
            self.write_tag('activity', [P('name', 0x03, -1, self.get_str('.MainActivity')),
                P('label', 0x03, -1, self.get_str('TCC')),
                P('exported', 0x12, 0xFFFFFFFF, -1),
                P('configChanges', 0x03, -1, self.get_str('orientation|keyboardHidden|screenSize')),
                P('windowSoftInputMode', 0x03, -1, self.get_str('adjustResize'))]) +
            self.write_tag('intent-filter', []) +
            self.write_tag('action', [P('name', 0x03, -1,
                self.get_str('android.intent.action.MAIN'))]) + self.write_end('action') +
            self.write_tag('category', [P('name', 0x03, -1,
                self.get_str('android.intent.category.LAUNCHER'))]) +
            self.write_end('category') + self.write_end('intent-filter') +
            self.write_end('activity') + self.write_end('application') +
            self.write_end('manifest') +
            self.write_ns(0x0101, self.get_str('android'), self.get_str(ANDROID_NS)))
        return struct.pack('<HHI', 0x0003, 8, 8 + len(hdr)) + hdr

def gen_resources_arsc():
    """Minimal resources.arsc: maps 0x7F020000 → res/mipmap-hdpi-v4/ic_launcher.png"""
    def str_pool(strings):
        encoded = []; offsets = []; data = b''
        for s in strings:
            offsets.append(len(data))
            enc = s.encode('utf-16-le')
            data += struct.pack('<H', len(enc)//2) + enc + b'\x00\x00'
        while len(data) % 4: data += b'\x00\x00'
        h = 0x1C
        t = h + len(strings) * 4 + len(data)
        c = struct.pack('<HHI', 0x0001, h, t)
        c += struct.pack('<IIIII', len(strings), 0, 0, h + len(strings) * 4, 0)
        for o in offsets: c += struct.pack('<I', o)
        return c + data

    buf = bytearray()
    # 1) ResTable header
    table_start = len(buf)
    buf += bytearray(struct.pack('<HHI', 0x0002, 8, 0))
    # 2) Global string pool: icon file path (index 0)
    global_pool = str_pool(['res/mipmap-hdpi-v4/ic_launcher.png'])
    buf += global_pool
    # 3) Package chunk
    pkg_start = len(buf)
    buf += bytearray(struct.pack('<HHI', 0x0200, 288, 0))  # headerSize = sizeof(ResTable_package)
    buf += bytearray(struct.pack('<I', 0x7F))  # package id
    name_enc = b'com.tcc\x00\x00'
    buf += name_enc + b'\x00' * (256 - len(name_enc))  # package name UTF-16LE
    # typeStrings(4), lastPublicType(4), keyStrings(4), lastPublicKey(4), typeIdOffset(4)
    offsets_pos = len(buf)
    buf += bytearray(b'\x00' * 20)
    # Type string pool: ["mipmap"]
    type_pool_off = len(buf) - pkg_start
    buf += str_pool(['mipmap'])
    # Key string pool: ["ic_launcher"]
    key_pool_off = len(buf) - pkg_start
    buf += str_pool(['ic_launcher'])
    # Patch package offsets
    struct.pack_into('<I', buf, offsets_pos, type_pool_off)
    struct.pack_into('<I', buf, offsets_pos + 4, 1)   # lastPublicType
    struct.pack_into('<I', buf, offsets_pos + 8, key_pool_off)
    struct.pack_into('<I', buf, offsets_pos + 12, 1)  # lastPublicKey
    # TypeSpec: type 0x0202, id=2, 1 entry, no config flags
    buf += bytearray(struct.pack('<HHI', 0x0202, 16, 20))
    buf += bytearray(struct.pack('<BBHI', 2, 0, 0, 1))  # id=2, res0=0, res1=0, entryCount=1
    buf += bytearray(struct.pack('<I', 0))  # flags[0]=0
    # Type: type 0x0201, id=2, 1 entry, default config
    # headerSize = 20 + 28(config) = 48, entriesStart = 48 + 1*4 = 52, total = 52 + 20 = 72
    buf += bytearray(struct.pack('<HHI', 0x0201, 48, 72))
    buf += bytearray(struct.pack('<BBH', 2, 0, 0))  # id=2, res0=0, res1=0
    buf += bytearray(struct.pack('<II', 1, 52))  # entryCount=1, entriesStart=52
    # default config (28 bytes, all zeros)
    buf += bytearray(struct.pack('<I', 28) + b'\x00' * 24)
    # entry offset[0] = 0
    buf += bytearray(struct.pack('<I', 0))
    # ResTable_entry: size=20(header+value), flags=0(=simple), ref=-1, key=0 (=ic_launcher)
    buf += bytearray(struct.pack('<HHiI', 20, 0, -1, 0))
    # Res_value: size=8, res0=0, dataType=3(=STRING), data=0 (=global pool idx)
    buf += bytearray(struct.pack('<HBBI', 8, 0, 3, 0))
    # Patch sizes
    struct.pack_into('<I', buf, 4, len(buf))           # ResTable total
    struct.pack_into('<I', buf, pkg_start + 4, len(buf) - pkg_start)  # package total
    return bytes(buf)

def gen_icon():
    def chunk(t, d):
        c = t + d
        return struct.pack('>I', len(d)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    size = 96
    raw = b''
    for y in range(size):
        raw += b'\x00'
        for x in range(size):
            dx, dy = x - size//2, y - size//2
            d = (dx*dx + dy*dy) / ((size//2)*(size//2))
            r = int(0x6C * (1-d) + 0x2D * d)
            g = int(0x5C * (1-d) + 0x1B * d)
            b = int(0xE7 * (1-d) + 0x69 * d)
            raw += struct.pack('BBB', max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b)))
    ihdr = chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 2, 0, 0, 0))
    idat = chunk(b'IDAT', zlib.compress(raw))
    iend = chunk(b'IEND', b'')
    return b'\x89PNG\r\n\x1a\n' + ihdr + idat + iend

def build():
    os.makedirs(OUT, exist_ok=True); os.makedirs(BUILD, exist_ok=True)
    src = []
    for r, d, f in os.walk(os.path.join(BASE, 'src')):
        for fn in f:
            if fn.endswith('.kt'): src.append(os.path.join(r, fn))
    if not src: print("No Kotlin sources!"); return

    xzjar = os.path.join(BASE, 'lib', 'xz-1.9.jar')
    print(f"  Compiling {len(src)} Kotlin files...")
    cls = os.path.join(BUILD, 'classes')
    if os.path.exists(cls): shutil.rmtree(cls)
    os.makedirs(cls, exist_ok=True)
    subprocess.run(['kotlinc', '-cp', f'{ANDROID_JAR}:{xzjar}', '-d', cls] + src,
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    print("  Converting to DEX...")
    dx = os.path.join(BUILD, 'dex')
    if os.path.exists(dx): shutil.rmtree(dx)
    os.makedirs(dx, exist_ok=True)
    cf = []
    for r, d, f in os.walk(cls):
        for fn in f:
            if fn.endswith('.class'): cf.append(os.path.join(r, fn))
    kt = '/data/data/com.termux/files/usr/opt/kotlin/lib/kotlin-stdlib.jar'
    subprocess.run(['d8', '--output', dx, '--min-api', '26',
                    '--lib', ANDROID_JAR] + cf + [kt, xzjar],
                   check=True, stderr=subprocess.DEVNULL)

    print("  Generating AndroidManifest.xml...")
    axml = AXMLWriter().build()
    with open(os.path.join(BUILD, 'AndroidManifest.xml'), 'wb') as f:
        f.write(axml)

    print("  Creating APK...")
    apk = os.path.join(OUT, 'TCC.apk')

    tar_path = os.path.join(BASE, 'assets', 'termux-bundle.tar')
    xz_path = tar_path + '.xz'
    if os.path.exists(tar_path):
        if not os.path.exists(xz_path) or os.path.getmtime(tar_path) > os.path.getmtime(xz_path):
            subprocess.run(['xz', '-z', '-e', '-T0', '-k', '-f', tar_path], check=True)
        bundle_file = xz_path
        bundle_asset = 'assets/termux-bundle.tar.xz'
    elif os.path.exists(xz_path):
        bundle_file = xz_path
        bundle_asset = 'assets/termux-bundle.tar.xz'
    else:
        bundle_file = None

    entries = []
    entries.append(('AndroidManifest.xml',
        open(os.path.join(BUILD, 'AndroidManifest.xml'), 'rb').read(), zipfile.ZIP_STORED))
    dex_path = os.path.join(dx, 'classes.dex')
    if os.path.exists(dex_path):
        entries.append(('classes.dex', open(dex_path, 'rb').read(), zipfile.ZIP_STORED))
    if bundle_file:
        entries.append((bundle_asset, open(bundle_file, 'rb').read(), zipfile.ZIP_STORED))
    icon = gen_icon()
    entries.append(('res/mipmap-anydpi-v26/ic_launcher.png', icon, zipfile.ZIP_STORED))
    entries.append(('res/mipmap-hdpi-v4/ic_launcher.png', icon, zipfile.ZIP_STORED))
    # glibc exec 转发器（用于绕过 SELinux 运行 claude 等非 PIE 二进制）
    exec_wrapper = os.path.join(BASE, 'assets', 'exec_glibc')
    if os.path.exists(exec_wrapper):
        entries.append(('assets/exec_glibc', open(exec_wrapper, 'rb').read(), zipfile.ZIP_STORED))
    with zipfile.ZipFile(apk, 'w', zipfile.ZIP_DEFLATED) as z:
        for arcname, data, ct in entries:
            zi = zipfile.ZipInfo(arcname)
            zi.compress_type = ct
            z.writestr(zi, data)

    print("  Signing...")
    ks = os.path.join(BASE, 'debug.keystore')
    if not os.path.exists(ks):
        subprocess.run(['keytool', '-genkey', '-v', '-keystore', ks, '-alias', 'debug',
            '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
            '-storepass', 'android', '-keypass', 'android',
            '-dname', 'CN=TCC, OU=Dev, O=AI, L=Unknown, ST=Unknown, C=CN'])
    sp = os.path.join(OUT, 'TCC-signed.apk')
    subprocess.run(['apksigner', 'sign', '--min-sdk-version', '26',
        '--ks', ks, '--ks-pass', 'pass:android', '--ks-key-alias', 'debug',
        '--key-pass', 'pass:android', '--out', sp, apk])
    shutil.move(sp, apk)
    sz = os.path.getsize(apk)
    print(f"\n  TCC APK build complete!  Size: {sz//1024} KB  Package: com.tcc\n")

if __name__ == '__main__':
    build()
