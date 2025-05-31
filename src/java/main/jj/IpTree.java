package jj;

import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.MapEntry;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class IpTree {

    private static final String dotRegex = "\\.";
    private final TreeNode[] nodeArray;

    public IpTree(IPersistentMap map) {
        this.nodeArray = new TreeNode[256];
        map.forEach(o -> {
            if (o instanceof MapEntry mapEntry) {
                addMask((String) mapEntry.getKey(), (Keyword) mapEntry.getValue());
            }
        });
    }

    public Keyword locate(String ip) {
        List<Integer> octetList = ipToList(ip);
        int firstIpOctet = octetList.getFirst();
        TreeNode node = nodeArray[firstIpOctet];

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
            TreeNode currentOctetNode = nodeArray[firstOctetInStart];
            if (currentOctetNode == null) {
                TreeNode newNode = new TreeNode(firstOctetInStart, firstOctetInStart);
                nodeArray[firstOctetInStart] = newNode;
                currentOctetNode = newNode;
            }

            currentOctetNode.addIpOctets(startOctets.subList(1, startOctets.size()), endOctets.subList(1, startOctets.size()), country);

        } else {
            TreeNode newNode = new TreeNode(firstOctetInStart, firstOctetInEnd, country);
            Arrays.fill(nodeArray, firstOctetInStart, firstOctetInEnd + 1, newNode);
        }
    }

    private static List<Integer> ipToList(String ipAddress) {
        List<Integer> result = new ArrayList<>();
        if (ipAddress == null || ipAddress.isEmpty()) {
            return new ArrayList<>(0);
        }

        String[] parts = ipAddress.split(dotRegex);

        for (String part : parts) {
            int value = Integer.parseInt(part);
            result.add(value);
        }
        return result;
    }


    private static String[] convertCidrToIpRange(String cidrIp) {
        if (cidrIp == null || cidrIp.isEmpty()) {
            return null;
        }

        String[] parts = cidrIp.split("/");
        if (parts.length != 2) {
            System.err.println("Invalid CIDR format. Expected format: ip_address/subnet_mask");
            return null;
        }

        String ipAddressStr = parts[0];
        int subnetMaskLength;
        try {
            subnetMaskLength = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid subnet mask length: " + parts[1]);
            return null;
        }

        if (subnetMaskLength < 0 || subnetMaskLength > 32) {
            System.err.println("Subnet mask length must be between 0 and 32.");
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
                System.err.println("Only IPv4 addresses are supported.");
                return null;
            }

            byte[] ipBytes = ipAddress.getAddress();
            int ipInt = ByteBuffer.wrap(ipBytes).getInt();
            int networkMask = (0xFFFFFFFF << (32 - subnetMaskLength));
            int networkAddressInt = (ipInt & networkMask);
            int broadcastAddressInt = (networkAddressInt | (~networkMask));
            byte[] startIpBytes = ByteBuffer.allocate(4).putInt(networkAddressInt).array();
            byte[] endIpBytes = ByteBuffer.allocate(4).putInt(broadcastAddressInt).array();

            String startIp = InetAddress.getByAddress(startIpBytes).getHostAddress();
            String endIp = InetAddress.getByAddress(endIpBytes).getHostAddress();

            return new String[]{
                    startIp,
                    endIp
            };

        } catch (UnknownHostException e) {
            System.err.println("Invalid IP address: " + ipAddressStr);
            return null;
        }
    }

    final static class TreeNode {
        private final int rangeStart;
        private final int rangeEnd;
        private final Keyword country;
        private final ArrayList<TreeNode> children;


        public TreeNode(int startDate, int endDate) {
            this.rangeStart = startDate;
            this.rangeEnd = endDate;
            this.country = null;
            children = new ArrayList<>(1);
        }

        public TreeNode(int startDate, int endDate, Keyword country) {
            this.rangeStart = startDate;
            this.rangeEnd = endDate;
            this.country = country;
            children = new ArrayList<>(1);
        }

        public Keyword locate() {
            return country;
        }

        private boolean isInRange(int number) {
            return rangeStart <= number && number <= rangeEnd;
        }

        Keyword locate(List<Integer> octetList) {
            int firstIpOctet = octetList.getFirst();
            TreeNode node = null;
            for (TreeNode treeNode : children) {
                if (treeNode.isInRange(firstIpOctet)) {
                    node = treeNode;
                    break;
                }
            }
            if (null != node) {
                if (node.isLeafNode()) {
                    return node.country;
                } else {
                    return node.locate(octetList.subList(1, octetList.size()));
                }
            } else {
                return null;
            }
        }

        boolean isLeafNode() {
            return country != null;
        }

        private TreeNode getTreeNodeForOctet(int octet) {
            for (TreeNode treeNode : children) {
                if (treeNode.isInRange(octet)) {
                    return treeNode;
                }
            }
            return null;
        }

        public void addIpOctets(List<Integer> startOctets, List<Integer> endOctets, Keyword country) {
            int firstOctetInStart = startOctets.getFirst();
            int firstOctetInEnd = endOctets.getFirst();
            if (startOctets.size() < 2 || firstOctetInStart != firstOctetInEnd) {
                TreeNode newNode = new TreeNode(firstOctetInStart, firstOctetInEnd, country);
                children.add(newNode);
            } else {
                TreeNode currentOctetNode = getTreeNodeForOctet(firstOctetInStart);
                if (currentOctetNode == null) {
                    TreeNode newNode = new TreeNode(firstOctetInStart, firstOctetInStart);
                    children.add(newNode);
                    currentOctetNode = newNode;
                }
                currentOctetNode.addIpOctets(startOctets.subList(1, startOctets.size()), endOctets.subList(1, startOctets.size()), country);
            }
        }
    }


}