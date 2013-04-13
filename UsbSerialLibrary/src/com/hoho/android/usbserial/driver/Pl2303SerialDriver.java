/**
 * 
 */

package com.hoho.android.usbserial.driver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbRequest;
import android.util.Log;

/**
 * @author
 */
public class Pl2303SerialDriver extends CommonUsbSerialDriver implements Runnable {

    private static final String TAG = Pl2303SerialDriver.class.getSimpleName();

    // All USB Classes
    // private UsbManager mUsbManager;
    // private UsbDevice mDevice;
    // private UsbDeviceConnection mConnection;
    private UsbInterface intf;
    private UsbEndpoint ep0;
    private UsbEndpoint ep1;
    private UsbEndpoint ep2;

    // USB control commands
    private static final int SET_LINE_REQUEST_TYPE = 0x21;
    private static final int SET_LINE_REQUEST = 0x20;
    private static final int BREAK_REQUEST_TYPE = 0x21;
    private static final int BREAK_REQUEST = 0x23;
    private static final int BREAK_OFF = 0x0000;
    private static final int GET_LINE_REQUEST_TYPE = 0xa1;
    private static final int GET_LINE_REQUEST = 0x21;
    private static final int VENDOR_WRITE_REQUEST_TYPE = 0x40;
    private static final int VENDOR_WRITE_REQUEST = 0x01;
    private static final int VENDOR_READ_REQUEST_TYPE = 0xc0;
    private static final int VENDOR_READ_REQUEST = 0x01;
    private static final int SET_CONTROL_REQUEST_TYPE = 0x21;
    private static final int SET_CONTROL_REQUEST = 0x22;

    // RS232 Line constants
    private static final int CONTROL_DTR = 0x01;
    private static final int CONTROL_RTS = 0x02;
    private static final int UART_DCD = 0x01;
    private static final int UART_DSR = 0x02;
    private static final int UART_RING = 0x08;
    private static final int UART_CTS = 0x80;

    // Type 0 = PL2303, Type 1 = PL2303-HX
    private int PL2303type = 0;

    // Status of DTR/RTS Lines
    private int ControlLines = 0;

    private boolean opened;

    /**
     * @param device
     * @param connection
     */
    public Pl2303SerialDriver(UsbDevice device, UsbDeviceConnection connection) {
        super(device, connection);
        // TODO Auto-generated constructor stub
    }

    /*
     * (non-Javadoc)
     * @see com.hoho.android.usbserial.driver.CommonUsbSerialDriver#open()
     */
    @Override
    public void open() throws IOException {
        opened = false;
        try {
            for (int i = 0; i < mDevice.getInterfaceCount(); i++) {
                UsbInterface usbIface = mDevice.getInterface(i);
                if (mConnection.claimInterface(usbIface, true)) {
                    Log.d(TAG, "claimInterface " + i + " SUCCESS");
                } else {
                    Log.d(TAG, "claimInterface " + i + " FAIL");
                }
            }

            intf = mDevice.getInterface(0);

            UsbInterface dataIface = mDevice.getInterface(mDevice.getInterfaceCount() - 1);
            for (int i = 0; i < dataIface.getEndpointCount(); i++) {
                UsbEndpoint ep = dataIface.getEndpoint(i);
                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                        // endpoint addr 0x83 = input bulk
                        ep2 = ep;
                    } else {
                        // endpoint addr 0x2 = output bulk
                        ep1 = ep;
                    }
                } else {
                    // endpoint addr 0x81 = input interrupt
                    ep0 = ep;
                }
            }

            // Type 1 = PL2303HX
            // if (mConnection.getRawDescriptors()[7] == 64) PL2303type = 1;
            Log.d(TAG, "PL2303 type " + PL2303type + " detected");

            // Initialization of PL2303 according to linux pl2303.c driver
            byte[] buffer = new byte[1];
            mConnection.controlTransfer(VENDOR_READ_REQUEST_TYPE, VENDOR_READ_REQUEST, 0x8484, 0,
                    buffer, 1, 100);
            mConnection.controlTransfer(VENDOR_WRITE_REQUEST_TYPE, VENDOR_WRITE_REQUEST, 0x0404, 0,
                    null, 0, 100);
            mConnection.controlTransfer(VENDOR_READ_REQUEST_TYPE, VENDOR_READ_REQUEST, 0x8484, 0,
                    buffer, 1, 100);
            mConnection.controlTransfer(VENDOR_READ_REQUEST_TYPE, VENDOR_READ_REQUEST, 0x8383, 0,
                    buffer, 1, 100);
            mConnection.controlTransfer(VENDOR_READ_REQUEST_TYPE, VENDOR_READ_REQUEST, 0x8484, 0,
                    buffer, 1, 100);
            mConnection.controlTransfer(VENDOR_WRITE_REQUEST_TYPE, VENDOR_WRITE_REQUEST, 0x0404, 1,
                    null, 0, 100);
            mConnection.controlTransfer(VENDOR_READ_REQUEST_TYPE, VENDOR_READ_REQUEST, 0x8484, 0,
                    buffer, 1, 100);
            mConnection.controlTransfer(VENDOR_READ_REQUEST_TYPE, VENDOR_READ_REQUEST, 0x8383, 0,
                    buffer, 1, 100);
            mConnection.controlTransfer(VENDOR_WRITE_REQUEST_TYPE, VENDOR_WRITE_REQUEST, 0, 1,
                    null, 0, 100);
            mConnection.controlTransfer(VENDOR_WRITE_REQUEST_TYPE, VENDOR_WRITE_REQUEST, 1, 0,
                    null, 0, 100);
            if (PL2303type == 1)
                mConnection.controlTransfer(VENDOR_WRITE_REQUEST_TYPE, VENDOR_WRITE_REQUEST, 2,
                        0x44, null, 0, 100);
            else
                mConnection.controlTransfer(VENDOR_WRITE_REQUEST_TYPE, VENDOR_WRITE_REQUEST, 2,
                        0x24, null, 0, 100);

            // Start control thread for ModemStatus lines
            Thread tMS = new Thread(this);
            tMS.start();

            opened = true;
        } finally {
            if (!opened) {
                close();
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see com.hoho.android.usbserial.driver.CommonUsbSerialDriver#close()
     */
    @Override
    public void close() throws IOException {
        if (mConnection != null) {
            // terminate tMS thread
            // https://code.google.com/p/android/issues/detail?id=39522
            opened = false;
            // up/down DTR/RTS for generate interrupt in tMS thread
            setRTS(true);
            setDTR(true);
            setRTS(false);
            setDTR(false);

            mConnection.releaseInterface(intf);
            mConnection.close();
            // mConnection = null;
            // mDevice = null;
            ep0 = null;
            ep1 = null;
            ep2 = null;
            Log.d(TAG, "Device closed");
        }
    }

    /*
     * (non-Javadoc)
     * @see com.hoho.android.usbserial.driver.CommonUsbSerialDriver#read(byte[],
     * int)
     */
    @Override
    public int read(byte[] dest, int timeoutMillis) throws IOException {
        // TODO Auto-generated method stub
        return 0;
    }

    /*
     * (non-Javadoc)
     * @see
     * com.hoho.android.usbserial.driver.CommonUsbSerialDriver#write(byte[],
     * int)
     */
    @Override
    public int write(byte[] src, int timeoutMillis) throws IOException {
        // TODO Auto-generated method stub
        return 0;
    }

    /*
     * (non-Javadoc)
     * @see
     * com.hoho.android.usbserial.driver.CommonUsbSerialDriver#setParameters
     * (int, int, int, int)
     */
    @Override
    public void setParameters(int baudRate, int dataBits, int stopBits, int parity)
            throws IOException {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * @see
     * com.hoho.android.usbserial.driver.CommonUsbSerialDriver#getModemStatus()
     */
    @Override
    public int getModemStatus() throws IOException {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return lastModemStatus;

    }

    /*
     * (non-Javadoc)
     * @see
     * com.hoho.android.usbserial.driver.CommonUsbSerialDriver#setRTS(boolean)
     */
    @Override
    public void setRTS(boolean value) throws IOException {
        if ((value) && !((ControlLines & CONTROL_RTS) == CONTROL_RTS))
            ControlLines = ControlLines + CONTROL_RTS;
        if (!(value) && ((ControlLines & CONTROL_RTS) == CONTROL_RTS))
            ControlLines = ControlLines - CONTROL_RTS;
        mConnection.controlTransfer(SET_CONTROL_REQUEST_TYPE, SET_CONTROL_REQUEST, ControlLines, 0,
                null, 0, 100);
        // Log.d(TAG, "RTS set to " + value);
    }

    /*
     * (non-Javadoc)
     * @see
     * com.hoho.android.usbserial.driver.CommonUsbSerialDriver#setDTR(boolean)
     */
    @Override
    public void setDTR(boolean value) throws IOException {
        if ((value) && !((ControlLines & CONTROL_DTR) == CONTROL_DTR))
            ControlLines = ControlLines + CONTROL_DTR;
        if (!(value) && ((ControlLines & CONTROL_DTR) == CONTROL_DTR))
            ControlLines = ControlLines - CONTROL_DTR;
        mConnection.controlTransfer(SET_CONTROL_REQUEST_TYPE, SET_CONTROL_REQUEST, ControlLines, 0,
                null, 0, 100);
        // Log.d(TAG, "DTR set to " + value);
    }

    public static Map<Integer, int[]> getSupportedDevices() {
        final Map<Integer, int[]> supportedDevices = new LinkedHashMap<Integer, int[]>();
        supportedDevices.put(Integer.valueOf(UsbId.VENDOR_PROLIFIC),
                new int[] {
                    UsbId.PROLIFIC_PL2303
                });
        return supportedDevices;
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        int res;
        ByteBuffer readBuffer = ByteBuffer.allocate(ep0.getMaxPacketSize());
        UsbRequest request = new UsbRequest();

        request.initialize(mConnection, ep0);

        while ((mConnection != null) & opened) {
            request.queue(readBuffer, ep0.getMaxPacketSize());
            // request.queue(readBuffer, 9);

            // Log.d(TAG, "Start mConnection.requestWait()");
            UsbRequest retRequest = mConnection.requestWait();
            // Log.d(TAG, "Stop mConnection.requestWait()");

            // The request returns when any line status has changed
            if (retRequest.getEndpoint() == ep0) {
                // Save status
                // lastModemStatus = 0xFF & readBuffer.get(8);
                res = MS_DCD_MASK | MS_CTS_MASK | (MS_RTS_MASK & 0x00) | MS_DSR_MASK
                        | (MS_DTR_MASK & 0x00)
                        | MS_RI_MASK;
                res <<= 8;
                res = res | (((readBuffer.get(8) & UART_DCD) == 0) ? 0 : MS_DCD_MASK)
                        | (((readBuffer.get(8) & UART_CTS) == 0) ? 0 : MS_CTS_MASK)
                        // | (((buffer[0] & GET_MCR_RTS) == 0) ? 0 :
                        // MS_RTS_MASK)
                        | (((readBuffer.get(8) & UART_DSR) == 0) ? 0 : MS_DSR_MASK)
                        // | (((buffer[0] & GET_MCR_DTR) == 0) ? 0 :
                        // MS_DTR_MASK)
                        | (((readBuffer.get(8) & UART_RING) == 0) ? 0 : MS_RI_MASK);
                lastModemStatus = res;
            }
        }
        Log.d(TAG, "terminate tMS thread");

    }

}
