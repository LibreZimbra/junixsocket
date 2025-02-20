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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.kohlschutter.util.IOUtil;

/**
 * Tests sending and receiving file descriptors.
 * 
 * @author Christian Kohlschütter
 */
@AFUNIXSocketCapabilityRequirement(AFUNIXSocketCapability.CAPABILITY_FILE_DESCRIPTORS)
// CPD-OFF - Skip code-duplication checks
public class FileDescriptorsTest extends SocketTestBase {
  @Test
  public void testSendRecvFileDescriptors() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      try (ServerThread serverThread = new ServerThread() {
        @Override
        protected void handleConnection(final AFUNIXSocket socket) throws IOException {
          socket.setOutboundFileDescriptors(FileDescriptor.in, FileDescriptor.err);
          assertTrue(socket.hasOutboundFileDescriptors());
          try (OutputStream outputStream = socket.getOutputStream()) {
            outputStream.write("HELLO".getBytes("UTF-8"));
          }
          assertFalse(socket.hasOutboundFileDescriptors());

          stopAcceptingConnections();
        }
      };
          AFUNIXSocket socket = connectToServer(); //
          InputStream in = socket.getInputStream()) {
        socket.setAncillaryReceiveBufferSize(1024);

        byte[] buf = new byte[64];
        FileDescriptor[] fds;
        int numRead;
        fds = socket.getReceivedFileDescriptors();
        assertNull(fds, "Initially, there are no file descriptors");

        numRead = in.read(buf);
        assertEquals(5, numRead, "'HELLO' is five bytes long");
        assertEquals("HELLO", new String(buf, 0, numRead, "UTF-8"));

        fds = socket.getReceivedFileDescriptors();
        assertEquals(2, fds.length, "Now, we should have two file descriptors");

        fds = socket.getReceivedFileDescriptors();
        assertNull(fds, "If we ask again, these new file descriptors should be gone");

        try (InputStream is = socket.getInputStream()) {
          numRead = is.read(buf);
          assertEquals(-1, numRead, "There shouldn't be anything left to read");
          fds = socket.getReceivedFileDescriptors();
          assertNull(fds, "There shouldn't be any new file descriptors");
        }
      }
    });
  }

  @Test
  public void testNullFileDescriptorArray() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      try (ServerThread serverThread = new ServerThread() {

        @Override
        protected void handleConnection(final AFUNIXSocket socket) throws IOException {
          socket.setOutboundFileDescriptors((FileDescriptor[]) null);
          // NOTE: send an arbitrary byte — we can't send fds without any in-band data
          try (OutputStream outputStream = socket.getOutputStream()) {
            outputStream.write(123);
          }

          stopAcceptingConnections();
        }
      }; //
          AFUNIXSocket socket = connectToServer();) {
        socket.setAncillaryReceiveBufferSize(1024);
        try (InputStream inputStream = socket.getInputStream()) {
          inputStream.read();
        }
      }
    });
  }

  @Test
  public void testEmptyFileDescriptorArray() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      try (ServerThread serverThread = new ServerThread() {

        @Override
        protected void handleConnection(final AFUNIXSocket socket) throws IOException {
          socket.setOutboundFileDescriptors();
          // NOTE: send an arbitrary byte — we can't send fds without any in-band data
          try (OutputStream outputStream = socket.getOutputStream()) {
            outputStream.write(123);
          }

          stopAcceptingConnections();
        }
      }; //
          AFUNIXSocket socket = connectToServer();) {
        socket.setAncillaryReceiveBufferSize(1024);
        try (InputStream inputStream = socket.getInputStream()) {
          inputStream.read();
        }
      }
    });
  }

  @Test
  public void testBadFileDescriptor() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      try (ServerThread serverThread = new ServerThread() {

        @Override
        protected void handleConnection(final AFUNIXSocket socket) throws IOException {
          socket.setOutboundFileDescriptors(new FileDescriptor());
          try {
            // NOTE: send an arbitrary byte — we can't send fds without any in-band data
            try (OutputStream outputStream = socket.getOutputStream()) {
              outputStream.write(123);
            }

            Assertions.fail("Expected a \"Bad file descriptor\" SocketException");
          } catch (SocketException e) {
            // expected
          }

          stopAcceptingConnections();
        }
      }; //
          AFUNIXSocket socket = connectToServer();) {
        socket.setAncillaryReceiveBufferSize(1024);
        try (InputStream inputStream = socket.getInputStream()) {
          inputStream.read();
        }
      }
    });
  }

  @Test
  public void testNoAncillaryReceiveBuffer() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      try (ServerThread serverThread = new ServerThread() {

        @Override
        protected void handleConnection(final AFUNIXSocket socket) throws IOException {
          socket.setOutboundFileDescriptors(FileDescriptor.in, FileDescriptor.err);

          // NOTE: send an arbitrary byte — we can't send fds without any in-band data
          try (OutputStream outputStream = socket.getOutputStream()) {
            outputStream.write(123);
          }

          stopAcceptingConnections();
        }
      }; //
          AFUNIXSocket socket = connectToServer(); //
          InputStream inputStream = socket.getInputStream()) {
        // NOTE: we haven't set the ancillary receive buffer size

        try {
          assertEquals(123, inputStream.read());
        } catch (SocketException e) {
          // on Linux, a SocketException may be thrown (an ancillary message was sent, but not read)
        }
        assertNull(socket.getReceivedFileDescriptors());
        assertEquals(0, socket.getAncillaryReceiveBufferSize());
      }
    });
  }

  @Test
  public void testAncillaryReceiveBufferTooSmall() throws Exception {
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      try (ServerThread serverThread = new ServerThread() {

        @Override
        protected void handleConnection(final AFUNIXSocket socket) throws IOException {
          socket.setOutboundFileDescriptors(FileDescriptor.in, FileDescriptor.err);
          assertTrue(socket.hasOutboundFileDescriptors());

          // NOTE: send an arbitrary byte — we can't send fds without any in-band data
          try (OutputStream outputStream = socket.getOutputStream()) {
            outputStream.write(123);
          }

          assertFalse(socket.hasOutboundFileDescriptors());

          stopAcceptingConnections();
        }
      };
          AFUNIXSocket socket = connectToServer();
          InputStream inputStream = socket.getInputStream()) {
        // using this call directly to get a really small buffer
        socket.getAFImpl().ancillaryDataSupport.setAncillaryReceiveBufferSize0(13);
        try {
          assertEquals(123, inputStream.read());
          FileDescriptor[] fds = socket.getReceivedFileDescriptors();
          if (fds != null) {
            if (fds.length == 2) {
              // space was sufficient
            } else if (fds.length == 1) {
              // Not all operating systems throw a "No buffer space available" message
              System.err.println("WARNING: Not all file descriptors were received");
            } else {
              assertEquals(2, fds.length, "Received wrong number of file descriptors");
            }
          } else {
            Assertions.fail("Expected a \"No buffer space available\" SocketException");
          }
        } catch (SocketException e) {
          // expected
        }
        assertNull(socket.getReceivedFileDescriptors());
      }
    });
  }

  @Test
  public void testFileInputStream() throws Exception {
    final File tmpFile = SocketTestBase.newTempFile();

    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      try (ServerThread serverThread = new ServerThread() {

        @Override
        protected void handleConnection(final AFUNIXSocket socket) throws IOException {
          try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
            fos.write("WORLD!".getBytes("UTF-8"));
          }
          try (FileInputStream fin = new FileInputStream(tmpFile)) {
            socket.setOutboundFileDescriptors(fin.getFD());
            try (OutputStream outputStream = socket.getOutputStream()) {
              outputStream.write("HELLO".getBytes("UTF-8"));
            }
          }

          stopAcceptingConnections();
        }
      }; //
          AFUNIXSocket socket = connectToServer(); //
          InputStream in = socket.getInputStream()) {
        socket.setAncillaryReceiveBufferSize(1024);

        byte[] buf = new byte[64];
        FileDescriptor[] fds;
        int numRead;

        numRead = in.read(buf);
        assertEquals(5, numRead, "'HELLO' is five bytes long");
        assertEquals("HELLO", new String(buf, 0, numRead, "UTF-8"));

        fds = socket.getReceivedFileDescriptors();
        assertEquals(1, fds.length, "Now, we should have two file descriptors");
        FileDescriptor fdesc = fds[0];

        try (FileInputStream fin = new FileInputStream(fdesc)) {
          numRead = fin.read(buf);
          assertEquals(6, numRead, "'WORLD!' is six bytes long");
          assertEquals("WORLD!", new String(buf, 0, numRead, "UTF-8"));
        }
      } finally {
        Files.deleteIfExists(tmpFile.toPath());
      }
    });
  }

  @Test
  public void testFileInputStreamPartiallyConsumed() throws Exception {
    final File tmpFile = SocketTestBase.newTempFile();

    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      try (ServerThread serverThread = new ServerThread() {

        @Override
        protected void handleConnection(final AFUNIXSocket socket) throws IOException {
          try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
            fos.write("WORLD!".getBytes("UTF-8"));
          }
          try (FileInputStream fin = new FileInputStream(tmpFile)) {
            assertEquals('W', fin.read());

            // We send the file descriptor of fin, from which we already consumed one byte.
            socket.setOutboundFileDescriptors(fin.getFD());
            try (OutputStream outputStream = socket.getOutputStream()) {
              outputStream.write("HELLO".getBytes("UTF-8"));
            }
          }

          stopAcceptingConnections();
        }
      }; //
          AFUNIXSocket socket = connectToServer(); //
          InputStream in = socket.getInputStream()) {
        socket.setAncillaryReceiveBufferSize(1024);

        byte[] buf = new byte[64];
        FileDescriptor[] fds;
        int numRead;

        numRead = in.read(buf);
        assertEquals(5, numRead, "'HELLO' is five bytes long");
        assertEquals("HELLO", new String(buf, 0, numRead, "UTF-8"));

        fds = socket.getReceivedFileDescriptors();
        assertEquals(1, fds.length, "Now, we should have two file descriptors");
        FileDescriptor fdesc = fds[0];

        try (FileInputStream fin = new FileInputStream(fdesc)) {
          numRead = fin.read(buf);
          assertEquals(5, numRead, "'ORLD!' is five bytes long");
          assertEquals("ORLD!", new String(buf, 0, numRead, "UTF-8"));
        }
      } finally {
        Files.deleteIfExists(tmpFile.toPath());
      }
    });
  }

  @Test
  public void testDatagramSocket() throws Exception {
    AFUNIXSocketAddress ds1Addr = AFUNIXSocketAddress.of(newTempFile());
    AFUNIXSocketAddress ds2Addr = AFUNIXSocketAddress.of(newTempFile());

    try (AFUNIXDatagramSocket ds1 = AFUNIXDatagramSocket.newInstance();
        AFUNIXDatagramSocket ds2 = AFUNIXDatagramSocket.newInstance();) {
      ds1.setAncillaryReceiveBufferSize(1024);
      ds2.setAncillaryReceiveBufferSize(1024);

      ds1.bind(ds1Addr);
      ds2.bind(ds2Addr);
      ds1.connect(ds2Addr);
      ds2.connect(ds1Addr);

      File tmpOut = newTempFile();
      try (FileOutputStream fos = new FileOutputStream(tmpOut)) {
        ds1.setOutboundFileDescriptors(fos.getFD());
        DatagramPacket dp = AFUNIXDatagramUtil.datagramWithCapacity(64);
        assertTrue(ds1.hasOutboundFileDescriptors());
        ds1.send(dp);
        assertFalse(ds1.hasOutboundFileDescriptors());
        ds2.receive(dp);
        FileDescriptor[] fds = ds2.getReceivedFileDescriptors();
        assertEquals(1, fds.length);

        try (FileOutputStream fos2 = new FileOutputStream(fds[0])) {
          fos.write("Hello".getBytes(StandardCharsets.UTF_8));
          // closing the received file descriptor will not close the original one ...
        }

        // ... which is why we can append the data here
        fos.write("World".getBytes(StandardCharsets.UTF_8));
      }

      try (FileInputStream fin = new FileInputStream(tmpOut)) {
        String text = new String(IOUtil.readAllBytes(fin), StandardCharsets.UTF_8);
        // ... and the final output will contain both parts
        assertEquals("HelloWorld", text);
      }
    }
  }
}
