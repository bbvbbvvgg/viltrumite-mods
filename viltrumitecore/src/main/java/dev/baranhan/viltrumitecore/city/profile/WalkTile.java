package dev.baranhan.viltrumitecore.city.profile;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Pedestrian data of one 512x512 region (pure, no Minecraft classes; tests and the
 * offline tools share the format). Per column one 16-bit cell:
 * 0 = no pedestrian surface; else bits 0-12 = surface height in half blocks + 512,
 * bits 13-14 = class ({@link #WALK} network, {@link #PREFERRED} sidewalk/plaza/park:
 * spawn and stroll targets).
 *
 * <p>File: magic "VCW1", int format (1), then a deflated stream of: 128-byte chunk
 * presence bitmap (bit i = local chunk index z*32+x), then 256 big-endian shorts
 * (index z*16+x) for every present chunk in index order.
 */
public final class WalkTile {
   public static final int MAGIC = 0x56435731; // "VCW1"
   public static final int FORMAT = 1;
   public static final int WALK = 1;
   public static final int PREFERRED = 2;
   public static final int HEIGHT_OFFSET = 512;
   public static final WalkTile EMPTY = new WalkTile(new short[1024][]);

   private final short[][] chunks;
   private final int cells;
   private final int preferredCells;

   public WalkTile(short[][] chunks) {
      if (chunks.length != 1024) {
         throw new IllegalArgumentException("1024 chunks expected");
      }

      this.chunks = chunks;
      int c = 0;
      int p = 0;
      for (short[] ch : chunks) {
         if (ch == null) {
            continue;
         }

         for (short v : ch) {
            if (v != 0) {
               c++;
               if (cls(v) == PREFERRED) {
                  p++;
               }
            }
         }
      }

      this.cells = c;
      this.preferredCells = p;
   }

   public static int encode(int h2, int cls) {
      int h = h2 + HEIGHT_OFFSET;
      if (h <= 0 || h >= 1 << 13 || cls < 1 || cls > 3) {
         throw new IllegalArgumentException("cell out of range: h2=" + h2 + " cls=" + cls);
      }

      return h | cls << 13;
   }

   public static int cls(int v) {
      return (v & 0xFFFF) >>> 13 & 3;
   }

   /** Surface height in half blocks (2 * standing Y). */
   public static int h2(int v) {
      return (v & 0x1FFF) - HEIGHT_OFFSET;
   }

   /** Block Y of the feet of someone standing on the cell. */
   public static int feetY(int v) {
      return Math.floorDiv(h2(v), 2);
   }

   /** Raw cell at local region coordinates (0..511). */
   public int cell(int lx, int lz) {
      short[] ch = this.chunks[(lz >> 4) * 32 + (lx >> 4)];
      return ch == null ? 0 : ch[(lz & 15) * 16 + (lx & 15)] & 0xFFFF;
   }

   public boolean hasChunk(int localChunkX, int localChunkZ) {
      return this.chunks[localChunkZ * 32 + localChunkX] != null;
   }

   public int cells() {
      return this.cells;
   }

   public int preferredCells() {
      return this.preferredCells;
   }

   public static WalkTile read(InputStream raw) throws IOException {
      DataInputStream head = new DataInputStream(raw);
      if (head.readInt() != MAGIC) {
         throw new IOException("not a walk tile");
      }

      int format = head.readInt();
      if (format != FORMAT) {
         throw new IOException("unsupported walk tile format " + format);
      }

      DataInputStream in = new DataInputStream(new InflaterInputStream(raw));
      byte[] bitmap = new byte[128];
      in.readFully(bitmap);
      short[][] chunks = new short[1024][];
      for (int i = 0; i < 1024; i++) {
         if ((bitmap[i >> 3] >> (i & 7) & 1) == 0) {
            continue;
         }

         short[] ch = new short[256];
         for (int j = 0; j < 256; j++) {
            ch[j] = in.readShort();
         }

         chunks[i] = ch;
      }

      return new WalkTile(chunks);
   }

   public byte[] write() throws IOException {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream head = new DataOutputStream(bytes);
      head.writeInt(MAGIC);
      head.writeInt(FORMAT);
      head.flush();
      DeflaterOutputStream z = new DeflaterOutputStream(bytes);
      DataOutputStream out = new DataOutputStream(z);
      byte[] bitmap = new byte[128];
      for (int i = 0; i < 1024; i++) {
         if (this.chunks[i] != null) {
            bitmap[i >> 3] |= (byte)(1 << (i & 7));
         }
      }

      out.write(bitmap);
      for (short[] ch : this.chunks) {
         if (ch != null) {
            for (short v : ch) {
               out.writeShort(v);
            }
         }
      }

      out.flush();
      z.finish();
      return bytes.toByteArray();
   }
}
