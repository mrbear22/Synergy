package me.synergy.web;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import me.synergy.brains.Synergy;
import me.synergy.modules.Config;
import me.synergy.modules.DataManager;
import me.synergy.modules.Locales;
import me.synergy.modules.Module;
import me.synergy.modules.Tasks;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;
import net.dv8tion.jda.api.utils.data.DataObject;

public class VotifierServer implements Module {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAGIC_V2 = 0x733A;

    private static KeyPair keyPair;
    private static ServerSocket socket;

    @Override public Side side() { return Side.HUB; }
    @Override public String toggle() { return "votifier.enabled"; }
    @Override public String name() { return "Votifier"; }

    @Override
    public void defaults() {
        Locales.addDefault("vote.reward.received", "<success>Vote from %service% counted. Thank you!<sound:'entity.player.levelup'>");
        Locales.addDefault("vote.reward.announce", "<success>%player% voted on %service%!");
        Config.addDefault("votifier.enabled", false);
        Config.addDefault("votifier.port", 8192);
        Config.addDefault("votifier.token", Config.secret(40));
        Config.addDefault("votifier.monitorings", new String[] {"https://example.com/vote/example"});
    }

    @Override
    public void start() throws Exception {
        keyPair = loadOrCreateKeyPair();
        ServerSocket server = new ServerSocket(Config.getInt("votifier.port", 8192));
        socket = server;
        Tasks.async(() -> {
            while (!server.isClosed()) {
                try {
                    Socket client = server.accept();
                    client.setSoTimeout(5000);
                    Tasks.async(() -> handle(client));
                } catch (IOException e) {
                    if (!server.isClosed()) Synergy.getLogger().warning("Votifier: failed to accept connection: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public void stop() throws IOException {
        if (socket != null) socket.close();
        socket = null;
    }

    public static String getPublicKey() {
        return keyPair != null ? Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()) : null;
    }

    public static void handle(Socket client) {
        if (keyPair == null) {
            close(client);
            return;
        }

        String challenge = new BigInteger(130, RANDOM).toString(32);

        try (Socket connection = client) {
            InputStream in = connection.getInputStream();
            OutputStream out = connection.getOutputStream();

            out.write(("VOTIFIER 2 " + challenge + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();

            byte[] header = read(in, 2);
            String[] vote;

            if (((header[0] & 0xFF) << 8 | (header[1] & 0xFF)) == MAGIC_V2) {
                vote = readModern(in, out, challenge);
            } else {
                byte[] block = new byte[256];
                block[0] = header[0];
                block[1] = header[1];
                System.arraycopy(read(in, 254), 0, block, 2, 254);
                vote = readLegacy(block);
            }

            if (vote != null) {
                Synergy.getLogger().info("Vote received: " + vote[1] + " (" + vote[0] + ")");
                Synergy.event("vote")
                       .setOption("service", vote[0])
                       .setOption("username", vote[1])
                       .send();
                
                UUID uuid = DataManager.findUuidByName(vote[1]);
                
                if (uuid == null) {
					return;
				}
                
                BreadMaker bread = Synergy.getBread(uuid);

                String message = LocaleBuilder.of("vote.reward.announce").placeholder("player", vote[1]).placeholder("service", vote[0]).placeholder("gender", bread.getGender()).build();
                
                Synergy.event("discord-embed").toProxy()
                    .setPlayerUniqueId(bread.getUniqueId())
                    .setOption("chat", "global")
                    .setOption("color", "#55efc4")
                    .setOption("author", message)
                    .fireEvent();
                
                bread.setData("last-voted", System.currentTimeMillis());
                bread.setData("votes", bread.getData("votes").isSet() ? bread.getData("votes").getAsInteger() + 1 : 1);
                
            }
        } catch (Exception e) {
            Synergy.getLogger().warning("Votifier: failed to process vote: " + e.getMessage());
        }
    }

    private static String[] readLegacy(byte[] block) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
        String[] parts = new String(cipher.doFinal(block), StandardCharsets.UTF_8).split("\n");

        if (parts.length < 3 || !"VOTE".equals(parts[0])) {
            throw new IOException("Malformed v1 vote");
        }
        return new String[] { parts[1], parts[2] };
    }

    private static String[] readModern(InputStream in, OutputStream out, String challenge) throws Exception {
        byte[] length = read(in, 2);
        String envelope = new String(read(in, (length[0] & 0xFF) << 8 | (length[1] & 0xFF)), StandardCharsets.UTF_8);

        DataObject message = DataObject.fromJson(envelope);
        String payload = message.getString("payload");

        if (!verify(payload, message.getString("signature", ""))) {
            respond(out, "signature does not match");
            return null;
        }

        DataObject vote = DataObject.fromJson(payload);
        if (!challenge.equals(vote.getString("challenge", ""))) {
            respond(out, "challenge does not match");
            return null;
        }

        respond(out, null);
        return new String[] { vote.getString("serviceName", "unknown"), vote.getString("username") };
    }

    private static boolean verify(String payload, String signature) throws Exception {
        String token = Config.getString("votifier.token");
        if (token == null || token.isEmpty()) {
            return false;
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return MessageDigest.isEqual(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)),
                Base64.getDecoder().decode(signature));
    }

    private static void respond(OutputStream out, String error) throws IOException {
        String response = error == null
                ? "{\"status\":\"ok\"}"
                : "{\"status\":\"error\",\"cause\":\"AuthenticationException\",\"error\":\"" + error + "\"}";
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static byte[] read(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = in.read(buffer, offset, length - offset);
            if (count == -1) {
                throw new IOException("Unexpected end of stream");
            }
            offset += count;
        }
        return buffer;
    }

    private static void close(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private static KeyPair loadOrCreateKeyPair() throws Exception {
        File folder = new File(Synergy.getDataFolder(), "votifier");
        File publicFile = new File(folder, "public.key");
        File privateFile = new File(folder, "private.key");

        if (publicFile.exists() && privateFile.exists()) {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return new KeyPair(
                factory.generatePublic(new X509EncodedKeySpec(
                    Base64.getDecoder().decode(Files.readString(publicFile.toPath()).trim()))),
                factory.generatePrivate(new PKCS8EncodedKeySpec(
                    Base64.getDecoder().decode(Files.readString(privateFile.toPath()).trim())))
            );
        }

        folder.mkdirs();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        Files.writeString(publicFile.toPath(), Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        Files.writeString(privateFile.toPath(), Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        Synergy.getLogger().info("Votifier: RSA keypair generated in " + folder.getPath());

        return pair;
    }
}
