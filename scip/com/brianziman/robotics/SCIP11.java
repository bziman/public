/**
 * SCIP 1.1 Java Interface for Hokuyo URG-04LX Laser Sensor.
 * Copyright 2007 - Brian Ziman [bziman(at)acm.org]
 * http://www.brianziman.com/
 */
package com.brianziman.robotics;

import java.net.*;
import java.io.*;
import java.util.*;

/**
 * <h1>SCIP 1.1 Java Interface for Hokuyo URG-04LX Laser Sensor</h1>
 * <p>
 * Copyright 2007 - <a href="http://www.brianziman.com/">Brian Ziman</a>
 * </p>
 * <p>
 * This class implements the SCIP 1.1 protocol for the Hokuyo URG-04LX
 * Laser sensor.  It communicates over a socket to Prof. Sean Luke's
 * <a href="http://cs.gmu.edu/~sean/cs685/uploads/Main/serialdaemon.c.zip">serialdaemon utility</a>
 * which communicates directly with the serial port.
 * </p>
 * <p>
 * The JavaDoc for the public methods will provide useful information 
 * for end users.  The more useful public methods may throw a runtime
 * exception, SCIPException is something weird happens, for example,
 * if the connection to the serialdaemon dies, or if invalid data is
 * received from the laser.  The response in these situations is undefined,
 * which is why it is a runtime exception that does not and should not
 * be explicitely caught, unless you're really sure you know how to handle it.
 * </p>
 * <p>
 * Each function returns a status code, which according to the spec,
 * indicates merely success or failure.  Whenever a failure is recorded,
 * a message is sent to standard output.  On the off chance that the
 * status code is important, there is a public method to retreive the last
 * error status, which resets the status field to zero.  I wouldn't count
 * on it having anything meaningful, though.
 * </p>
 * <p>
 * This interface is implemented precisely according to the SCIP spec,
 * however there are at least a few obvious mistakes in the spec, and a few
 * things that are probably mistakes.  In cases where I wasn't sure, I
 * followed the spec, and I'm hoping testing will reveal whether the spec
 * was indeed correct, or not.
 * </p>
 * <p>
 * The following information is based on the URG Series Communication
 * Protocol Specification (SCIP Version 1.1).  
 * </p>
 * <p>
 * Host to Sensor commands are in the format:
 * </p>
 * <p><code>|Command|Parameter|LF/CR|</code></p>
 * <p>
 * The Sensor will then respond in the format:
 * </p>
 * <p><code>|Command|Parameter|LF|Status|LF|Data|LF|LF|</code></p>
 * <ul>
 * <li>The response echos the command and parameter sent by the host.</li>
 * <li>LF = 0x0A (CR = 0x0D)</li>
 * <li>Status OK is '0' (0x30). Other status indicates error.</li>
 * <li>If data is larger than 64 bytes, an LF separates the data after
 *   every 64 bytes.</li>
 * <li>Two consective LF's indicate end of data.</li>
 * </ul>
 * <h1>Commands:</h1>
 * <h2>Version Information</h2>
 * <pre>
 * Command is 'V' (0x56)
 * No parameter.
 * Request is:
 * |'V'|LF/CR|
 *
 * Response is:
 * |'V'         |LF|
 * |Status      |LF|
 * |Vendor Info |LF|
 * |Product Info|LF|
 * |Firmware Ver|LF|
 * |Protocol Ver|LF|
 * |Serial Num  |LF|LF|
 * </pre>
 * <h2>Laser Illumination Control</h2>
 * <pre>
 * Command is 'L' (0x4C)
 * Parameter is 1 byte Control Code.
 * '1' switches laser on.
 * '0' switches laser off.
 * (Are these 0x31 and 0x30? or 0x01 and 0x00? Need to check.)
 * Request is:
 * |'L'|'0'/'1'|LF/CR|
 *
 * Response is:
 * |'L'|'0'/'1'|LF|Status|LF|LF|
 * </pre>
 * <h2>Communication Settings (Ignored when using USB connection)</h2>
 * <pre>
 * Command is 'S' (0x53)
 * Parameter is 6 ascii digit baud rate, followed by 7 digit reserved.
 * "019200" sets 19.2kbps (default)
 * "057600" sets 57.6kbps
 * "115200" sets 115.2kbps
 * "250000" sets 250.0kbps
 * "500000" sets 500.0kbps
 * "750000" sets 750.0kbps
 * Request is:
 * |'S'|aaaaaa|rrrrrrr|LF/CR|
 *
 * Response is:
 * |'S'|aaaaaa|rrrrrrr|Status|LF|LF|  (why is there no LF before Status here?)
 * </pre>
 * <h2>Distance Data Acquisition</h2>
 * <pre>
 * Command is 'G' (0x47)
 * Parameters are 3 digit starting point, 3 digit ending point,
 *            and 2 digit cluster count.
 *            Starting and ending point range from 0..768.
 *            Cluster count is from 0..99.
 *            (Encoded using ascii digits 0x30-0x39).
 * Request is:
 * |'G'|sss|eee|cc|LF/CR|
 * 
 * Response is:
 * |'G'|sss|eee|cc|LF|Status|Data|LF|LF|
 * Where data is broken into blocks of 64 bytes followed by LF.
 *
 * Sensor measures range of 4095mm with 1mm resolution.
 * Data point is expressed with 12 bits (0..4095).  12 bit
 * value is encoded as follows:
 *
 * 1234mm = 010011 010010(binary)
 *        = 0x13 0x12
 *  +0x30 = 0x43 0x42 = 'C','D' (ascii)
 *
 * Starting and ending point refer to steps in the field of
 * angular detection.  This field is a range of 240 degrees,
 * with a dead zone of 120 degrees at the rear.
 * Step 0 is at -135 degrees from front (right), Step 384 is at 0 degrees
 * (facing front), and Step 768 is at +135 degrees (left).
 * Step 0 and 768 are both in the deadzone.
 * Measurable range is from Step 44 to Step 725.
 * Each step has a resolution of 360deg/1024 = 0.3515625 deg.
 *
 * I assume that the "cluster" parameter, which refers to the
 * number of neighboring points that are grouped as a cluster
 * would reduce the amount of data returned.
 *
 * For example, if start is at "44" and end is at "725" and
 * cluster is "01", then you would expect some 680 data points.
 *
 * If cluster were "20", then you might expect only 34 data points,
 * each of which is perhaps the average of a 7 degree arc?
 *
 * Sensors will return values between 20mm and 4094mm.  Anything
 * less than 20mm will result in an error code at that data point.
 *
 * Error codes for data points are (don't ask me what they mean):
 *
 *  0    "Possibility of detected object is at 22m"
 *  1-5  "Reflected light has low intensity"
 *  6    "Possibility of detected object is at 5.7m"
 *  7    "Distance data on the preceding and succeeding steps have errors"
 *  8    "Others"
 *  9    "The same step had error in the last two scan"
 * 10-15 "Others"
 * 16    "Possibility of detected object is in the range 4096mm"
 * 17    "Others"
 * 18    "Unspecified"
 * 19    "Non-Measurable Distance"
 * </pre>
 */
public class SCIP11 {
    private Socket gSocket;
    private InputStream gInput;
    private OutputStream gOutput;
    private byte gLastError; // cleared by call to getLastError()

    private boolean debug = false;
    private StringBuffer debugSB = new StringBuffer();
    Thread debugBufferMonitor;

    public SCIP11(int port) throws IOException {
        debug = "true".equals(System.getProperty("scip11.debug"));
        gSocket = new Socket("localhost",port);
        gInput = gSocket.getInputStream();
        gOutput = gSocket.getOutputStream();
        if(debug) {
            debugBufferMonitor = new Thread() {
                public void run() {
                    for(;;) {
                        StringBuffer tmp = debugSB;
                        debugSB = new StringBuffer();
                        System.out.print(tmp.toString());
                        try { sleep(500); } catch (Exception e) { break; }
                    }
                }
            };
            debugBufferMonitor.setDaemon(true);
            debugBufferMonitor.start();
        };
    }

    /** Enable the laser. 
     * Note, an error is generated if you try to enable the
     * laser if it is already enabled, or disable it if it's already
     * disabled.  It logs an "Error" warning to stderr, but does
     * not throw an exception.
     */
    public void enable() {
        doLaserCommand(true);
    }

    /** Disable the laser. 
     * Note, an error is generated if you try to enable the
     * laser if it is already enabled, or disable it if it's already
     * disabled.  It logs an "Error" warning to stderr, but does
     * not throw an exception.
     */
    public void disable() {
        doLaserCommand(false);
    }

    /** 
     * Set the baud rate using one of the BAUD_n constants.
     * This function has no effect when using USB for communication.
     */
    public void setBaud(String baud) {
        doSettingsCommand(baud);
    }

    private HashMap<String,String> gVersionInfo = null;

    /** Returns static vendor information from laser. */
    public String getVendorInfo() {
        // no need to synchronized this, because doVersionCommand is
        // already synchronized.  It is possible that two version requests
        // could come in with gVersionInfo null, which will result in two
        // (safely) sequential calls to doVersionCommand(), which could over-
        // write gVersionInfo.  But that's okay, because it will point to
        // the old valid one, or the new valid one, but never to an invalid
        // one.
        if(gVersionInfo == null) gVersionInfo = doVersionCommand();
        return gVersionInfo.get(KEY_VENDOR);
    }

    /** Returns static product information from laser. */
    public String getProductInfo() {
        if(gVersionInfo == null) gVersionInfo = doVersionCommand();
        return gVersionInfo.get(KEY_PRODUCT);
    }

    /** Returns static firmware version from laser. */
    public String getFirmwareVersion() {
        if(gVersionInfo == null) gVersionInfo = doVersionCommand();
        return gVersionInfo.get(KEY_FIRMWARE);
    }

    /** Returns static protocol version from laser. */
    public String getProtocolVersion() {
        if(gVersionInfo == null) gVersionInfo = doVersionCommand();
        return gVersionInfo.get(KEY_PROTOCOL);
    }

    /** Returns static sensor serial number from laser. */
    public String getSensorSerialNumber() {
        if(gVersionInfo == null) gVersionInfo = doVersionCommand();
        return gVersionInfo.get(KEY_SERIAL);
    }

    /** Returns undocumented static status field laser. */
    public String getSensorStatus() {
        if(gVersionInfo == null) gVersionInfo = doVersionCommand();
        return gVersionInfo.get(KEY_STATUS);
    }

    /**
     * Returns the range detected at the given angle, measured in degrees,
     * and within the angular range of the sensor, -120 degrees to +120
     * degrees.
     * Zero degrees is forward, -120 degrees is back and to the right,
     * +120 degrees is back and to the left.
     *
     * According to the Hokuyo data sheet, the scan time is 100ms / scan.
     * That means, we can cache data for up to that long.
     *
     * If you want to collect a whole set of data at a time, call
     * doDistanceCommand() directly.
     */
    public synchronized int getRange(double angle) {
        angle -= gAngleOffset; // convert to proper zero-forward format
        if(angle < -180) angle += 360; // wrap particularly negative values
        if (angle < -120 || angle > 120)
            throw new IllegalArgumentException("Angle out of range: "+angle);
        int slot = (int)((angle+135) * 1024/360d) - 44;
        if(System.currentTimeMillis() > gDataExpireTime) {
            gData = doDistanceCommand(44,725,1);
            gDataExpireTime = System.currentTimeMillis() + gScanTime;
        }
        if(slot < 0) slot = 0;
        if(slot >= gData.size()) slot = gData.size() - 1;
        return gData.get(slot);
    }
    private ArrayList<Integer> gData = null;
    private long gDataExpireTime = 0;

    private long gScanTime = 100; // about ten hz
    /**
     * This method specifies the number of milliseconds per scan of the laser.
     * This defaults to 100ms, as specified by the Hokuyo data sheet,
     * however you may have a faster laser (or a slower one), so you can
     * adjust the scan time here.
     */
    public void setScanTime(long l) { gScanTime = l; }
    public long getScanTime() { return gScanTime; }

    private double gAngleOffset = 0;
    /**
     * The Hokuyo laser ranger considers forward to be zero degrees,
     * however, some systems, such as the sensor array on the Pioneer
     * robots, consider zero degrees to be in other locations (for
     * the Pioneers, that would be on the right).  This method allows
     * you to "set" the zero direction.  The argument to this method
     * tells the API what angle from forward you want to consider the
     * zero angle.  So to match the Pioneer, you would pass in 90.
     * Default is zero.
     */
    public void setAngleOffset(double d) { gAngleOffset = d; }
    /**
     * Retreive the angle offset.
     */
    public double getAngleOffset() { return gAngleOffset; }

    // ==== INTERNAL LASER INTERFACE VIA COM FUNCTIONS ====

    /**
     * <p>
     * Internal method to return a map of version info from the laser.
     * Most users should access this data via getVendorInfo(),
     * getProductInfo(), getFirmwareVersion(), getProtocolVersion(),
     * and getSensorSerialNumber().
     * </p>
     * <pre>
     * The keys are: 
     * KEY_VENDOR   = "Vender Information" // sic 
     * KEY_PRODUCT  = "Product Information" 
     * KEY_FIRMWARE = "Firmware Version" 
     * KEY_PROTOCOL = "Protocol Version" 
     * KEY_SERIAL   = "Sensor Serial Number"
     * KEY_STATUS   = "Undocumented Status Field"
     *
     * The values are informational strings.
     * </pre>
     */
    public synchronized HashMap<String,String> doVersionCommand() {
        write(VERSION);
        write(LF);
        flush();

        readCommand(VERSION);
        readLF();
        readStatus("Version"); // consumes LF

        HashMap<String,String> map = new HashMap<String,String>();
        map.put(KEY_VENDOR, readString()); // consumes LF...
        map.put(KEY_PRODUCT, readString());
        map.put(KEY_FIRMWARE, readString());
        map.put(KEY_PROTOCOL, readString());
        map.put(KEY_SERIAL, readString());
        map.put(KEY_STATUS, readString());
        readLF(); // end of response
        return map;
    }

    /**
     * Internal method to enable or disable the laser.  
     * Most users should use enable() or disable() methods.
     * Note, an error is generated if you try to enable the
     * laser if it is already enabled, or disable it if it's already
     * disabled.  It logs an "Error" warning to stderr, but does
     * not throw an exception.
     */
    public synchronized void doLaserCommand(boolean enable) {
        write(LASER);
        write(enable?LASER_ON:LASER_OFF);
        write(LF);
        flush();

        readCommand(LASER);
        assertByte(readByte(),enable?LASER_ON:LASER_OFF);
        readLF();
        readStatus("Laser"); // consumes LF
        readLF(); // end of response
    }

    /**
     * Internal method to configures the baud rate of the device.
     * The argument is one of 019200, 057600, 115200, 250000, 500000, 750000.
     * This method has no effect when communicating via USB.
     * Most users should use the method setBaud() for this function.
     */
    public synchronized void doSettingsCommand(String baud) {
        if(!BAUD_RATES.containsKey(baud)) {
            throw new SCIPException("Invalid baud rate: "+baud);
        }
        write(SETTINGS);
        write(BAUD_RATES.get(baud));
        write(RESERVED);
        write(LF);
        flush();

        readCommand(SETTINGS);
        assertBytes(readBytes(6),BAUD_RATES.get(baud));
        assertBytes(readBytes(7),RESERVED);
        // no LF here according to spec!
        readStatus("Settings"); // consumes LF
        readLF(); // end of response
    }

    /**
     * <p>
     * Internal method to request sensor data from the laser.
     * The getRange() method provides simpler access to range data.
     * </p>
     * <p>
     * Starting and ending point range from 0..768.
     * Cluster count is from 0..99.
     * </p>
     * <p>
     * Sensor measures range of 4095mm with 1mm resolution.
     * </p>
     * <p>
     * Starting and ending point refer to steps in the field of
     * angular detection.  This field is a range of 240 degrees,
     * with a dead zone of 120 degrees at the rear.
     * Step 0 is at -135 degrees from front (right), Step 384 is at 0 degrees
     * (facing front), and Step 768 is at +135 degrees (left).
     * Step 0 and 768 are both in the deadzone.
     * Measurable range is from Step 44 to Step 725.
     * Each step has a resolution of 360deg/1024 = 0.3515625 deg.
     * </p>
     * <p>
     * I assume that the "cluster" parameter, which refers to the
     * number of neighboring points that are grouped as a cluster
     * would reduce the amount of data returned.
     * </p>
     * <p>
     * For example, if start is at "44" and end is at "725" and
     * cluster is "01", then you would expect some 680 data points.
     * </p>
     * <p>
     * If cluster were "20", then you might expect only 34 data points,
     * each of which is perhaps the average of a 7 degree arc?
     * </p>
     * <p>
     * Sensors will return values between 20mm and 4094mm.  Anything
     * less than 20mm will result in an error code at that data point.
     * </p>
     * <p>
     * Error codes for data points are (don't ask me what they mean):
     * </p>
     * <pre>
     *  0    "Possibility of detected object is at 22m"
     *  1-5  "Reflected light has low intensity"
     *  6    "Possibility of detected object is at 5.7m"
     *  7    "Distance data on the preceding and succeeding steps have errors"
     *  8    "Others"
     *  9    "The same step had error in the last two scan"
     * 10-15 "Others"
     * 16    "Possibility of detected object is in the range 4096mm"
     * 17    "Others"
     * 18    "Unspecified"
     * 19    "Non-Measurable Distance"
     * </pre>
     */
    public synchronized ArrayList<Integer> doDistanceCommand(int start, int end, int cluster) {
        if(start < MIN_STEP || start > MAX_STEP || end < MIN_STEP || end > MAX_STEP ||
           end < start || cluster < MIN_CLUSTER || cluster > MAX_CLUSTER) {
            throw new SCIPException("Invalid distance parameters: start is "+
                    start+" and end is "+end+", whcih must be between "+
                    MIN_STEP+" and "+MAX_STEP+", and cluster is "+cluster+
                    ", which must be between "+MIN_CLUSTER+" and "+MAX_CLUSTER);
        }
        write(DISTANCE);
        byte[] ss = new byte[3];
        ss[0] = (byte)(0x30 + (start / 100) % 10); 
        ss[1] = (byte)(0x30 + (start / 10) % 10);
        ss[2] = (byte)(0x30 + start % 10);
        write(ss);
        byte[] ee = new byte[3];
        ee[0] = (byte)(0x30 + (end / 100) % 10); 
        ee[1] = (byte)(0x30 + (end / 10) % 10);
        ee[2] = (byte)(0x30 + end % 10);
        write(ee);
        byte[] cc = new byte[2];
        cc[0] = (byte)(0x30 + (cluster / 10) % 10);
        cc[1] = (byte)(0x30 + cluster % 10);
        write(cc);
        write(LF);
        flush();

        readCommand(DISTANCE);
        assertBytes(readBytes(3),ss);
        assertBytes(readBytes(3),ee);
        assertBytes(readBytes(2),cc);
        readLF();
        readStatus("Distance"); // consumes LF

        // read data
        ArrayList<Integer> ret = new ArrayList<Integer>();
        int count = 0;
        for(;;) {
            // each data point is encoded as a pair of ascii bytes
            int c1 = readByte(); count++;
            if(c1 == LF) break; // break when you see an LF in the 65th spot
            int c2 = readByte(); count++;
            int data = ((c1-0x30)<<6) | (c2 - 0x30);
            ret.add(data);
            if(count == 64) { // after 64 bytes...
                count = 0;
                readLF(); // consume a LF
            }
        }
        if(count < 64) readLF(); // if not on a boundary, we've got an extra LF
        return ret;
    }

    /**
     * Return the last error code byte generated by the laser and
     * clear the code.
     */
    public synchronized byte getLastError() {
        byte ch = gLastError;
        gLastError = 0;
        return ch;
    }

    // ==== UTILITY FUNCTIONS =========================

    private void write(byte b) {
        try {
            gOutput.write(b);
        } catch (IOException e) {
            throw new SCIPException(e);
        }
    }
    private void write(byte b[]) {
        try {
            gOutput.write(b);
        } catch (IOException e) {
            throw new SCIPException(e);
        }
    }
    private void flush() {
        try {
            gOutput.flush();
        } catch (IOException e) {
            throw new SCIPException(e);
        }
    }


    /**
     * Reads command from input, and assert that it is correct
     */
    private void readCommand(byte expected) {
        byte got = readByte();
        assertByte(got, expected);
    }

    /**
     * Reads status from input, followed by LF, returns byte of status.
     */
    private void readStatus(String cmd) {
        byte ch = readByte();
        if(!checkStatus(ch)) {
            error(cmd,ch);
            gLastError = ch;
        }
        readLF();
    }

    /**
     * Consumes a byte, and asserts that it is an LF.
     */
    private void readLF() {
        byte lf = readByte();
        assertByte(lf,LF);
    }

    private void assertByte(byte got, byte expected) {
        if(got != expected) throw new SCIPException("Unexpected input, received "+got+" instead of "+expected+".");
    }
    private void assertBytes(byte[] got, byte[] expected) {
        if(!Arrays.equals(got,expected)) throw new SCIPException("Unexpected input, received "+new String(got)+" instead of "+new String(expected)+".");
    }

    /**
     * Reads a single byte of input.
     */
    private byte readByte() {
        try {
            int ch = gInput.read();
            if(debug) debugSB.append((char)ch);
            if(ch == -1) throw new SCIPException("Unexpected end of input.");
            return (byte)ch;
        } catch (IOException e) {
            throw new SCIPException(e);
        }
    }

    /**
     * Reads the specified number of bytes of input.
     */
    private byte[] readBytes(int n) {
        try {
            byte[] buffer = new byte[n];
            int count = gInput.read(buffer);
            if(count < n) throw new SCIPException("Unexpected end of input.");
            return buffer;
        } catch (IOException e) {
            throw new SCIPException(e);
        }
    }

    /**
     * Returns true of status is okay (0x30), and
     * false otherwise.
     */
    private boolean checkStatus(byte status) {
        return status == STATUS_OK;
    }

    /**
     * Reads an ascii string (LF terminated).
     */
    private String readString() {
        StringBuilder sb = new StringBuilder();
        byte ch;
        for(;;) {
            ch = readByte();
            if(ch == LF) break;
            sb.append((char)ch);
        }
        if(sb.length() == 0) return null; // no empty strings!
        return sb.toString(); // otherwise return string
    }

    private void error(String cmdName, byte status) {
        System.err.println("Error: "+cmdName+" received status "+status);
    }

    /**
     * Only useful for testing.
     */
    public static void main(String args[]) throws Exception {
        SCIP11 laser = new SCIP11(Integer.parseInt(args[0]));
        System.out.println(laser.doVersionCommand());
        laser.doLaserCommand(true);
        ArrayList<Integer> list = laser.doDistanceCommand(44,725,20);
        System.out.println("I think this should return 35 data points. Got "+
                list.size());
        System.out.println(list);
        list = laser.doDistanceCommand(0,768,1);
        System.out.println("I think this should return 769 data points. Got "+
                list.size());
        System.out.println("Index 0..43 and 726-768 shouldn't contain useful values.");
        System.out.println(list);
        laser.doLaserCommand(false);
    }

    /**
     * If a data point less than 20 is returned, it
     * corresponds to an error code as documented.
     * This method returns the string associated with
     * that error code.
     */
    public static final String getErrorFromData(int c) {
        switch(c) {
            case  0: return "Possibility of detected object is at 22m";
            case  1:
            case  2:
            case  3:
            case  4:
            case  5: return "Reflected light has low intensity";
            case  6: return "Possibility of detected object is at 5.7m";
            case  7: return "Distance data on the preceding and succeeding steps have errors";
            case  8: return "Others";
            case  9: return "The same step had error in the last two scan";
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15: return "Others";
            case 16: return "Possibility of detected object is in the range 4096mm";
            case 17: return "Others";
            case 18: return "Unspecified";
            case 19: return "Non-Measurable Distance"; // i.e. -/+135..-/+121
            default: return null;
        }
    }

    private static final byte VERSION   = (byte)'V';
    private static final byte LASER     = (byte)'L';
    private static final byte SETTINGS  = (byte)'S'; 
    private static final byte DISTANCE  = (byte)'G';
    private static final byte LF        = (byte)0x0a;
    private static final byte LASER_ON  = (byte)0x31;
    private static final byte LASER_OFF = (byte)0x30;

    // Known statuses so far...
    // OK
    private static final byte STATUS_OK = (byte)0x30;
    // Trying to enable a laser that is already enabled.
    private static final byte STATUS_02 = (byte)0x32;
    // Trying to get data from a disabled laser.
    private static final byte STATUS_06 = (byte)0x36;

    private static final byte[] RESERVED = "0000000".getBytes();

    /** 
     * Minimum step value for laser that corresponds with -135 degrees
     * to the right of forward.
     */
    public static final int MIN_STEP = 0;

    /** 
     * Maximum step value for laser that corresponds with 135 degrees
     * to the left of forward.
     */
    public static final int MAX_STEP = 768;

    /** Smallest number of clusters permitted.  */
    public static final int MIN_CLUSTER = 0;

    /** Largest number of clusters permitted.  */
    public static final int MAX_CLUSTER = 99;

    /** Vendor Information.  */
    public static final String KEY_VENDOR   = "Vender Information"; // sic
    /** Product Information.  */
    public static final String KEY_PRODUCT  = "Product Information";
    /** Firmware Version.  */
    public static final String KEY_FIRMWARE = "Firmware Version";
    /** Protocol Version.  */
    public static final String KEY_PROTOCOL = "Protocol Version";
    /** Sensor Serial Number. */
    public static final String KEY_SERIAL   = "Sensor Serial Number";
    /** Undocumented Status Field. */
    public static final String KEY_STATUS   = "Undocumented Status Field";

    /** 19.2kbps baud rate. */
    public static final String BAUD_019200 = "019200";
    /** 57.6kbps baud rate. */
    public static final String BAUD_057600 = "057600";
    /** 115.2kbps baud rate. */
    public static final String BAUD_115200 = "115200";
    /** 250kbps baud rate. */
    public static final String BAUD_250000 = "250000";
    /** 500kbps baud rate. */
    public static final String BAUD_500000 = "500000";
    /** 750kbps baud rate. */
    public static final String BAUD_750000 = "750000";

    private static final HashMap<String,byte[]> BAUD_RATES;
    static {
        BAUD_RATES = new HashMap<String,byte[]>();
        BAUD_RATES.put(BAUD_019200,BAUD_019200.getBytes());
        BAUD_RATES.put(BAUD_057600,BAUD_057600.getBytes());
        BAUD_RATES.put(BAUD_115200,BAUD_115200.getBytes());
        BAUD_RATES.put(BAUD_250000,BAUD_250000.getBytes());
        BAUD_RATES.put(BAUD_500000,BAUD_500000.getBytes());
        BAUD_RATES.put(BAUD_750000,BAUD_750000.getBytes());
    }
}
