package org.client.scrcpy.utils;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdbHelperTest {

    @Test
    public void parseAdbDevices_normal_output() {
        String output = "List of devices attached\n"
                + "192.168.1.5:5555\tdevice\n"
                + "R58M12345\tdevice\n"
                + "[2001:db8::1]:5555\toffline\n"
                + "\n";
        Map<String, String> devices = AdbHelper.parseAdbDevices(output);
        assertEquals(3, devices.size());
        assertEquals("device", devices.get("192.168.1.5:5555"));
        assertEquals("device", devices.get("R58M12345"));
        assertEquals("offline", devices.get("[2001:db8::1]:5555"));
    }

    @Test
    public void parseAdbDevices_empty_and_null() {
        assertTrue(AdbHelper.parseAdbDevices("").isEmpty());
        assertTrue(AdbHelper.parseAdbDevices(null).isEmpty());
        assertTrue(AdbHelper.parseAdbDevices("List of devices attached\n").isEmpty());
    }

    @Test
    public void isTcpSerial_detects_host_port_serials() {
        assertTrue(AdbHelper.isTcpSerial("192.168.1.5:5555"));
        assertTrue(AdbHelper.isTcpSerial("[2001:db8::1]:5555"));
        assertFalse(AdbHelper.isTcpSerial("R58M12345"));
        assertFalse(AdbHelper.isTcpSerial("emulator-5554"));
        assertFalse(AdbHelper.isTcpSerial(""));
        assertFalse(AdbHelper.isTcpSerial(null));
        assertFalse(AdbHelper.isTcpSerial("192.168.1.5:"));
        assertFalse(AdbHelper.isTcpSerial("192.168.1.5:55aa"));
    }
}