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

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * マインカート乗り捨て防止プラグイン
 * @author ucchy
 */
public class MinecartCollect extends JavaPlugin implements Listener {

    private String configMessageCollected;
    private String configMessageDisappear;

    /**
     * プラグインが有効化されたときに呼び出されるメソッド
     * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
     */
    @Override
    public void onEnable() {

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

        // 乗っていたマインカートを削除する
        event.getVehicle().remove();

        // マインカートのアイテムを作成し、プレイヤーのインベントリに追加する
        ItemStack item = new ItemStack(Material.MINECART);
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
     * インベントリの空きスロット数を数える
     * @param inv インベントリ
     * @return 空きスロット数
     */
    private int getEmptySlotCount(Inventory inv) {
        int count = 0;
        for ( ItemStack i : inv.getContents() ) {
            if ( i == null || i.getType() == Material.AIR ) {
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
            writer = new BufferedWriter(new OutputStreamWriter(fos));

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
}