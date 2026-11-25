package Server;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The Server class initializes RMI services for clients to connect.
 * Tree server class has an "enabled" state. When disabled, only admin can log in and ranger logins are rejected.
 */
public class TreeServer extends Thread {
    private static volatile boolean running;
    private static boolean starting;
    private static Registry ownedRegistry;
    private static final List<java.rmi.Remote> exportedServices = new ArrayList<>();
    /**
     * When false, only admin can log in; rangers get "Server is currently disabled".
     */
    private static volatile boolean serverEnabled = true;
    private static final int RMI_PORT = 1099;
    /**
     * Lock for thread-safe server state changes
     */
    private static final ReentrantReadWriteLock serverStateLock = new ReentrantReadWriteLock();

    // UDP presence responder details (used for LAN discovery and single-server enforcement)
    public static final int PRESENCE_PORT = 50000;
    private static final String DISCOVER_MSG = "DISCOVER_TREE_SERVER";
    private static final String RESPONSE_PREFIX = "TREE_SERVER:"; // TREE_SERVER:<ip>:<port>:<instanceId>
    private static final String SERVER_INSTANCE_ID = UUID.randomUUID().toString();

    // Main method moved to Launcher (src/Launcher.java). Use that as the application's entry point.
    // public static void main(String[] args) { /* moved to Launcher */ }


    public static int getPort() {
        return RMI_PORT;
    }

    private static boolean isVirtualInterface(NetworkInterface ni) {
        if (ni.isVirtual()) return true;
        String name = ni.getName().toLowerCase();
        String displayName = ni.getDisplayName().toLowerCase();
        String combined = name + " " + displayName;
        return combined.contains("virtualbox") || combined.contains("vbox")
                || combined.contains("vmware") || combined.contains("vmnet")
                || combined.contains("docker") || combined.contains("veth")
                || combined.contains("virbr") || combined.contains("vethernet")
                || combined.contains("hyper-v") || combined.contains("virtual")
                || combined.contains("host-only") || combined.contains("hostonly");
    }

    private static boolean isKnownVirtualRange(String ip) {
        return ip.startsWith("192.168.56.") || ip.startsWith("192.168.57.")
                || ip.startsWith("172.17.") || ip.startsWith("172.18.");
    }

    /**
     * Returns all usable IPv4 addresses, ordered with real adapters first, virtual last.
     */
    public static List<String> getAllNetworkIPs() {
        List<String> real = new ArrayList<>();
        List<String> virtual = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.getAddress().length != 4) continue;
                    if (addr.isLinkLocalAddress()) continue;
                    String ip = addr.getHostAddress();
                    if (isVirtualInterface(ni) || isKnownVirtualRange(ip)) {
                        virtual.add(ip + "  [" + ni.getDisplayName() + "]");
                    } else {
                        real.add(ip + "  [" + ni.getDisplayName() + "]");
                    }
                }
            }
        } catch (Exception ignored) {}
        List<String> all = new ArrayList<>(real);
        all.addAll(virtual);
        return all;
    }

    public static String detectLocalNetworkIP() throws Exception {
        String fallback = null;
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            if (ni.isLoopback() || !ni.isUp()) continue;

            Enumeration<InetAddress> addresses = ni.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (addr.getAddress().length != 4) continue;
                if (addr.isLinkLocalAddress()) continue;
                String ip = addr.getHostAddress();

                if (isVirtualInterface(ni) || isKnownVirtualRange(ip)) {
                    if (fallback == null) fallback = ip;
                    System.out.println("[NET] Skipped virtual adapter: " + ip + " (" + ni.getDisplayName() + ")");
                } else {
                    System.out.println("[NET] Selected LAN adapter: " + ip + " (" + ni.getDisplayName() + ")");
                    return ip;
                }
            }
        }
        if (fallback != null) {
            System.out.println("[NET] WARNING: Only virtual adapters found, using fallback: " + fallback);
            return fallback;
        }
        return InetAddress.getLocalHost().getHostAddress();
    }

    private static String getAdvertisedHost() throws Exception {
        String prop = System.getProperty("java.rmi.server.hostname");
        if (prop != null) {
            prop = prop.trim();
            if (!prop.isEmpty()) return prop;
        }
        return detectLocalNetworkIP();
    }

    private static boolean isLocalIPv4(String ip) {
        if (ip == null || ip.trim().isEmpty()) return false;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp()) continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.getAddress().length == 4 && ip.equals(addr.getHostAddress())) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Returns true if the provided IPv4 string matches any local network interface address.
     * Used by launchers to distinguish "this machine" from "another machine" during discovery.
     */
    public static boolean isLocalAddress(String ip) {
        return isLocalIPv4(ip);
    }

    private static Set<InetAddress> getBroadcastAddresses() {
        Set<InetAddress> broadcasts = new HashSet<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress b = ia.getBroadcast();
                    if (b != null) broadcasts.add(b);
                }
            }
        } catch (Exception ignored) {}
        try {
            broadcasts.add(InetAddress.getByName("255.255.255.255"));
        } catch (Exception ignored) {}
        return broadcasts;
    }

    public static boolean isServerEnabled() {
        serverStateLock.readLock().lock();
        try {
            return serverEnabled;
        } finally {
            serverStateLock.readLock().unlock();
        }
    }

    public static void setServerEnabled(boolean enabled) {
        serverStateLock.writeLock().lock();
        try {
            serverEnabled = enabled;
            System.out.println("--- SERVER LOGIN " + (enabled ? "ENABLED" : "DISABLED") + " ---");
        } finally {
            serverStateLock.writeLock().unlock();
        }
    }

    public static synchronized void startServerEngine() {
        if (running || starting) return;

        // LAN single-server enforcement: if another server is already responding on the LAN,
        // refuse to start this instance.
        try {
            DiscoveredServer existing = discoverServerInfo(600);
            if (existing != null && !isLocalIPv4(existing.ip)) {
                System.err.println("Another TreeServer is already running at " + existing.ip + ":" + existing.port + ". Not starting a second server.");
                return;
            }
        } catch (Exception ignored) {}

        starting = true;
        Thread rmiThread = new Thread(() -> {
            synchronized (TreeServer.class) {
            try {
                // Always set RMI hostname to a reachable LAN address,
                // overriding any previously set value (e.g. from a GUI textfield
                // that may have picked a virtual adapter IP).
                try {
                    String host = detectLocalNetworkIP();
                    String prev = System.getProperty("java.rmi.server.hostname");
                    System.setProperty("java.rmi.server.hostname", host);
                    if (prev != null && !prev.equals(host)) {
                        System.out.println("[RMI] Overrode java.rmi.server.hostname from " + prev + " -> " + host);
                    } else {
                        System.out.println("[RMI] Set java.rmi.server.hostname=" + host);
                    }
                } catch (Exception e) {
                    System.err.println("[RMI] WARNING: Could not detect LAN IP: " + e.getMessage());
                }

                // Create and export the remote objects
                ownedRegistry = LocateRegistry.createRegistry(RMI_PORT);
                bindService("TreeService", new TreeServiceImpl());
                bindService("UserService", new UserServiceImpl());
                bindService("AdminService", new AdminServiceImpl());
                bindService("ChatService", new ChatServiceImpl());

                running = true;

                String serverIP = detectLocalNetworkIP();
                System.out.println("--- RMI SERVER LIVE ON PORT " + RMI_PORT + " ---");
                System.out.println("--- SERVER IP: " + serverIP + " ---");
                System.out.println("--- Services Registered ---");
                System.out.println("   - TreeService");
                System.out.println("   - UserService");
                System.out.println("   - AdminService");
                System.out.println("   - ChatService");

                // Start UDP presence responder so other launchers on the LAN can discover this server
                startPresenceResponder();

            } catch (Exception e) {
                releaseServices();
                running = false;
                System.err.println("Fatal: Could not start RMI server.");
                e.printStackTrace();
            } finally { starting = false; }
            }
        }, "RMI-Server");
        rmiThread.setDaemon(false);
        rmiThread.start();
    }

    private static Thread presenceResponderThread;

    private static void startPresenceResponder() {
        if (presenceResponderThread != null && presenceResponderThread.isAlive()) return;
        presenceResponderThread = new Thread(() -> {
            try (java.net.DatagramSocket socket = new java.net.DatagramSocket(PRESENCE_PORT)) {
                socket.setReuseAddress(true);
                socket.setBroadcast(true);
                socket.setSoTimeout(500);
                byte[] buf = new byte[256];
                System.out.println("Presence responder listening on UDP port " + PRESENCE_PORT);
                while (running) {
                    java.net.DatagramPacket packet = new java.net.DatagramPacket(buf, buf.length);
                    try {
                        socket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                        if (DISCOVER_MSG.equals(msg)) {
                            String ip = getAdvertisedHost();
                            String resp = RESPONSE_PREFIX + ip + ":" + RMI_PORT + ":" + SERVER_INSTANCE_ID;
                            byte[] respBytes = resp.getBytes("UTF-8");
                            java.net.DatagramPacket respPacket = new java.net.DatagramPacket(respBytes, respBytes.length, packet.getAddress(), packet.getPort());
                            socket.send(respPacket);
                        }
                    } catch (java.net.SocketTimeoutException retry) {
                        // Recheck the running flag while no clients are discovering.
                    } catch (Exception e) {
                        if (running) e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                System.err.println("Presence responder failed: " + e.getMessage());
            }
        }, "PresenceResponder");
        presenceResponderThread.setDaemon(true);
        presenceResponderThread.start();
    }

    /**
     * Sends a UDP broadcast discovery packet and waits up to timeoutMs for any server response.
     * Returns true if a server was discovered on the LAN.
     */
    public static boolean discoverServer(int timeoutMs) {
        return discoverServerInfo(timeoutMs) != null;
    }

    public static final class DiscoveredServer {
        public final String ip;
        public final int port;
        public final String instanceId;

        public DiscoveredServer(String ip, int port, String instanceId) {
            this.ip = ip;
            this.port = port;
            this.instanceId = instanceId;
        }
    }

    /**
     * Returns the first discovered server on the LAN, or null if none respond within timeoutMs.
     * Response format: TREE_SERVER:&lt;ip&gt;:&lt;port&gt;:&lt;instanceId&gt;
     */
    public static DiscoveredServer discoverServerInfo(int timeoutMs) {
        List<DiscoveredServer> all = discoverAllServers(timeoutMs);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * Broadcast discovery and collect all server responses until timeoutMs.
     * Useful if multiple servers are accidentally running.
     */
    public static List<DiscoveredServer> discoverAllServers(int timeoutMs) {
        List<DiscoveredServer> found = new ArrayList<>();
        try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
            socket.setSoTimeout(Math.max(100, timeoutMs));
            socket.setBroadcast(true);

            byte[] sendData = DISCOVER_MSG.getBytes("UTF-8");
            for (InetAddress bcast : getBroadcastAddresses()) {
                try {
                    java.net.DatagramPacket sendPacket = new java.net.DatagramPacket(sendData, sendData.length, bcast, PRESENCE_PORT);
                    socket.send(sendPacket);
                } catch (Exception ignored) {}
            }

            long deadline = System.currentTimeMillis() + Math.max(100, timeoutMs);
            byte[] recvBuf = new byte[512];
            while (System.currentTimeMillis() < deadline) {
                int remaining = (int) Math.max(1, deadline - System.currentTimeMillis());
                socket.setSoTimeout(remaining);
                try {
                    java.net.DatagramPacket receivePacket = new java.net.DatagramPacket(recvBuf, recvBuf.length);
                    socket.receive(receivePacket);
                    String resp = new String(receivePacket.getData(), 0, receivePacket.getLength(), "UTF-8");
                    DiscoveredServer ds = parseDiscoveryResponse(resp, receivePacket.getAddress());
                    if (ds != null) {
                        boolean dup = false;
                        for (DiscoveredServer existing : found) {
                            if (existing.ip.equals(ds.ip) && existing.port == ds.port) {
                                dup = true;
                                break;
                            }
                        }
                        if (!dup) found.add(ds);
                    }
                } catch (java.net.SocketTimeoutException ste) {
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("Discovery error: " + e.getMessage());
        }
        return found;
    }

    private static DiscoveredServer parseDiscoveryResponse(String resp, InetAddress sender) {
        if (resp == null) return null;
        resp = resp.trim();
        if (!resp.startsWith(RESPONSE_PREFIX)) return null;

        // Expected: TREE_SERVER:<ip>:<port>:<instanceId>
        String payload = resp.substring(RESPONSE_PREFIX.length());
        String[] parts = payload.split(":");
        if (parts.length < 2) return null;

        String ip = parts[0].trim();
        int port;
        try {
            port = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException nfe) {
            return null;
        }
        String instanceId = parts.length >= 3 ? parts[2].trim() : "";

        // If the message didn't include an IP (or it's malformed), fall back to sender address.
        if (ip.isEmpty() || "0.0.0.0".equals(ip)) {
            if (sender != null) ip = sender.getHostAddress();
        }
        return new DiscoveredServer(ip, port, instanceId);
    }

    /**
     * Stops the RMI server by unbinding all services.
     */
    public static synchronized void stopServerEngine() {
        if (!running) return;
        running = false;
        releaseServices();
        SessionManager.clearSessions();
    }

    private static void bindService(String name, java.rmi.Remote service) throws RemoteException {
        exportedServices.add(service);
        ownedRegistry.rebind(name, service);
    }

    private static void releaseServices() {
        for (java.rmi.Remote service : exportedServices) unexport(service);
        exportedServices.clear();
        if (ownedRegistry != null) {
            unexport(ownedRegistry);
            ownedRegistry = null;
        }
    }

    private static void unexport(java.rmi.Remote object) {
        try { java.rmi.server.UnicastRemoteObject.unexportObject(object, true); }
        catch (java.rmi.NoSuchObjectException alreadyStopped) { /* Already released. */ }
    }

    /**
     * Returns true if the RMI server is currently running.
     */
    public static boolean isRunning() {
        return running;
    }
}
