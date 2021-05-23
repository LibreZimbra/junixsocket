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

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link DatagramSocket} implementation that works with AF_UNIX Unix domain sockets.
 * 
 * @author Christian Kohlschütter
 */
public final class AFUNIXDatagramSocket extends DatagramSocket implements AFUNIXSocketExtensions {
  private final AFUNIXDatagramSocketImpl impl;
  private final AncillaryDataSupport ancillaryDataSupport;
  private final AtomicBoolean created = new AtomicBoolean(false);
  private final AtomicBoolean deleteOnClose = new AtomicBoolean(true);
  private final AFUNIXDatagramChannel channel = new AFUNIXDatagramChannel(this);

  private AFUNIXDatagramSocket(final AFUNIXDatagramSocketImpl impl) throws IOException {
    super(impl);
    this.impl = impl;
    this.ancillaryDataSupport = impl.ancillaryDataSupport;
  }

  public static AFUNIXDatagramSocket newInstance() throws IOException {
    return new AFUNIXDatagramSocket(new AFUNIXDatagramSocketImpl());
  }

  @Override
  public void connect(InetAddress address, int port) {
    throw new IllegalArgumentException("Cannot connect to InetAddress");
  }

  /**
   * Reads the next received packet without actually removing it from the queue.
   * 
   * In other words, once a packet is received, calling this method multiple times in a row will not
   * have further effects on the packet contents.
   * 
   * This call still blocks until at least one packet has been received and added to the queue.
   * 
   * @param p The packet.
   * @throws IOException on error.
   */
  public void peek(DatagramPacket p) throws IOException {
    synchronized (p) {
      if (isClosed()) {
        throw new SocketException("Socket is closed");
      }
      getAFImpl().peekData(p);
    }
  }

  @Override
  public void send(DatagramPacket p) throws IOException {
    synchronized (p) {
      if (isClosed()) {
        throw new SocketException("Socket is closed");
      }
      if (!isBound()) {
        bind(AFUNIXSocketAddress.INTERNAL_DUMMY_BIND);
      }
      getAFImpl().send(p);
    }
  }

  @Override
  public synchronized void connect(SocketAddress addr) throws SocketException {
    if (!isBound()) {
      bind(AFUNIXSocketAddress.INTERNAL_DUMMY_BIND);
    }
    super.connect(AFUNIXSocketAddress.INTERNAL_DUMMY_CONNECT);
    try {
      getAFImpl().connect(AFUNIXSocketAddress.preprocessSocketAddress(addr, null));
    } catch (SocketException e) {
      throw e;
    } catch (IOException e) {
      throw (SocketException) new SocketException(e.getMessage()).initCause(e);
    }
  }

  @Override
  public synchronized SocketAddress getRemoteSocketAddress() {
    if (!isConnected()) {
      return null;
    }
    return AFUNIXSocketAddress.getSocketAddress(getAFImpl().getFileDescriptor(), true);
  }

  @Override
  public void close() {
    // IMPORTANT This method must not be synchronized on "this",
    // otherwise we can't unblock a pending read
    if (isClosed()) {
      return;
    }
    getAFImpl().close();
    boolean wasBound = isBound();
    if (wasBound && deleteOnClose.get()) {
      InetAddress addr = getLocalAddress();
      if (AFUNIXSocketAddress.isSupportedAddress(addr)) {
        try {
          AFUNIXSocketAddress socketAddress = AFUNIXSocketAddress.unwrap(addr, 0);
          if (socketAddress.hasFilename()) {
            if (!socketAddress.getFile().delete()) {
              // ignore
            }
          }
        } catch (IOException e) {
          // ignore
        }
      }
    }
    super.close();
  }

  @Override
  public synchronized void bind(SocketAddress addr) throws SocketException {
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    }
    if (isBound()) {
      throw new SocketException("already bound");
    }
    if (addr == null) {
      addr = new InetSocketAddress(0);
    }
    AFUNIXSocketAddress epoint = AFUNIXSocketAddress.preprocessSocketAddress(addr, null);
    super.bind(AFUNIXSocketAddress.INTERNAL_DUMMY_BIND);
    try {
      getAFImpl().bind(epoint);
    } catch (SocketException e) {
      getAFImpl().close();
      throw e;
    }
  }

  @Override
  public SocketAddress getLocalSocketAddress() {
    if (isClosed()) {
      return null;
    }
    if (!isBound()) {
      return null;
    }
    try {
      return AFUNIXSocketAddress.unwrap(getLocalAddress(), getLocalPort());
    } catch (SocketException e) {
      return null;
    }
  }

  /**
   * Checks if this {@link AFUNIXDatagramSocket}'s bound filename should be removed upon
   * {@link #close()}.
   * 
   * Deletion is not guaranteed, especially when not supported (e.g., addresses in the abstract
   * namespace).
   * 
   * @return {@code true} if an attempt is made to delete the socket file upon {@link #close()}.
   */
  public boolean isDeleteOnClose() {
    return deleteOnClose.get();
  }

  /**
   * Enables/disables deleting this {@link AFUNIXDatagramSocket}'s bound filename upon
   * {@link #close()}.
   * 
   * Deletion is not guaranteed, especially when not supported (e.g., addresses in the abstract
   * namespace).
   * 
   * @param b Enabled if {@code true}.
   */
  public void setDeleteOnClose(boolean b) {
    deleteOnClose.set(b);
  }

  AFUNIXDatagramSocketImpl getAFImpl() {
    if (created.compareAndSet(false, true)) {
      try {
        getSoTimeout(); // trigger create via java.net.Socket
      } catch (SocketException e) {
        // ignore
      }
    }
    return impl;
  }

  @Override
  public int getAncillaryReceiveBufferSize() {
    return ancillaryDataSupport.getAncillaryReceiveBufferSize();
  }

  @Override
  public void setAncillaryReceiveBufferSize(int size) {
    ancillaryDataSupport.setAncillaryReceiveBufferSize(size);
  }

  @Override
  public void ensureAncillaryReceiveBufferSize(int minSize) {
    ancillaryDataSupport.ensureAncillaryReceiveBufferSize(minSize);
  }

  @Override
  public FileDescriptor[] getReceivedFileDescriptors() throws IOException {
    return ancillaryDataSupport.getReceivedFileDescriptors();
  }

  @Override
  public void clearReceivedFileDescriptors() {
    ancillaryDataSupport.clearReceivedFileDescriptors();
  }

  @Override
  public void setOutboundFileDescriptors(FileDescriptor... fdescs) throws IOException {
    if (fdescs != null && fdescs.length > 0 && !isConnected()) {
      throw new SocketException("Not connected");
    }
    ancillaryDataSupport.setOutboundFileDescriptors(fdescs);
  }

  @Override
  public boolean hasOutboundFileDescriptors() {
    return ancillaryDataSupport.hasOutboundFileDescriptors();
  }

  @Override
  public AFUNIXSocketCredentials getPeerCredentials() throws IOException {
    if (isClosed() || !isConnected()) {
      throw new SocketException("Not connected");
    }
    return impl.getPeerCredentials();
  }

  @Override
  public boolean isClosed() {
    return super.isClosed() || getAFImpl().isClosed();
  }

  @Override
  public AFUNIXDatagramChannel getChannel() {
    return channel;
  }
}
