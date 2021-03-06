/*
 * @author     ucchy
 * @license    LGPLv3
 * @copyright  Copyright ucchy 2015
 */
package org.bitbucket.ucchy.mc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * マインカート乗り捨て防止プラグイン
 * @author ucchy
 */
public class MinecartCollect extends JavaPlugin implements Listener {

    private static final String META_NAME = "MinecartCollect";

    private static MinecartCollect instance;

    private String configMessageCollected;
    private String configMessageDisappear;

    /**
     * プラグインが有効化されたときに呼び出されるメソッド
     * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
     */
    @Override
    public void onEnable() {

        instance = this;

        // リスナー登録
        getServer().getPluginManager().registerEvents(this, this);

        // コンフィグロード
        if ( !getDataFolder().exists() ) {
            getDataFolder().mkdirs();
        }
        File file = new File(getDataFolder(), "config.yml");
        if ( !file.exists() ) {
            copyFileFromJar(getFile(), file, "config_ja.yml");
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        configMessageCollected = replaceColorCode(config.getString("messageCollected"));
        configMessageDisappear = replaceColorCode(config.getString("messageDisappear"));
    }

    /**
     * 乗り物に乗った時に発生するイベント
     * @param event
     */
    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {

        // 乗ろうとしているエンティティがプレイヤーでないなら、イベントを無視する
        if ( !(event.getEntered() instanceof Player) ) {
            return;
        }

        // 乗ろうとしている乗り物がMinecartでないなら、イベントを無視する
        if ( !(event.getVehicle() instanceof RideableMinecart) ) {
            return;
        }

        // メタデータを設定する
        event.getVehicle().setMetadata(META_NAME, new FixedMetadataValue(this, true));
    }

    /**
     * 乗り物から降りた時に発生するイベント
     * @param event
     */
    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {

        // 降りたエンティティがプレイヤーでないなら、イベントを無視する
        if ( !(event.getExited() instanceof Player) ) {
            return;
        }

        // 降りた乗り物がMinecartでないなら、イベントを無視する
        if ( !(event.getVehicle() instanceof RideableMinecart) ) {
            return;
        }

        Player player = (Player)event.getExited();
        final Vehicle vehicle = event.getVehicle();
        Minecart cart = (Minecart)vehicle;
        String customName = cart.getCustomName();

        // 乗っていたマインカートを削除する
        vehicle.remove();

        // 2tick後にメタデータを消去する
        new BukkitRunnable() {
            public void run() {
                if ( vehicle != null ) {
                    vehicle.removeMetadata(META_NAME, instance);
                }
            }
        }.runTaskLater(this, 2);

        // マインカートのアイテムを作成し、プレイヤーのインベントリに追加する
        ItemStack item = new ItemStack(Material.MINECART);
        if ( customName != null ) {
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(customName);
            item.setItemMeta(meta);
        }
        if ( getEmptySlotCount(player.getInventory()) > 0 ) {
            player.getInventory().addItem(item);
            if ( configMessageCollected != null ) {
                player.sendMessage(configMessageCollected);
            }
        } else {
            if ( configMessageDisappear != null ) {
                player.sendMessage(configMessageDisappear);
            }
        }
    }

    /**
     * 乗り物が壊された時に発生するイベント
     * @param event
     */
    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent event) {

        // 壊された乗り物がMinecartでないなら、イベントを無視する
        if ( !(event.getVehicle() instanceof RideableMinecart) ) {
            return;
        }

        // メタデータが無いなら、イベントを無視する
        if ( !event.getVehicle().hasMetadata(META_NAME) ) {
            return;
        }

        // イベントをキャンセルして、アイテムがドロップしないようにする
        event.setCancelled(true);
    }

    /**
     * プレイヤーが死亡した時に発生するイベント
     * @param event
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {

        // プレイヤーが乗り物に乗っていなかったなら、イベントを無視する
        if ( !event.getEntity().isInsideVehicle() ) {
            return;
        }

        // 乗り物がトロッコでないなら、イベントを無視する
        Entity vehicle = event.getEntity().getVehicle();
        if ( !(vehicle instanceof RideableMinecart) ) {
            return;
        }

        // 乗っていたマインカートを削除し、その場にアイテム化することで、
        // 死亡ドロップ品と混ぜてしまう。
        vehicle.removeMetadata(META_NAME, this);
        vehicle.remove();
        Minecart cart = (Minecart)vehicle;
        String customName = cart.getCustomName();

        ItemStack item = new ItemStack(Material.MINECART);
        if ( customName != null ) {
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(customName);
            item.setItemMeta(meta);
        }
        vehicle.getWorld().dropItemNaturally(vehicle.getLocation(), item);
    }

    /**
     * インベントリの空きスロット数を数える
     * @param inv インベントリ
     * @return 空きスロット数
     */
    private int getEmptySlotCount(PlayerInventory inv) {
        int count = 0;
        for ( int index = 0; index < 36; index++ ) {
            ItemStack item = inv.getItem(index);
            if ( item == null || item.getType() == Material.AIR ) {
                count++;
            }
        }
        return count;
    }

    /**
     * 文字列内のカラーコード候補（&a）を、カラーコード（§a）に置き換えする
     * @param source 置き換え元の文字列
     * @return 置き換え後の文字列
     */
    private static String replaceColorCode(String source) {
        if ( source == null ) return null;
        return ChatColor.translateAlternateColorCodes('&', source);
    }

    /**
     * jarファイルの中に格納されているテキストファイルを、jarファイルの外にコピーするメソッド<br/>
     * WindowsだとS-JISで、MacintoshやLinuxだとUTF-8で保存されます。
     * @param jarFile jarファイル
     * @param targetFile コピー先
     * @param sourceFilePath コピー元
     */
    private static void copyFileFromJar(
            File jarFile, File targetFile, String sourceFilePath) {

        JarFile jar = null;
        InputStream is = null;
        FileOutputStream fos = null;
        BufferedReader reader = null;
        BufferedWriter writer = null;

        File parent = targetFile.getParentFile();
        if ( !parent.exists() ) {
            parent.mkdirs();
        }

        try {
            jar = new JarFile(jarFile);
            ZipEntry zipEntry = jar.getEntry(sourceFilePath);
            is = jar.getInputStream(zipEntry);

            fos = new FileOutputStream(targetFile);

            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));

            // CB190以降は、書き出すファイルエンコードにUTF-8を強制する。
            if ( isCB19orLater() ) {
                writer = new BufferedWriter(new OutputStreamWriter(fos, "UTF-8"));
            } else {
                writer = new BufferedWriter(new OutputStreamWriter(fos));
            }

            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if ( jar != null ) {
                try {
                    jar.close();
                } catch (IOException e) {
                    // do nothing.
                }
            }
            if ( writer != null ) {
                try {
                    writer.flush();
                    writer.close();
                } catch (IOException e) {
                    // do nothing.
                }
            }
            if ( reader != null ) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // do nothing.
                }
            }
            if ( fos != null ) {
                try {
                    fos.flush();
                    fos.close();
                } catch (IOException e) {
                    // do nothing.
                }
            }
            if ( is != null ) {
                try {
                    is.close();
                } catch (IOException e) {
                    // do nothing.
                }
            }
        }
    }

    /**
     * 現在動作中のCraftBukkitが、v1.9 以上かどうかを確認する
     * @return v1.8以上ならtrue、そうでないならfalse
     */
    private static boolean isCB19orLater() {
        return isUpperVersion(Bukkit.getBukkitVersion(), "1.9");
    }

    /**
     * 指定されたバージョンが、基準より新しいバージョンかどうかを確認する
     * @param version 確認するバージョン
     * @param border 基準のバージョン
     * @return 基準より確認対象の方が新しいバージョンかどうか<br/>
     * ただし、無効なバージョン番号（数値でないなど）が指定された場合はfalseに、
     * 2つのバージョンが完全一致した場合はtrueになる。
     */
    private static boolean isUpperVersion(String version, String border) {

        int hyphen = version.indexOf("-");
        if ( hyphen > 0 ) {
            version = version.substring(0, hyphen);
        }

        String[] versionArray = version.split("\\.");
        int[] versionNumbers = new int[versionArray.length];
        for ( int i=0; i<versionArray.length; i++ ) {
            if ( !versionArray[i].matches("[0-9]+") )
                return false;
            versionNumbers[i] = Integer.parseInt(versionArray[i]);
        }

        String[] borderArray = border.split("\\.");
        int[] borderNumbers = new int[borderArray.length];
        for ( int i=0; i<borderArray.length; i++ ) {
            if ( !borderArray[i].matches("[0-9]+") )
                return false;
            borderNumbers[i] = Integer.parseInt(borderArray[i]);
        }

        int index = 0;
        while ( (versionNumbers.length > index) && (borderNumbers.length > index) ) {
            if ( versionNumbers[index] > borderNumbers[index] ) {
                return true;
            } else if ( versionNumbers[index] < borderNumbers[index] ) {
                return false;
            }
            index++;
        }
        if ( borderNumbers.length == index ) {
            return true;
        } else {
            return false;
        }
    }
}
