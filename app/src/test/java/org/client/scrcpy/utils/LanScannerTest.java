package org.client.scrcpy.utils;

import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LanScannerTest {

    private static LanScanner.NetRange range(String network, String local, int prefix, String label) {
        return new LanScanner.NetRange(ip(network), ip(local), prefix, label);
    }

    private static byte[] ip(String s) {
        String[] parts = s.split("\\.");
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            out[i] = (byte) Integer.parseInt(parts[i]);
        }
        return out;
    }

    @Test
    public void networkAddress_masks_correctly() {
        assertArrayEquals(ip("192.168.1.0"), LanScanner.networkAddress(ip("192.168.1.73"), 24));
        assertArrayEquals(ip("192.168.0.0"), LanScanner.networkAddress(ip("192.168.1.73"), 16));
        assertArrayEquals(ip("192.0.0.0"), LanScanner.networkAddress(ip("192.168.1.73"), 8));
        assertArrayEquals(ip("192.168.1.72"), LanScanner.networkAddress(ip("192.168.1.73"), 30));
    }

    @Test
    public void buildCandidates_full_class_c_subnet() {
        List<LanScanner.NetRange> ranges = Collections.singletonList(
                range("192.168.1.0", "192.168.1.50", 24, "Wi-Fi (wlan0)"));
        List<String> candidates = LanScanner.buildCandidates(ranges, LanScanner.MAX_HOSTS_PER_RANGE);

        // .0 (network) and .255 (broadcast) excluded, .50 (self) excluded -> 253
        assertEquals(253, candidates.size());
        assertTrue(candidates.contains("192.168.1.1"));
        assertTrue(candidates.contains("192.168.1.254"));
        assertFalse(candidates.contains("192.168.1.0"));
        assertFalse(candidates.contains("192.168.1.50"));
        assertFalse(candidates.contains("192.168.1.255"));
    }

    @Test
    public void buildCandidates_larger_subnet_capped() {
        List<LanScanner.NetRange> ranges = Collections.singletonList(
                range("192.168.0.0", "192.168.0.5", 16, "Hotspot (ap0)"));
        List<String> candidates = LanScanner.buildCandidates(ranges, LanScanner.MAX_HOSTS_PER_RANGE);

        // .0.1..0.254 swept, .0.5 (self) excluded and the /16 broadcast
        // (.255.255) is outside the window -> 253
        assertEquals(253, candidates.size());
        assertEquals("192.168.0.1", candidates.get(0));
        assertEquals("192.168.0.254", candidates.get(252));
        assertFalse(candidates.contains("192.168.0.5"));
    }

    @Test
    public void buildCandidates_deduplicates_overlapping_ranges() {
        LanScanner.NetRange a = range("192.168.1.0", "192.168.1.50", 24, "Wi-Fi (wlan0)");
        LanScanner.NetRange b = range("192.168.1.0", "192.168.1.50", 24, "Hotspot (ap0)");
        List<String> candidates = LanScanner.buildCandidates(Arrays.asList(a, b),
                LanScanner.MAX_HOSTS_PER_RANGE);
        assertEquals(253, candidates.size());
    }

    @Test
    public void buildCandidates_skips_local_ips_across_ranges() {
        LanScanner.NetRange a = range("192.168.1.0", "192.168.1.50", 24, "Wi-Fi (wlan0)");
        LanScanner.NetRange b = range("192.168.1.0", "192.168.1.100", 24, "Hotspot (ap0)");
        List<String> candidates = LanScanner.buildCandidates(Arrays.asList(a, b),
                LanScanner.MAX_HOSTS_PER_RANGE);
        assertFalse(candidates.contains("192.168.1.50"));
        assertFalse(candidates.contains("192.168.1.100"));
    }

    @Test
    public void probe_finds_listening_host() throws IOException {
        try (ServerSocket server = new ServerSocket(0)) {
            // loopback is never scanned in production, but the probe itself is address-agnostic
            Set<String> found = LanScanner.probe(
                    Collections.singletonList("127.0.0.1"), server.getLocalPort(),
                    LanScanner.PROBE_TIMEOUT_MS);
            assertTrue(found.contains("127.0.0.1"));
        }
    }

    @Test
    public void probe_skips_hosts_with_closed_port() throws IOException {
        ServerSocket server = new ServerSocket(0);
        int port = server.getLocalPort();
        server.close();
        Set<String> found = LanScanner.probe(
                Collections.singletonList("127.0.0.1"), port, LanScanner.PROBE_TIMEOUT_MS);
        assertTrue(found.isEmpty());
    }

    @Test
    public void probe_empty_input_returns_empty() {
        assertTrue(LanScanner.probe(Collections.<String>emptyList(), 5555,
                LanScanner.PROBE_TIMEOUT_MS).isEmpty());
    }

    private static void assertArrayEquals(byte[] expected, byte[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("byte " + i, expected[i], actual[i]);
        }
    }
}