package dev.baranhan.viltrumitecore.client.scp096;

import java.io.IOException;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

/**
 * Folds a 16-bit stereo stream to mono so OpenAL can place it in the world (stereo buffers are never spatialised:
 * they would play at full volume everywhere). The original SCP: Containment Breach loops are stereo; mono streams
 * and other formats pass through untouched. Client only.
 */
public final class MonoDownmixStream implements AudioStream {
   private final AudioStream inner;
   private final AudioFormat format;

   private MonoDownmixStream(AudioStream inner) {
      this.inner = inner;
      AudioFormat f = inner.getFormat();
      this.format = new AudioFormat(f.getSampleRate(), 16, 1, true, f.isBigEndian());
   }

   /** The stream itself when it is not 16-bit stereo, otherwise a mono view of it. */
   public static AudioStream wrap(AudioStream stream) {
      AudioFormat f = stream.getFormat();
      return f.getChannels() == 2 && f.getSampleSizeInBits() == 16 ? new MonoDownmixStream(stream) : stream;
   }

   @Override
   public AudioFormat getFormat() {
      return this.format;
   }

   @Override
   public ByteBuffer read(int size) throws IOException {
      ByteBuffer in = this.inner.read(size * 2);
      ByteBuffer out = BufferUtils.createByteBuffer(in.remaining() / 2);
      downmix(in, out);
      out.flip();
      return out;
   }

   /**
    * Pure: averages every left/right pair of 16-bit samples of {@code in} (read in its own byte order) into one
    * sample of {@code out} (written in out's order); a trailing incomplete frame is dropped.
    */
   static void downmix(ByteBuffer in, ByteBuffer out) {
      int frames = Math.min(in.remaining() / 4, out.remaining() / 2);
      int p = in.position();
      for (int i = 0; i < frames; i++) {
         int l = in.getShort(p + i * 4);
         int r = in.getShort(p + i * 4 + 2);
         out.putShort((short)((l + r) >> 1));
      }
      in.position(p + frames * 4);
   }

   @Override
   public void close() throws IOException {
      this.inner.close();
   }
}
