package org.gloyer057.cuffsx.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Generates skin-format (64x64) overlay textures for handcuffs at runtime.
 *
 * Minecraft skin UV layout (64x64):
 *   Right arm:  texture x=40, y=16, size 4x12 (front face of arm)
 *   Left arm:   texture x=32, y=48, size 4x12 (1.8 skin format)
 *   Right leg:  texture x=0,  y=16, size 4x12
 *   Left leg:   texture x=16, y=48, size 4x12
 *
 * We draw a dark metal band (2px tall) at the wrist/ankle position.
 */
@Environment(EnvType.CLIENT)
public class CuffTextureGenerator {

    public static final Identifier HANDS_ID = new Identifier("cuffsx", "generated/handcuffs_hands");
    public static final Identifier LEGS_ID  = new Identifier("cuffsx", "generated/handcuffs_legs");

    // Dark iron/metal color
    private static final int METAL_DARK   = argb(255, 50,  50,  55);
    private static final int METAL_MID    = argb(255, 80,  80,  85);
    private static final int METAL_LIGHT  = argb(255, 110, 110, 115);
    private static final int TRANSPARENT  = 0x00000000;

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        client.getTextureManager().registerTexture(HANDS_ID,
                new NativeImageBackedTexture(buildHandsTexture()));
        client.getTextureManager().registerTexture(LEGS_ID,
                new NativeImageBackedTexture(buildLegsTexture()));
    }

    /**
     * Builds a 64x64 skin-format texture with metal bands on the wrists.
     *
     * Arm UV regions in 64x64 skin:
     *   Right arm body: u=44, v=20, w=4, h=12  (front face)
     *   Left arm body:  u=36, v=52, w=4, h=12  (front face, 1.8 format)
     *
     * We paint a 4-wide, 2-tall band at the bottom of each arm (wrist = v+10..v+12)
     */
    private static NativeImage buildHandsTexture() {
        NativeImage img = new NativeImage(64, 64, true);
        // Fill with transparent
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++)
                img.setColor(x, y, TRANSPARENT);

        // Right arm — full UV block: x=40..48, y=16..32
        // Front face of arm: x=44..48, y=20..32 (4 wide, 12 tall)
        // Wrist band: bottom 2 rows of arm front = y=30..31
        drawBand(img, 40, 16, 16, 16); // entire right arm region — draw band at bottom
        paintWristBand(img, 44, 20, 4, 12); // right arm front face

        // Left arm (1.8 skin) — UV block: x=32..48, y=48..64
        // Front face: x=36..40, y=52..64
        paintWristBand(img, 36, 52, 4, 12); // left arm front face

        return img;
    }

    /**
     * Builds a 64x64 skin-format texture with metal bands on the ankles.
     *
     * Leg UV regions:
     *   Right leg body: u=4, v=20, w=4, h=12  (front face)
     *   Left leg body:  u=20, v=52, w=4, h=12 (1.8 format)
     */
    private static NativeImage buildLegsTexture() {
        NativeImage img = new NativeImage(64, 64, true);
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++)
                img.setColor(x, y, TRANSPARENT);

        // Right leg front face: x=4..8, y=20..32
        paintAnkleBand(img, 4, 20, 4, 12);

        // Left leg front face (1.8): x=20..24, y=52..64
        paintAnkleBand(img, 20, 52, 4, 12);

        return img;
    }

    /**
     * Paints a 2-pixel-tall metal band at the bottom of a limb face (wrist).
     * The band covers the full width of the face.
     */
    private static void paintWristBand(NativeImage img, int faceX, int faceY, int w, int h) {
        // Band at bottom 2 rows (wrist)
        int bandY = faceY + h - 3;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < w; col++) {
                int color = (col == 0 || col == w - 1) ? METAL_DARK :
                            (row == 0) ? METAL_LIGHT : METAL_MID;
                img.setColor(faceX + col, bandY + row, color);
            }
        }
        // Also paint the sides of the arm UV block for the band
        // Right side face of arm: x=faceX-4..faceX, same y
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 4; col++) {
                img.setColor(faceX - 4 + col, bandY + row, METAL_MID);
            }
        }
    }

    /**
     * Paints a 2-pixel-tall metal band at the bottom of a leg face (ankle).
     */
    private static void paintAnkleBand(NativeImage img, int faceX, int faceY, int w, int h) {
        int bandY = faceY + h - 3;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < w; col++) {
                int color = (col == 0 || col == w - 1) ? METAL_DARK :
                            (row == 0) ? METAL_LIGHT : METAL_MID;
                img.setColor(faceX + col, bandY + row, color);
            }
        }
        // Side face
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 4; col++) {
                img.setColor(faceX - 4 + col, bandY + row, METAL_MID);
            }
        }
    }

    private static void drawBand(NativeImage img, int x, int y, int w, int h) {
        // unused — kept for reference
    }

    private static int argb(int a, int r, int g, int b) {
        // NativeImage uses ABGR format
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
