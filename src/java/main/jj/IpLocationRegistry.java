package jj;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.MapEntry;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class IpLocationRegistry {

    private static final IFn require = Clojure.var("clojure.core", "require");
    private static final IFn log;
    private static final String dotRegex = "\\.";

    static {
        require.invoke(Clojure.read("clojure.tools.logging"));
        log = Clojure.var("clojure.tools.logging", "log");
    }

    private final SegmentNode[] nodeArray;


    public IpLocationRegistry(IPersistentMap map) {
        this.nodeArray = new SegmentNode[256];

        for (Object o : map) {
            if (o instanceof MapEntry mapEntry) {
                addMask((String) mapEntry.getKey(), (Keyword) mapEntry.getValue());
            }

        }
    }

    public Keyword locate(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }

        List<Integer> octetList = ipToList(ip);

        if (octetList == null || octetList.size() != 4) {
            return null;
        }

        int firstIpOctet = octetList.getFirst();

        if (firstIpOctet > 255) {
            return null;
        }

        SegmentNode node = nodeArray[firstIpOctet];

        if (null != node) {
            if (node.isLeafNode()) {
                return node.locate();
            } else {
                return node.locate(octetList.subList(1, octetList.size()));
            }
        } else {
            return null;
        }
    }

    private void addMask(String ipWithMask, Keyword country) {
        String[] ips = convertCidrToIpRange(ipWithMask);
        List<Integer> startOctets = ipToList(ips[0]);
        List<Integer> endOctets = ipToList(ips[1]);
        int firstOctetInStart = startOctets.getFirst();
        int firstOctetInEnd = endOctets.getFirst();

        if (firstOctetInStart == firstOctetInEnd) {
            SegmentNode currentOctetNode = nodeArray[firstOctetInStart];

            if (currentOctetNode == null) {
                SegmentNode newNode = new SegmentNode();
                nodeArray[firstOctetInStart] = newNode;
                currentOctetNode = newNode;
            }

            currentOctetNode.addIpOctets(
                    startOctets.subList(1, startOctets.size()),
                    endOctets.subList(1, startOctets.size()),
                    country
            );
        } else {
            SegmentNode newNode = new SegmentNode(country);

            Arrays.fill(nodeArray, firstOctetInStart, firstOctetInEnd + 1, newNode);
        }
    }

    private static List<Integer> ipToList(String ipAddress) {
        List<Integer> result = new ArrayList<>();

        if (ipAddress == null || ipAddress.isEmpty()) {
            return new ArrayList<>(0);
        }

        String[] parts = ipAddress.split(dotRegex);

        try {
            for (String part : parts) {
                int value = Integer.parseInt(part);

                result.add(value);
            }

            return result;
        } catch (Exception e) {
            return new ArrayList<>(0);
        }
    }

    private static String[] convertCidrToIpRange(String cidrIp) {
        if (cidrIp == null || cidrIp.isEmpty()) {
            return null;
        }

        String[] parts = cidrIp.split("/");

        if (parts.length != 2) {
            log.invoke(
                    Clojure.read(":error"),
                    "Invalid CIDR format. Expected format: ip_address/subnet_mask"
            );
            return null;
        }

        String ipAddressStr = parts[0];

        int subnetMaskLength;

        try {
            subnetMaskLength = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            log.invoke(Clojure.read(":error"), "Invalid subnet mask length: " + parts[1]);
            return null;
        }

        if (subnetMaskLength < 0 || subnetMaskLength > 32) {
            log.invoke(Clojure.read(":error"), "Subnet mask length must be between 0 and 32.");
            return null;
        }

        try {
            InetAddress ipAddress;

            try {
                ipAddress = InetAddress.getByName(ipAddressStr);
            } catch (UnknownHostException e) {
                return null;
            }

            if (ipAddress.getAddress().length != 4) {
                log.invoke(
                        Clojure.read(":error"),
                        "Only IPv4 addresses are supported." + parts[1]
                );
                return null;
            }

            byte[] ipBytes = ipAddress.getAddress();

            int ipInt = ByteBuffer.wrap(ipBytes).getInt();
            int networkMask = (0xFFFFFFFF << (32 - subnetMaskLength));
            int networkAddressInt = (ipInt & networkMask);
            int broadcastAddressInt = (networkAddressInt | (~networkMask));
            byte[] startIpBytes = ByteBuffer.allocate(4)
                    .putInt(networkAddressInt)
                    .array();

            byte[] endIpBytes = ByteBuffer.allocate(4)
                    .putInt(broadcastAddressInt)
                    .array();

            String startIp = InetAddress.getByAddress(startIpBytes).getHostAddress();

            String endIp = InetAddress.getByAddress(endIpBytes).getHostAddress();

            return new String[]{startIp, endIp};
        } catch (UnknownHostException e) {
            log.invoke(Clojure.read(":error"), "Invalid IP address: " + ipAddressStr);
            return null;
        }
    }

    static final class SegmentNode {

        private final Keyword country;
        private SegmentNode[] arrayChildren = new SegmentNode[0];

        public SegmentNode() {
            this.country = null;
        }

        public SegmentNode(Keyword country) {
            this.country = country;
        }

        public Keyword locate() {
            return country;
        }

        /**
         * Locates the Keyword associated with a given list of IP octets.
         * This method assumes a complete path of octets to traverse.
         *
         * @param octetList The list of IP octets.
         * @return The Keyword if found at the leaf node, otherwise null.
         */

        Keyword locate(List<Integer> octetList) {
            if (octetList.isEmpty()) {
                return this.country;
            }

            int currentOctet = octetList.getFirst();

            if (
                    currentOctet >= arrayChildren.length ||
                            arrayChildren[currentOctet] == null
            ) {
                return null;
            }

            SegmentNode node = arrayChildren[currentOctet];

            if (node.isLeafNode()) {
                return node.country;
            } else {
                return node.locate(octetList.subList(1, octetList.size()));
            }
        }

        boolean isLeafNode() {
            return country != null;
        }

        /**
         * Resizes arrayChildren to at least the requiredCapacity.
         * If arrayChildren is already large enough, no action is taken.
         * Elements are copied, and new slots are null-initialized.
         *
         * @param requiredCapacity The minimum size the arrayChildren should be.
         */

        private void ensureCapacity(int requiredCapacity) {
            if (requiredCapacity >= arrayChildren.length) {
                int newCapacity = Math.max(
                        requiredCapacity + 1,
                        arrayChildren.length == 0 ? 1 : arrayChildren.length * 2
                );

                if (newCapacity > 256) { // Cap at 256 for IP octets if that's the ultimate range
                    newCapacity = 256;
                }

                if (newCapacity < requiredCapacity + 1) { // Ensure it's at least requiredCapacity + 1
                    newCapacity = requiredCapacity + 1;
                }

                arrayChildren = Arrays.copyOf(arrayChildren, newCapacity);
            }
        }

        /**
         * Adds IP octet ranges and associates them with a Keyword.
         * Dynamically resizes arrayChildren as needed.
         *
         * @param startOctets The list of starting IP octets for the range.
         * @param endOctets   The list of ending IP octets for the range.
         * @param country     The Keyword (e.g., country) to associate with this range.
         */

        public void addIpOctets(List<Integer> startOctets, List<Integer> endOctets, Keyword country) {
            if (startOctets.isEmpty()) {
                return;
            }

            int firstOctetInStart = startOctets.getFirst();
            int firstOctetInEnd = endOctets.getFirst();

            ensureCapacity(firstOctetInEnd);

            if (startOctets.size() == 1 || firstOctetInStart != firstOctetInEnd) {
                SegmentNode newNode = new SegmentNode(country);

                for (int i = firstOctetInStart; i <= firstOctetInEnd; i++) {
                    arrayChildren[i] = newNode;
                }
            } else {
                SegmentNode currentOctetNode = arrayChildren[firstOctetInStart];

                if (currentOctetNode == null) {
                    SegmentNode newNode = new SegmentNode();
                    arrayChildren[firstOctetInStart] = newNode;
                    currentOctetNode = newNode;
                }

                currentOctetNode.addIpOctets(startOctets.subList(1, startOctets.size()), endOctets.subList(1, endOctets.size()), country
                );
            }
        }
    }
}
