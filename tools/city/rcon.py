"""Minimal RCON client: python3 tools/city/rcon.py "city status" "sleep 2" "city status" (port 25575, password via RCON_PASSWORD, default smoke)."""
import os, socket, struct, sys, time
def pkt(s, i, t, body):
    b = body.encode()+b'\x00\x00'
    s.sendall(struct.pack('<iii', len(b)+8, i, t)+b)
def read(s):
    n = struct.unpack('<i', s.recv(4))[0]; d=b''
    while len(d)<n: d+=s.recv(n-len(d))
    i,t = struct.unpack('<ii', d[:8]); return d[8:-2].decode('utf-8','replace')
s = socket.create_connection(('127.0.0.1', 25575)); pkt(s,1,3,os.environ.get('RCON_PASSWORD','smoke')); read(s)
for cmd in sys.argv[1:]:
    if cmd.startswith('sleep '): time.sleep(float(cmd[6:])); continue
    pkt(s,2,2,cmd); print('>',cmd); print(read(s))
