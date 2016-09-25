package us.myles.ViaVersion.protocols.protocol1_9to1_8;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import us.myles.ViaVersion.api.PacketWrapper;
import us.myles.ViaVersion.api.Via;
import us.myles.ViaVersion.api.data.UserConnection;
import us.myles.ViaVersion.api.minecraft.item.Item;
import us.myles.ViaVersion.api.minecraft.metadata.Metadata;
import us.myles.ViaVersion.api.protocol.Protocol;
import us.myles.ViaVersion.api.remapper.ValueTransformer;
import us.myles.ViaVersion.api.type.Type;
import us.myles.ViaVersion.api.type.types.version.Metadata1_8Type;
import us.myles.ViaVersion.api.type.types.version.MetadataList1_8Type;
import us.myles.ViaVersion.protocols.base.ProtocolInfo;
import us.myles.ViaVersion.protocols.protocol1_9to1_8.packets.*;
import us.myles.ViaVersion.protocols.protocol1_9to1_8.storage.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class Protocol1_9TO1_8 extends Protocol {
    @Deprecated
    public static Type<List<Metadata>> METADATA_LIST = new MetadataList1_8Type();
    @Deprecated
    public static Type<Metadata> METADATA = new Metadata1_8Type();

    private static Gson gson = new GsonBuilder().create();
    public static ValueTransformer<String, String> FIX_JSON = new ValueTransformer<String, String>(Type.STRING) {
        @Override
        public String transform(PacketWrapper wrapper, String line) {
            return fixJson(line);
        }
    };

    public static String fixJson(String line) {
        if (line == null || line.equalsIgnoreCase("null")) {
            line = "{\"text\":\"\"}";
        } else {
            if ((!line.startsWith("\"") || !line.endsWith("\"")) && (!line.startsWith("{") || !line.endsWith("}"))) {
                return constructJson(line);
            }
            if (line.startsWith("\"") && line.endsWith("\"")) {
                line = "{\"text\":" + line + "}";
            }
        }
        try {
            gson.fromJson(line, JsonObject.class);
        } catch (Exception e) {
            if (Via.getConfig().isForceJsonTransform()) {
                return constructJson(line);
            } else {
                System.out.println("Invalid JSON String: \"" + line + "\" Please report this issue to the ViaVersion Github: " + e.getMessage());
                return "{\"text\":\"\"}";
            }
        }
        return line;
    }

    private static String constructJson(String text) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("text", text);
        return gson.toJson(jsonObject);
    }

    public static Item getHandItem(final UserConnection info) {
        if (HandItemCache.CACHE) {
            return HandItemCache.getHandItem(info.get(ProtocolInfo.class).getUuid());
        } else {
            try {
                return Bukkit.getScheduler().callSyncMethod(Bukkit.getPluginManager().getPlugin("ViaVersion"), new Callable<Item>() {
                    @Override
                    public Item call() throws Exception {
                        UUID playerUUID = info.get(ProtocolInfo.class).getUuid();
                        if (Bukkit.getPlayer(playerUUID) != null) {
                            return Item.getItem(Bukkit.getPlayer(playerUUID).getItemInHand());
                        }
                        return null;
                    }
                }).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.out.println("Error fetching hand item: " + e.getClass().getName());
                if (Via.getManager().isDebug())
                    e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    protected void registerPackets() {
        SpawnPackets.register(this);
        InventoryPackets.register(this);
        EntityPackets.register(this);
        PlayerPackets.register(this);
        WorldPackets.register(this);
    }

    @Override
    protected void registerListeners() {

    }

    @Override
    public boolean isFiltered(Class packetClass) {
        return packetClass.getName().endsWith("PacketPlayOutMapChunkBulk");
    }

    @Override
    protected void filterPacket(UserConnection info, Object packet, List output) throws Exception {
        output.addAll(info.get(ClientChunks.class).transformMapChunkBulk(packet));
    }

    @Override
    public void init(UserConnection userConnection) {
        // Entity tracker
        userConnection.put(new EntityTracker(userConnection));
        // Chunk tracker
        userConnection.put(new ClientChunks(userConnection));
        // Movement tracker
        userConnection.put(new MovementTracker(userConnection));
        // Inventory tracker
        userConnection.put(new InventoryTracker(userConnection));
        // Place block tracker
        userConnection.put(new PlaceBlockTracker(userConnection));
    }

    public static boolean isSword(int id) {
        if (id == 267) return true; // Iron
        if (id == 268) return true; // Wood
        if (id == 272) return true; // Stone
        if (id == 276) return true; // Diamond
        if (id == 283) return true; // Gold

        return false;
    }
}
