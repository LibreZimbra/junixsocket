/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.newsclub.net.unix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Duration;

import org.junit.jupiter.api.Test;

public class AvailableTest extends SocketTestBase {
  private static final int BYTES_SENT = 23;
  private static final int TIME_TO_SLEEP = 100;

  private void receiveBytes(final Socket sock, final int expected) throws IOException {
    final InputStream in = sock.getInputStream();

    int toExpect = expected;

    char firstChar = 'A';

    int available = in.available();
    if (available == 0 && expected != 0) {
      // this may happen, and it's ok.
      final int r = in.read();
      assertEquals('A', r,
          "Available returned 0, so we tried to read the first byte (which should be 65=='A')");

      // as we have already read one byte, we now expect one byte less
      toExpect--;

      available = in.available();

      firstChar = 'B';
    }
    assertEquals(toExpect, available);
    final byte[] buf = new byte[expected];
    final int numRead = in.read(buf);
    assertEquals(toExpect, numRead);

    for (int i = 0; i < numRead; i++) {
      assertEquals(firstChar + i, buf[i] & 0xFF);
    }

    assertEquals(0, in.available());
  }

  private void sendBytes(final Socket sock) throws IOException {
    final OutputStream out = sock.getOutputStream();
    final byte[] buf = new byte[BYTES_SENT];
    for (int i = 0; i < BYTES_SENT; i++) {
      buf[i] = (byte) (i + 'A');
    }
    out.write(buf);
    out.flush();
  }

  /**
   * Tests if {@link InputStream#available()} works as expected. The server sends 23 bytes. The
   * client waits for 100ms. After that, the client should be able to read exactly 23 bytes without
   * blocking. Then, we try the opposite direction.
   */
  @Test
  public void testAvailableAtClient() {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      try (ServerThread serverThread = new ServerThread() {

        @Override
        protected void handleConnection(final AFUNIXSocket sock) throws IOException {
          sendBytes(sock);
          sleepFor(TIME_TO_SLEEP);
          receiveBytes(sock, BYTES_SENT);

          stopAcceptingConnections();
        }
      }; AFUNIXSocket sock = connectToServer()) {
        sleepFor(TIME_TO_SLEEP);
        receiveBytes(sock, BYTES_SENT);
        sendBytes(sock);
      }
    });
  }

  /**
   * Tests if {@link InputStream#available()} works as expected. The client sends 23 bytes. The
   * server waits for 100ms. After that, the server should be able to read exactly 23 bytes without
   * blocking. Then, we try the opposite direction.
   */
  @Test
  public void testAvailableAtServer() {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {

      try (ServerThread serverThread = new ServerThread() {
        @Override
        protected void handleConnection(final AFUNIXSocket sock) throws IOException {
          sleepFor(TIME_TO_SLEEP);
          receiveBytes(sock, BYTES_SENT);
          sendBytes(sock);

          stopAcceptingConnections();
        }
      }; AFUNIXSocket sock = connectToServer()) {
        sendBytes(sock);
        sleepFor(TIME_TO_SLEEP);

        receiveBytes(sock, BYTES_SENT);
      }
    });
  }
}
