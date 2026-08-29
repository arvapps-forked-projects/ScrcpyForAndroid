package org.client.scrcpy.model;

/**
 * A candidate mirror target discovered by scanning the local network, or a
 * device already known to the local adb server.
 */
public class DiscoveredDevice {

    public enum Source {
        /** Listed by `adb devices` (already connected / known to adb). */
        ADB_LIST,
        /** Found by probing the adb port (5555) on the local network. */
        SCAN
    }

    private final String host;
    private final int port;
    /** Friendly display name (reverse-DNS hostname), may be null. */
    private final String name;
    /** Source interface label, e.g. "Wi-Fi (wlan0)". null for adb-list items. */
    private final String interfaceLabel;
    /** adb state: "device", "offline", "unauthorized" or null if unknown. */
    private final String adbState;
    private final Source source;

    public DiscoveredDevice(String host, int port, String name, String interfaceLabel,
                            String adbState, Source source) {
        this.host = host;
        this.port = port;
        this.name = name;
        this.interfaceLabel = interfaceLabel;
        this.adbState = adbState;
        this.source = source;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getName() {
        return name;
    }

    public String getInterfaceLabel() {
        return interfaceLabel;
    }

    public String getAdbState() {
        return adbState;
    }

    public Source getSource() {
        return source;
    }

    /** "host:port" as used by adb. */
    public String getAddress() {
        return host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiscoveredDevice)) return false;
        DiscoveredDevice that = (DiscoveredDevice) o;
        return getAddress().equals(that.getAddress());
    }

    @Override
    public int hashCode() {
        return getAddress().hashCode();
    }
}