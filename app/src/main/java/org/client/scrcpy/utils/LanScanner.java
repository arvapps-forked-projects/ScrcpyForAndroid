package org.client.scrcpy.utils;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Discovers Android devices listening on the adb port (5555) on the local
 * network: the Wi-Fi subnet and the phone's own hotspot (tether) subnets.
 *
 * <p>All network work here is blocking and must be called off the UI thread
 * (see {@link ThreadUtils}).
 */
public final class LanScanner {

    /** Default adb listen port. Keep in sync with Scrcpy.DEFAULT_ADB_PORT. */
    public static final int ADB_PORT = 5555;

    /** Max hosts probed per interface subnet (a full /24, or the first 254 neighbours of larger subnets). */
    public static final int MAX_HOSTS_PER_RANGE = 254;

    /** Per-connect timeout (ms) for the parallel port probe. */
    public static final int PROBE_TIMEOUT_MS = 300;

    /** Ceiling for the whole probe, in case a connect hangs past its timeout. */
    private static final long PROBE_TOTAL_TIMEOUT_SECONDS = 30;

    private static final int PROBE_THREADS = 64;

    /** A local IPv4 segment worth scanning. */
    public static final class NetRange {
        public final byte[] network; // 4-byte network (base) address
        public final byte[] local;   // 4-byte local address on this interface
        public final int prefix;     // network prefix length, 1..30
        public final String label;   // display label, e.g. "Wi-Fi (wlan0)"

        NetRange(byte[] network, byte[] local, int prefix, String label) {
            this.network = network;
            this.local = local;
            this.prefix = prefix;
            this.label = label;
        }
    }

    private LanScanner() {
    }

    /**
     * Enumerates the active IPv4 ranges of this device: Wi-Fi, phone hotspot
     * (tether) and USB/ethernet tethers. Loopback, virtual, point-to-point
     * (cellular) and IPv6-only interfaces are skipped.
     */
    public static List<NetRange> getActiveRanges() {
        List<NetRange> ranges = new ArrayList<>();
        try {
            List<NetworkInterface> interfaces =
                    Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (!intf.isUp() || intf.isLoopback() || intf.isVirtual()) continue;
                String name = intf.getName() == null ? "" : intf.getName().toLowerCase(Locale.US);
                if (isExcludedInterface(name)) continue;
                for (InterfaceAddress addr : intf.getInterfaceAddresses()) {
                    if (!(addr.getAddress() instanceof Inet4Address)) continue; // IPv6 deferred
                    int prefix = addr.getNetworkPrefixLength();
                    if (prefix < 1 || prefix > 30) continue;
                    Inet4Address ip = (Inet4Address) addr.getAddress();
                    byte[] network = networkAddress(ip.getAddress(), prefix);
                    ranges.add(new NetRange(network, ip.getAddress(), prefix,
                            labelFor(intf.getName())));
                }
            }
        } catch (SocketException e) {
            // No interfaces readable; the caller surfaces the empty state.
        }
        return ranges;
    }

    private static boolean isExcludedInterface(String name) {
        return name.startsWith("lo")
                || name.startsWith("dummy")
                || name.startsWith("tun")
                || name.startsWith("tap")
                || name.startsWith("p2p")
                || name.startsWith("rmnet")
                || name.startsWith("ccmni")
                || name.startsWith("sit")
                || name.startsWith("vpn")
                || name.startsWith("ppp")
                || name.startsWith("bond");
    }

    private static String labelFor(String name) {
        if (name == null) return "Network";
        String n = name.toLowerCase(Locale.US);
        String kind;
        if (n.startsWith("wlan") || n.startsWith("swlan")) {
            kind = "Wi-Fi";
        } else if (n.startsWith("ap") || n.startsWith("softap")) {
            kind = "Hotspot";
        } else if (n.startsWith("rndis") || n.startsWith("usb") || n.startsWith("eth")) {
            kind = "USB tether / Ethernet";
        } else if (n.startsWith("bt") || n.contains("pan")) {
            kind = "Bluetooth tether";
        } else {
            kind = "Network";
        }
        return kind + " (" + name + ")";
    }

    /** AND the address with the prefix mask to get the network base address. */
    static byte[] networkAddress(byte[] ip, int prefix) {
        byte[] mask = new byte[4];
        for (int i = 0; i < 4; i++) {
            int bits = Math.max(0, Math.min(8, prefix - i * 8));
            mask[i] = bits == 0 ? 0 : (byte) (0xFF << (8 - bits));
        }
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            out[i] = (byte) (ip[i] & mask[i]);
        }
        return out;
    }

    /**
     * Builds the host addresses to probe: network base + 1 .. + maxHostsPerRange
     * for every range, skipping the broadcast address and every local address
     * across all ranges. Duplicates are removed. Pure JVM logic (unit-testable).
     */
    public static List<String> buildCandidates(List<NetRange> ranges, int maxHostsPerRange) {
        Set<Long> locals = new LinkedHashSet<>();
        for (NetRange range : ranges) {
            locals.add(toUnsignedLong(range.local));
        }
        Set<Long> hosts = new LinkedHashSet<>();
        for (NetRange range : ranges) {
            long base = toUnsignedLong(range.network);
            long broadcast = base | (~maskLong(range.prefix) & 0xFFFFFFFFL);
            for (long h = 1; h <= maxHostsPerRange; h++) {
                long addr = base + h;
                if (addr == broadcast || addr == 0 || addr == 0xFFFFFFFFL) continue;
                if (locals.contains(addr)) continue;
                hosts.add(addr);
            }
        }
        List<String> out = new ArrayList<>(hosts.size());
        for (long h : hosts) {
            out.add(fromUnsignedLong(h));
        }
        return out;
    }

    private static long maskLong(int prefix) {
        return prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
    }

    private static long toUnsignedLong(byte[] ip) {
        return ((ip[0] & 0xFFL) << 24) | ((ip[1] & 0xFFL) << 16)
                | ((ip[2] & 0xFFL) << 8) | (ip[3] & 0xFFL);
    }

    private static String fromUnsignedLong(long v) {
        return ((v >>> 24) & 0xFF) + "." + ((v >>> 16) & 0xFF)
                + "." + ((v >>> 8) & 0xFF) + "." + (v & 0xFF);
    }

    /**
     * Concurrently checks whether each host accepts TCP connections on the given
     * port. Returns the set of host strings that did. Blocking; call off the UI
     * thread. Bounded by {@link #PROBE_TOTAL_TIMEOUT_SECONDS}.
     */
    public static Set<String> probe(List<String> hosts, int port, int timeoutMs) {
        Set<String> found = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
        if (hosts == null || hosts.isEmpty()) return found;
        int threads = Math.max(1, Math.min(PROBE_THREADS, hosts.size()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(hosts.size());
        for (final String host : hosts) {
            pool.execute(() -> {
                try {
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(host, port), timeoutMs);
                        found.add(host);
                    }
                } catch (IOException | IllegalArgumentException ignored) {
                    // unreachable, not listening, or malformed host
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(PROBE_TOTAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pool.shutdownNow();
        return found;
    }

    /**
     * Best-effort reverse-DNS lookup with a hard timeout. Returns the hostname
     * when it resolves to something other than the literal address, else null.
     */
    public static String resolveHostname(final String host, long timeoutMs) {
        if (host == null || host.isEmpty()) return null;
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = pool.submit(() -> {
                InetAddress addr = InetAddress.getByName(host);
                return addr.getHostName();
            });
            try {
                return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                return null;
            } catch (Exception e) {
                return null;
            }
        } finally {
            pool.shutdownNow();
        }
    }
}