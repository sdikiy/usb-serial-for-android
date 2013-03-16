/**
 * 
 */
package com.hoho.android.usbserial.driver;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

/**
 * @author
 *
 */
public class Pl2303SerialDriver extends CommonUsbSerialDriver {

    /**
     * @param device
     * @param connection
     */
    public Pl2303SerialDriver(UsbDevice device, UsbDeviceConnection connection) {
        super(device, connection);
        // TODO Auto-generated constructor stub
    }

    /* (non-Javadoc)
     * @see com.hoho.android.usbserial.driver.CommonUsbSerialDriver#open()
     */
    @Override
    public void open() throws IOException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.hoho.android.usbserial.driver.CommonUsbSerialDriver#close()
     */
    @Override
    public void close() throws IOException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.hoho.android.usbserial.driver.CommonUsbSerialDriver#read(byte[], int)
     */
    @Override
    public int read(byte[] dest, int timeoutMillis) throws IOException {
        // TODO Auto-generated method stub
        return 0;
    }

    /* (non-Javadoc)
     * @see com.hoho.android.usbserial.driver.CommonUsbSerialDriver#write(byte[], int)
     */
    @Override
    public int write(byte[] src, int timeoutMillis) throws IOException {
        // TODO Auto-generated method stub
        return 0;
    }

    /* (non-Javadoc)
     * @see com.hoho.android.usbserial.driver.CommonUsbSerialDriver#setBaudRate(int)
     */
    @Override
    public int setBaudRate(int baudRate) throws IOException {
        // TODO Auto-generated method stub
        return 0;
    }

    /* (non-Javadoc)
     * @see com.hoho.android.usbserial.driver.CommonUsbSerialDriver#setParameters(int, int, int, int)
     */
    @Override
    public void setParameters(int baudRate, int dataBits, int stopBits, int parity)
            throws IOException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.hoho.android.usbserial.driver.CommonUsbSerialDriver#getModemStatus()
     */
    @Override
    public int getModemStatus() throws IOException {
        // TODO Auto-generated method stub
        return 0;
    }

    /* (non-Javadoc)
     * @see com.hoho.android.usbserial.driver.CommonUsbSerialDriver#setRTS(boolean)
     */
    @Override
    public void setRTS(boolean value) throws IOException {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.hoho.android.usbserial.driver.CommonUsbSerialDriver#setDTR(boolean)
     */
    @Override
    public void setDTR(boolean value) throws IOException {
        // TODO Auto-generated method stub

    }

    public static Map<Integer, int[]> getSupportedDevices() {
        final Map<Integer, int[]> supportedDevices = new LinkedHashMap<Integer, int[]>();
        supportedDevices.put(Integer.valueOf(UsbId.VENDOR_PROLIFIC),
                new int[] {
                        UsbId.PROLIFIC_PL2303
                });
        return supportedDevices;
    }

}
