package net.sf.katta.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;

/**
 * An {@link InputStream} which throttles the amount of bytes which is read from
 * the underlying {@link InputStream} in a given time frame.
 * 
 * <p>
 * Usage Example: <br>
 * <i>//creates an throttled input stream which reads 1024 bytes/sec from the
 * underlying input stream at the most </i> <br>
 * <code>
 * ThrottledInputStream throttledInputStream = new ThrottledInputStream(otherIputStream, new ThrottleSemaphore(1024));
 * </code><br>
 * 
 * <p>
 * Usage over multiple {@link InputStream}s: <br>
 * <i>//throttle the read of multiple input streams at the rate of 1024
 * bytes/sec </i> <br>
 * <code>
 * ThrottleSemaphore semaphore = new ThrottleSemaphore(1024);<br>
 * ThrottledInputStream throttledInputStream1 = new ThrottledInputStream(otherIputStream1, semaphore);<br>
 * ThrottledInputStream throttledInputStream2 = new ThrottledInputStream(otherIputStream2, semaphore);<br>
 * ...
 * </code><br>
 */
public class ThrottledInputStream extends InputStream {

  private final InputStream _inputStream;
  private final ThrottleSemaphore _semaphore;

  public ThrottledInputStream(InputStream inputStream, ThrottleSemaphore semaphore) {
    _inputStream = inputStream;
    _semaphore = semaphore;
  }

  @Override
  public int read() throws IOException {
    _semaphore.aquireBytes(1);
    return _inputStream.read();
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int aquired = _semaphore.aquireBytes(len);
    return _inputStream.read(b, off, aquired);
  }

  @Override
  public void close() throws IOException {
    _inputStream.close();
  }

  /**
   * This semaphore maintains the permitted bytes in a given timeframe. Each
   * {@link #aquireBytes(int)} blocks if necessary until at least once byte
   * couldb be aquired.
   * 
   * <p>
   * The time unit is bytes/second wheras the window of one second is splitted
   * into smaller windows to allow more steadied operations.
   * 
   * <p>
   * This class is thread safe and one instance can be used by multiple threads/
   * {@link ThrottledInputStream}s. (But it might not be fair to the different
   * treads)
   */
  public static class ThrottleSemaphore {

    private static final int SECOND = 1000;
    private final int _maxBytesPerWindow;
    private int _remainingBytesInCurrentWindow;
    private long _nextWindowStartTime;
    private final int _windowsPerSecond;

    public ThrottleSemaphore(float bytesPerSecond) {
      this(bytesPerSecond, 10);
    }

    public ThrottleSemaphore(float bytesPerSecond, int windowsPerSecond) {
      _windowsPerSecond = windowsPerSecond;
      _maxBytesPerWindow = (int) (bytesPerSecond / windowsPerSecond);
    }

    public synchronized int aquireBytes(int desired) throws IOException {
      try {
        waitForAllowedBytes();
        int auiredBytes = Math.min(desired, _remainingBytesInCurrentWindow);
        _remainingBytesInCurrentWindow -= auiredBytes;
        return auiredBytes;
      } catch (InterruptedException e) {
        throw new InterruptedIOException();
      }
    }

    private void waitForAllowedBytes() throws InterruptedException {
      updateWindow();
      while (_remainingBytesInCurrentWindow == 0) {
        updateWindow();
        Thread.sleep(_nextWindowStartTime - System.currentTimeMillis());
      }
    }

    private final void updateWindow() {
      long now = System.currentTimeMillis();
      while (now >= _nextWindowStartTime) {
        if (now >= _nextWindowStartTime + SECOND) {
          _nextWindowStartTime = now + SECOND / _windowsPerSecond;
          _remainingBytesInCurrentWindow = _maxBytesPerWindow;
        }
        _nextWindowStartTime += SECOND / _windowsPerSecond;
        _remainingBytesInCurrentWindow += _maxBytesPerWindow;
      }
    }
  }

}