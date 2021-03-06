package com.hoho.android.usbserial.driver;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.util.Log;

/*
 * http://www.silabs.com/Support%20Documents/TechnicalDocs/AN571.pdf
 */
public class Cp2102SerialDriver extends CommonUsbSerialDriver {
    
    private static final String TAG = Cp2102SerialDriver.class.getSimpleName();
    
    private static final int DEFAULT_BAUD_RATE = 9600;
    
    private static final int USB_WRITE_TIMEOUT_MILLIS = 5000;

    /*
     * Configuration Request Types
     */
    // bmRequestType 01000001b
    private static final int REQTYPE_HOST_TO_DEVICE = 0x41;
    // bmRequestType 11000001b
    private static final int REQTYPE_DEVICE_TO_HOST = 0xC1;

    /*
     * Configuration Request Codes
     */
    private static final int SILABSER_IFC_ENABLE_REQUEST_CODE = 0x00;
    private static final int SILABSER_SET_BAUDDIV_REQUEST_CODE = 0x01;
    private static final int SILABSER_SET_LINE_CTL_REQUEST_CODE = 0x03;
    private static final int SILABSER_SET_MHS_REQUEST_CODE = 0x07;
    // GET_MDMSTS 0x08 Get modem status.
    private static final int SILABSER_GET_MHS_REQUEST_CODE = 0x08;
    private static final int SILABSER_SET_BAUDRATE = 0x1E;
    
    /*
     * SILABSER_IFC_ENABLE_REQUEST_CODE
     */
    private static final int UART_ENABLE = 0x0001;
    private static final int UART_DISABLE = 0x0000;
    
    /*
     * SILABSER_SET_BAUDDIV_REQUEST_CODE
     */
    private static final int BAUD_RATE_GEN_FREQ = 0x384000;
    
    /*
     * SILABSER_SET_MHS_REQUEST_CODE
     */
    private static final int MCR_DTR = 0x0001;
    private static final int MCR_RTS = 0x0002;
    private static final int MCR_ALL = 0x0003;
    
    private static final int CONTROL_WRITE_DTR = 0x0100;
    private static final int CONTROL_WRITE_RTS = 0x0200;    

    /*
     * SILABSER_GET_MHS_REQUEST_CODE AN571 Rev. 0.1 p.11 5.10. GET_MDMSTS (0x08)
     */
    // bit 0: DTR state (as set by host or by handshaking logic in CP210x)
    private static final int GET_MCR_DTR = 0x0001;
    // bit 1: RTS state (as set by host or by handshaking logic in CP210x)
    private static final int GET_MCR_RTS = 0x0002;
    // bit 4: CTS state (as set by end device)
    private static final int GET_MCR_CTS = 0x0010;
    // bit 5: DSR state (as set by end device)
    private static final int GET_MCR_DSR = 0x0020;
    // bit 6: RI state (as set by end device)
    private static final int GET_MCR_RI = 0x0040;
    // bit 7: DCD state (as set by end device).
    private static final int GET_MCR_DCD = 0x0080;

    private UsbEndpoint mReadEndpoint;
    private UsbEndpoint mWriteEndpoint; 
    
    public Cp2102SerialDriver(UsbDevice device, UsbDeviceConnection connection) {
        super(device, connection);
    }
    
    private int setConfigSingle(int request, int value) {
        return mConnection.controlTransfer(REQTYPE_HOST_TO_DEVICE, request, value, 
                0, null, 0, USB_WRITE_TIMEOUT_MILLIS);
    }

    @Override
    public void open() throws IOException {        
        boolean opened = false;
        try {
            for (int i = 0; i < mDevice.getInterfaceCount(); i++) {                
                UsbInterface usbIface = mDevice.getInterface(i);
                if (mConnection.claimInterface(usbIface, true)) {
                    Log.d(TAG, "claimInterface " + i + " SUCCESS");                    
                } else {
                    Log.d(TAG, "claimInterface " + i + " FAIL");
                }
            }                       
            
            UsbInterface dataIface = mDevice.getInterface(mDevice.getInterfaceCount() - 1);
            for (int i = 0; i < dataIface.getEndpointCount(); i++) {
                UsbEndpoint ep = dataIface.getEndpoint(i);
                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                        mReadEndpoint = ep;
                    } else {
                        mWriteEndpoint = ep;
                    }
                }
            }
            
            setConfigSingle(SILABSER_IFC_ENABLE_REQUEST_CODE, UART_ENABLE);
            setConfigSingle(SILABSER_SET_MHS_REQUEST_CODE, MCR_ALL | CONTROL_WRITE_DTR | CONTROL_WRITE_RTS);
            setConfigSingle(SILABSER_SET_BAUDDIV_REQUEST_CODE, BAUD_RATE_GEN_FREQ / DEFAULT_BAUD_RATE);            
//            setParameters(DEFAULT_BAUD_RATE, DEFAULT_DATA_BITS, DEFAULT_STOP_BITS, DEFAULT_PARITY);
            opened = true;
        } finally {
            if (!opened) {
                close();
            }
        }        
    }

    @Override
    public void close() throws IOException {
        setConfigSingle(SILABSER_IFC_ENABLE_REQUEST_CODE, UART_DISABLE);
        mConnection.close();
    }

    @Override
    public int read(byte[] dest, int timeoutMillis) throws IOException {
        final int numBytesRead;
        synchronized (mReadBufferLock) {
            int readAmt = Math.min(dest.length, mReadBuffer.length);
            numBytesRead = mConnection.bulkTransfer(mReadEndpoint, mReadBuffer, readAmt,
                    timeoutMillis);
            if (numBytesRead < 0) {
                // This sucks: we get -1 on timeout, not 0 as preferred.
                // We *should* use UsbRequest, except it has a bug/api oversight
                // where there is no way to determine the number of bytes read
                // in response :\ -- http://b.android.com/28023
                return 0;
            }
            System.arraycopy(mReadBuffer, 0, dest, 0, numBytesRead);
        }
        return numBytesRead;
    }

    @Override
    public int write(byte[] src, int timeoutMillis) throws IOException {
        int offset = 0;

        while (offset < src.length) {
            final int writeLength;
            final int amtWritten;

            synchronized (mWriteBufferLock) {
                final byte[] writeBuffer;

                writeLength = Math.min(src.length - offset, mWriteBuffer.length);
                if (offset == 0) {
                    writeBuffer = src;
                } else {
                    // bulkTransfer does not support offsets, make a copy.
                    System.arraycopy(src, offset, mWriteBuffer, 0, writeLength);
                    writeBuffer = mWriteBuffer;
                }

                amtWritten = mConnection.bulkTransfer(mWriteEndpoint, writeBuffer, writeLength,
                        timeoutMillis);
            }
            if (amtWritten <= 0) {
                throw new IOException("Error writing " + writeLength
                        + " bytes at offset " + offset + " length=" + src.length);
            }

            Log.d(TAG, "Wrote amt=" + amtWritten + " attempted=" + writeLength);
            offset += amtWritten;
        }
        return offset;
    }

    private void setBaudRate(int baudRate) throws IOException {   
        byte[] data = new byte[] {
                (byte) ( baudRate & 0xff),
                (byte) ((baudRate >> 8 ) & 0xff),
                (byte) ((baudRate >> 16) & 0xff),
                (byte) ((baudRate >> 24) & 0xff)
        };
        int ret = mConnection.controlTransfer(REQTYPE_HOST_TO_DEVICE, SILABSER_SET_BAUDRATE, 
                0, 0, data, 4, USB_WRITE_TIMEOUT_MILLIS);
        if (ret < 0) {
            throw new IOException("Error setting baud rate.");
        }
    }

    @Override
    public void setParameters(int baudRate, int dataBits, int stopBits, int parity)
            throws IOException {
        setBaudRate(baudRate);
                
        int configDataBits = 0;
        switch (dataBits) {
            case DATABITS_5:
                configDataBits |= 0x0500;
                break;
            case DATABITS_6:
                configDataBits |= 0x0600;
                break;
            case DATABITS_7:
                configDataBits |= 0x0700;
                break;
            case DATABITS_8:
                configDataBits |= 0x0800;
                break;
            default:
                configDataBits |= 0x0800;
                break;
        }
        setConfigSingle(SILABSER_SET_LINE_CTL_REQUEST_CODE, configDataBits);

        int configParityBits = 0; // PARITY_NONE
        switch (parity) {
            case PARITY_ODD:
                configParityBits |= 0x0010;
                break;
            case PARITY_EVEN:
                configParityBits |= 0x0020;
                break;            
        }
        setConfigSingle(SILABSER_SET_LINE_CTL_REQUEST_CODE, configParityBits);
        
        int configStopBits = 0;
        switch (stopBits) {
            case STOPBITS_1:
                configStopBits |= 0;
                break;
            case STOPBITS_2:
                configStopBits |= 2;
                break;
        }
        setConfigSingle(SILABSER_SET_LINE_CTL_REQUEST_CODE, configStopBits);        
    }

    @Override
    public int getModemStatus() throws IOException {
        byte[] buffer = new byte[1] ;
        int res;
        mConnection.controlTransfer(REQTYPE_DEVICE_TO_HOST, SILABSER_GET_MHS_REQUEST_CODE, 0,
                0, buffer, 1, USB_WRITE_TIMEOUT_MILLIS);
        /*
        Log.d(TAG, "GET_MDMSTS (0x08)"
                + (((buffer[0] & GET_MCR_DCD) == 0) ? " dcd" : " DCD")
                + (((buffer[0] & GET_MCR_CTS) == 0) ? " cts" : " CTS")
                + (((buffer[0] & GET_MCR_RTS) == 0) ? " rts" : " RTS")
                + (((buffer[0] & GET_MCR_DSR) == 0) ? " dsr" : " DSR")
                + (((buffer[0] & GET_MCR_DTR) == 0) ? " dtr" : " DTR")
                + (((buffer[0] & GET_MCR_RI) == 0) ? " ri" : " RI")
                );
        */
        res = MS_DCD_MASK | MS_CTS_MASK | MS_RTS_MASK | MS_DSR_MASK | MS_DTR_MASK | MS_RI_MASK;
        res <<= 8;
        res = res | (((buffer[0] & GET_MCR_DCD) == 0) ? 0 : MS_DCD_MASK)
                | (((buffer[0] & GET_MCR_CTS) == 0) ? 0 : MS_CTS_MASK)
                | (((buffer[0] & GET_MCR_RTS) == 0) ? 0 : MS_RTS_MASK)
                | (((buffer[0] & GET_MCR_DSR) == 0) ? 0 : MS_DSR_MASK)
                | (((buffer[0] & GET_MCR_DTR) == 0) ? 0 : MS_DTR_MASK)
                | (((buffer[0] & GET_MCR_RI) == 0) ? 0 : MS_RI_MASK);
        lastModemStatus = res;
        return res;
    }

    @Override
    public void setRTS(boolean value) throws IOException {
        // TODO DTR and RTS values can be set only if the current handshaking
        // state of the interface allows direct control of the modem control
        // lines.
        setConfigSingle(SILABSER_SET_MHS_REQUEST_CODE,
                (CONTROL_WRITE_RTS | (value ? MCR_RTS : 0)));
    }

    @Override
    public void setDTR(boolean value) throws IOException {
        // TODO DTR and RTS values can be set only if the current handshaking
        // state of the interface allows direct control of the modem control
        // lines.
        setConfigSingle(SILABSER_SET_MHS_REQUEST_CODE,
                (CONTROL_WRITE_DTR | (value ? MCR_DTR : 0)));
    }

    public static Map<Integer, int[]> getSupportedDevices() {
        final Map<Integer, int[]> supportedDevices = new LinkedHashMap<Integer, int[]>();
        supportedDevices.put(Integer.valueOf(UsbId.VENDOR_SILAB),
                new int[] {
                        UsbId.SILAB_CP2102
                });
        return supportedDevices;
    }

}
