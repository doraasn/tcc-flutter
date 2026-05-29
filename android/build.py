#!/usr/bin/env python3
import os, struct, zipfile, subprocess, shutil

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
        # UTF-16 string pool: each entry = charCount(2) + utf16le_data(2*count) + null(2)
        offsets, data = [], b''
        for s in self.strings:
            offsets.append(len(data))
            enc = s.encode('utf-16-le')
            char_count = len(enc) // 2  # number of UTF-16 code units
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
            if vt == 0x03:  # string type: rawValue AND typedData are both string index
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
        # CRITICAL: All android-namespace ATTRIBUTE NAME strings must come FIRST
        # (before element names, values, etc.) so RES_MAP entries align correctly
        # by string pool index.
        ATTR_NAMES = ['versionCode', 'versionName', 'compileSdkVersion',
            'compileSdkVersionCodename', 'minSdkVersion', 'targetSdkVersion',
            'allowBackup', 'label', 'name', 'exported',
            'configChanges', 'windowSoftInputMode', 'usesCleartextTraffic']
        ATTR_IDS = [0x0101021b, 0x0101021c, 0x01010572, 0x01010573,
                     0x0101020c, 0x01010270, 0x01010280, 0x01010001,
                     0x01010003, 0x01010010, 0x0101009e, 0x010100d3,
                     0x010103ef]
        # Register attribute names first
        for s in ATTR_NAMES: self.get_str(s)
        # Then remaining strings (element names, values, namespace, etc.)
        for s in ['manifest', 'xmlns:android', ANDROID_NS, 'application',
            'activity', 'intent-filter', 'action', 'category',
            'package', 'uses-sdk', 'platformBuildVersionCode',
            'platformBuildVersionName',
            '.MainActivity', 'MCC', '1.0.0',
            'android.intent.action.MAIN', 'android.intent.category.LAUNCHER',
            'android', 'orientation|keyboardHidden|screenSize',
            'adjustResize', 'com.tcc', '16']: self.get_str(s)

        # P = android-namespace attr, B = bare attr (no namespace)
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
                P('targetSdkVersion', 0x10, 35, -1)]) + self.write_end('uses-sdk') +
            self.write_tag('application', [P('allowBackup', 0x12, 0, -1),
                P('label', 0x03, -1, self.get_str('MCC')),
                P('usesCleartextTraffic', 0x12, 0xFFFFFFFF, -1)]) +
            self.write_tag('activity', [P('name', 0x03, -1, self.get_str('.MainActivity')),
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

# Build APK
def build():
    os.makedirs(OUT, exist_ok=True); os.makedirs(BUILD, exist_ok=True)
    src = []
    for r, d, f in os.walk(os.path.join(BASE, 'src')):
        for fn in f:
            if fn.endswith('.kt'): src.append(os.path.join(r, fn))
    if not src: print("No Kotlin sources!"); return

    print(f"  Compiling {len(src)} Kotlin files...")
    cls = os.path.join(BUILD, 'classes')
    if os.path.exists(cls): shutil.rmtree(cls)
    os.makedirs(cls, exist_ok=True)
    subprocess.run(['kotlinc', '-cp', ANDROID_JAR, '-d', cls] + src,
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
                    '--lib', ANDROID_JAR] + cf + [kt],
                   check=True, stderr=subprocess.DEVNULL)

    print("  Generating AndroidManifest.xml...")
    axml = AXMLWriter().build()
    with open(os.path.join(BUILD, 'AndroidManifest.xml'), 'wb') as f:
        f.write(axml)

    print("  Creating APK...")
    apk = os.path.join(OUT, 'TCC.apk')
    with zipfile.ZipFile(apk, 'w', zipfile.ZIP_DEFLATED) as z:
        zi = zipfile.ZipInfo('AndroidManifest.xml')
        zi.compress_type = zipfile.ZIP_STORED
        zi.extra = b'\x00\x00\x00'
        z.writestr(zi, open(os.path.join(BUILD, 'AndroidManifest.xml'), 'rb').read())
        for dfn in [os.path.join(dx, 'classes.dex')] if os.path.exists(os.path.join(dx, 'classes.dex')) else []:
            with open(dfn, 'rb') as f:
                z.writestr('classes.dex', f.read(), compress_type=zipfile.ZIP_STORED)

    print("  Signing...")
    ks = os.path.join(BASE, 'debug.keystore')
    if not os.path.exists(ks):
        subprocess.run(['keytool', '-genkey', '-v', '-keystore', ks, '-alias', 'debug',
            '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
            '-storepass', 'android', '-keypass', 'android',
            '-dname', 'CN=MCC, OU=Dev, O=AI, L=Unknown, ST=Unknown, C=CN'])
    sp = os.path.join(OUT, 'MCC-signed.apk')
    subprocess.run(['apksigner', 'sign', '--min-sdk-version', '26',
        '--ks', ks, '--ks-pass', 'pass:android', '--ks-key-alias', 'debug',
        '--key-pass', 'pass:android', '--out', sp, apk])
    shutil.move(sp, apk)
    sz = os.path.getsize(apk)
    print(f"\n  TCC APK build complete!  Size: {sz//1024} KB  Package: com.tcc\n")

if __name__ == '__main__':
    build()
