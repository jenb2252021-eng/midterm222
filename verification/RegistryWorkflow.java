import Model.Tree;
import Model.ChatMessage;
import Server.*;
import JSONUtil.UserJSONUtil;
import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.rmi.Remote;
import java.rmi.server.*;
import java.util.*;

/** Runs service checks on an isolated dataset, optionally through loopback RMI. */
public class RegistryWorkflow {
    private static final List<Remote> exported = new ArrayList<>();
    private static int passed;
    private static boolean overRmi;
    private static void check(String id, boolean outcome) {
        if (!outcome) throw new AssertionError(id);
        System.out.println("PASS  " + id);
        passed++;
    }
    private static <T extends Remote> T stub(Class<T> type, T service) throws Exception {
        exported.add(service);
        return overRmi ? type.cast(RemoteObject.toStub(service)) : service;
    }
    private static Tree tree(String name) {
        return new Tree(0, name, "16.405700,120.598500", 1.8, 3.2, "2025-06-14",
                "Healthy", "Field inspection", null, null);
    }
    public static void main(String[] args) throws Exception {
        overRmi = Arrays.asList(args).contains("--rmi");
        System.out.println(overRmi ? "Mode: loopback RMI" : "Mode: direct service calls");
        Path directory = Files.createTempDirectory("lorax-acceptance-");
        System.setProperty("lorax.data.dir", directory.toString());
        System.setProperty("java.rmi.server.hostname", "127.0.0.1");
        RMISocketFactory.setSocketFactory(new RMISocketFactory() {
            public Socket createSocket(String host, int port) throws IOException {
                return new Socket(InetAddress.getLoopbackAddress(), port);
            }
            public ServerSocket createServerSocket(int port) throws IOException {
                return new ServerSocket(port, 16, InetAddress.getLoopbackAddress());
            }
        });
        String secret = UUID.randomUUID().toString();
        try {
            check("R01 local administrator setup", UserJSONUtil.createLocalAdministrator(secret));
            UserService users = stub(UserService.class, new UserServiceImpl());
            TreeService trees = stub(TreeService.class, new TreeServiceImpl());
            ChatService chat = stub(ChatService.class, new ChatServiceImpl());
            AdminService admin = stub(AdminService.class, new AdminServiceImpl());
            check("R02 ranger registration", users.register("ranger", secret).startsWith("SUCCESS"));
            check("R03 duplicate registration", users.register("Ranger", secret).startsWith("ERROR"));
            String a = users.authenticate("admin", secret), r = users.authenticate("ranger", secret);
            check("R04 authenticated roles", a.startsWith("SUCCESS: ") && r.startsWith("SUCCESS: ") && !a.equals(r));
            String adminToken = a.substring(9), rangerToken = r.substring(9);
            String accounts = Files.readString(directory.resolve("users.json"));
            check("R05 salted password storage", !accounts.contains(secret) && accounts.contains("passwordHash"));
            Tree invalid = tree("Narra"); invalid.location = "outside map";
            check("R06 invalid coordinates", trees.addTree(invalid, rangerToken).startsWith("ERROR"));
            invalid = tree("Narra"); invalid.height = Double.NaN;
            check("R07 invalid measurement", trees.addTree(invalid, rangerToken).startsWith("ERROR"));
            invalid = tree("Narra"); invalid.plantedDate = "2026-02-30";
            check("R08 invalid planting date", trees.addTree(invalid, rangerToken).startsWith("ERROR"));
            Tree submission = tree("Benguet Pine");
            var raster = new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);
            var photo = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(raster,"png",photo);
            submission.imageBytes = photo.toByteArray();
            check("R09 ranger submits", trees.addTree(submission, rangerToken).startsWith("SUCCESS_PENDING"));
            check("R10 pending hidden from inventory", trees.getAllTrees(rangerToken).isEmpty());
            var queue = trees.getPendingTrees(adminToken);
            check("R11 review queue", queue.size()==1 && "ranger".equals(queue.get(0).ownerUsername));
            int id = queue.get(0).id;
            check("R21 review restricted to admin", trees.approvePendingTree(id,rangerToken).startsWith("ERROR")
                    && trees.getPendingTrees(adminToken).size() == 1);
            check("R22 validation leaves audit accurate", admin.getActivityLog(adminToken).stream()
                    .filter(s -> s.contains("SUBMIT_PENDING_TREE")).count() == 1);
            check("R12 approval with stable ID", trees.approvePendingTree(id, adminToken).startsWith("SUCCESS")
                    && trees.getAllTrees(rangerToken).get(0).id == id && trees.getPendingTrees(adminToken).isEmpty());
            check("R23 attached photo survives review", Arrays.equals(photo.toByteArray(), trees.getAllTrees(rangerToken).get(0).imageBytes)
                    && Arrays.equals(photo.toByteArray(), storage.ImageStorage.loadTreeImageById(id)));
            check("R13 species search", trees.searchBySpecies("Benguet Pine", rangerToken).size()==1);
            check("R14 ranger sees decision", trees.getMySubmissions(rangerToken).stream().anyMatch(s -> s.startsWith("Approved")));
            trees.addTree(tree("Alnus"), rangerToken);
            int other = trees.getPendingTrees(adminToken).get(0).id;
            check("R15 rejection", trees.rejectPendingTree(other, adminToken).startsWith("SUCCESS")
                    && trees.getMySubmissions(rangerToken).stream().anyMatch(s -> s.startsWith("Rejected")));
            ChatMessage sent = chat.sendMessage(rangerToken,"admin","Revisit the pine.");
            check("R16 field messaging", sent.id!=null && chat.getConversation(adminToken,"ranger").size()==1);
            check("R17 server audit", admin.getActivityLog(adminToken).stream().anyMatch(s -> s.contains("APPROVE_PENDING_TREE")));
            check("R18 persisted registry", Files.readString(directory.resolve("tree_data.json")).contains("Benguet Pine"));
            check("R19 archive", trees.deleteTree(id,adminToken).startsWith("SUCCESS") && trees.getDeletedTrees(adminToken).size()==1);
            check("R20 sign out", users.signOut(rangerToken).startsWith("SUCCESS") && !chat.getOnlineUsers(adminToken).contains("ranger"));
            System.out.println(passed + " checks passed.");
        } finally {
            for (Remote object : exported) UnicastRemoteObject.unexportObject(object,true);
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }
}
