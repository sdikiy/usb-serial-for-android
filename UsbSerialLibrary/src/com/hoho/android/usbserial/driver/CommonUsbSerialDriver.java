/* Copyright 2013 Google Inc.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * Project home page: http://code.google.com/p/usb-serial-for-android/
 */

package com.hoho.android.usbserial.driver;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

import java.io.IOException;

/**
 * A base class shared by several driver implementations.
 *
 * @author mike wakerly (opensource@hoho.com)
 */
abstract class CommonUsbSerialDriver implements UsbSerialDriver {

    public static final int DEFAULT_READ_BUFFER_SIZE = 16 * 1024;
    public static final int DEFAULT_WRITE_BUFFER_SIZE = 16 * 1024;

    protected final UsbDevice mDevice;
    protected final UsbDeviceConnection mConnection;

    protected final Object mReadBufferLock = new Object();
    protected final Object mWriteBufferLock = new Object();

    /** Internal read buffer.  Guarded by {@link #mReadBufferLock}. */
    protected byte[] mReadBuffer;

    /** Internal write buffer.  Guarded by {@link #mWriteBufferLock}. */
    protected byte[] mWriteBuffer;

    /** Store last result {@link #getModemStatus()}*/
    protected int lastModemStatus =0;

    public CommonUsbSerialDriver(UsbDevice device, UsbDeviceConnection connection) {
        mDevice = device;
        mConnection = connection;

        mReadBuffer = new byte[DEFAULT_READ_BUFFER_SIZE];
        mWriteBuffer = new byte[DEFAULT_WRITE_BUFFER_SIZE];
    }

    /**
     * Returns the currently-bound USB device.
     *
     * @return the device
     */
    public final UsbDevice getDevice() {
        return mDevice;
    }

    /**
     * Sets the size of the internal buffer used to exchange data with the USB
     * stack for read operations.  Most users should not need to change this.
     *
     * @param bufferSize the size in bytes
     */
    public final void setReadBufferSize(int bufferSize) {
        synchronized (mReadBufferLock) {
            if (bufferSize == mReadBuffer.length) {
                return;
            }
            mReadBuffer = new byte[bufferSize];
        }
    }

    /**
     * Sets the size of the internal buffer used to exchange data with the USB
     * stack for write operations.  Most users should not need to change this.
     *
     * @param bufferSize the size in bytes
     */
    public final void setWriteBufferSize(int bufferSize) {
        synchronized (mWriteBufferLock) {
            if (bufferSize == mWriteBuffer.length) {
                return;
            }
            mWriteBuffer = new byte[bufferSize];
        }
    }

    @Override
    public abstract void open() throws IOException;

    @Override
    public abstract void close() throws IOException;

    @Override
    public abstract int read(final byte[] dest, final int timeoutMillis) throws IOException;

    @Override
    public abstract int write(final byte[] src, final int timeoutMillis) throws IOException;

    @Override
    public abstract void setParameters(
            int baudRate, int dataBits, int stopBits, int parity) throws IOException;

    @Override
    public abstract int getModemStatus() throws IOException;

    @Override
    public boolean getCD() throws IOException {
        // TODO if (((getModemStatus() >>> 8) & MS_DCD_MASK) == 0) return null;
        return (((getModemStatus() & MS_DCD_MASK) == 0) ? false : true);
    }

    @Override
    public boolean getCTS() throws IOException {
        return (((getModemStatus() & MS_CTS_MASK) == 0) ? false : true);
    }

    @Override
    public boolean getRTS() throws IOException {
        return (((getModemStatus() & MS_RTS_MASK) == 0) ? false : true);
    }

    @Override
    public boolean getDSR() throws IOException {
        return (((getModemStatus() & MS_DSR_MASK) == 0) ? false : true);
    }

    @Override
    public boolean getDTR() throws IOException {
        return (((getModemStatus() & MS_DTR_MASK) == 0) ? false : true);
    }

    @Override
    public boolean getRI() throws IOException {
        return (((getModemStatus() & MS_RI_MASK) == 0) ? false : true);
    }

    @Override
    public boolean getLastCD() throws IOException {
        return (((lastModemStatus & MS_DCD_MASK) == 0) ? false : true);
    }

    @Override
    public boolean getLastCTS() throws IOException {
        return (((lastModemStatus & MS_CTS_MASK) == 0) ? false : true);
    }

    @Override
    public boolean getLastRTS() throws IOException {
        return (((lastModemStatus & MS_RTS_MASK) == 0) ? false : true);
    }

    @Override
    public boolean getLastDSR() throws IOException {
        return (((lastModemStatus & MS_DSR_MASK) == 0) ? false : true);
    }

    @Override
    public boolean getLastDTR() throws IOException {
        return (((lastModemStatus & MS_DTR_MASK) == 0) ? false : true);
    }

    @Override
    public boolean getLastRI() throws IOException {
        return (((lastModemStatus & MS_RI_MASK) == 0) ? false : true);
    }

    @Override
    public abstract void setRTS(boolean value) throws IOException;

    @Override
    public abstract void setDTR(boolean value) throws IOException;

}
