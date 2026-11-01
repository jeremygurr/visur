#!/usr/bin/env python3
"""Drive the spike in a real pty so it has a tty, send keys, capture the screen."""
import fcntl, os, pty, re, select, struct, sys, termios, time

COLS, ROWS = 100, 30
BIN = os.path.join(os.path.dirname(__file__), "visur-term/target/release/visur-term")

master, slave = pty.openpty()
fcntl.ioctl(slave, termios.TIOCSWINSZ, struct.pack("HHHH", ROWS, COLS, 0, 0))

pid = os.fork()
if pid == 0:
    os.setsid()
    fcntl.ioctl(slave, termios.TIOCSCTTY, 0)
    os.dup2(slave, 0); os.dup2(slave, 1); os.dup2(slave, 2)
    os.close(master)
    os.execv(BIN, [BIN])
os.close(slave)

buf = b""
def pump(seconds):
    global buf
    end = time.time() + seconds
    while time.time() < end:
        r, _, _ = select.select([master], [], [], 0.05)
        if master in r:
            try:
                chunk = os.read(master, 65536)
            except OSError:
                return
            if not chunk:
                return
            buf += chunk

def key(s):
    try:
        os.write(master, s.encode())
    except OSError as e:
        print(f"!! write failed ({e}) -- child is gone"); return False
    pump(0.45)
    return True

pump(4.0)
print(f"--- bytes after attach: {len(buf)}")
first = buf.decode(errors="replace")
plain = re.sub(r"\x1b\[[0-9;?]*[a-zA-Z]", "", first)
print("--- screen (escapes stripped), first 400 chars ---")
print(plain[:400].replace("\x1b", ""))
print("--- first 300 raw bytes ---")
print(repr(first[:300]))

if not all(key("l") for _ in range(6)):
    print("--- tail after early death ---"); print(buf.decode(errors="replace")[-500:]); raise SystemExit(0)
key("i")
for _ in range(5): key("x")
key("\x1b")          # Esc back to navigate
key("l")
key("l")


try:
    os.write(master, b"\x03")   # Ctrl-C quits
except OSError as e:
    print(f"!! ctrl-c write failed: {e}")
pump(1.5)
try:
    os.waitpid(pid, 0)
except ChildProcessError:
    pass
tail = buf.decode(errors="replace")
print("--- tail ---")
print(tail[-700:])
print(f"--- total bytes: {len(buf)}")
