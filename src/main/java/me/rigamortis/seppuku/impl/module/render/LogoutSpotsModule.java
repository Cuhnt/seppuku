package me.rigamortis.seppuku.impl.module.render;

import com.google.common.collect.Maps;
import com.mojang.authlib.GameProfile;
import me.rigamortis.seppuku.api.event.player.EventPlayerJoin;
import me.rigamortis.seppuku.api.event.player.EventPlayerLeave;
import me.rigamortis.seppuku.api.event.player.EventPlayerUpdate;
import me.rigamortis.seppuku.api.event.render.EventRender2D;
import me.rigamortis.seppuku.api.event.render.EventRender3D;
import me.rigamortis.seppuku.api.module.Module;
import me.rigamortis.seppuku.api.util.GLUProjection;
import me.rigamortis.seppuku.api.value.NumberValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import team.stiff.pomelo.impl.annotated.handler.annotation.Listener;

import java.util.Map;

/**
 * created by noil on 11/5/2019 at 6:37 PM
 */
public final class LogoutSpotsModule extends Module {

    public final NumberValue<Integer> removeDistance = new NumberValue<Integer>("RemoveDistance", new String[]{"RD", "RemoveRange"}, 200, Integer.class, 1, 2000, 1);

    private final Map<String, EntityPlayer> playerCache = Maps.newConcurrentMap();
    private final Map<String, PlayerData> logoutCache = Maps.newConcurrentMap();

    public LogoutSpotsModule() {
        super("LogoutSpots", new String[]{"Logout", "Spots"}, "Draws the location of nearby player logouts.", "NONE", -1, ModuleType.RENDER);
    }

    @Override
    public void onToggle() {
        super.onToggle();
        playerCache.clear();
        logoutCache.clear();
    }

    @Listener
    public void onPlayerUpdate(EventPlayerUpdate event) {
        final Minecraft mc = Minecraft.getMinecraft();

        if (mc.player == null)
            return;

        for (EntityPlayer player : mc.world.playerEntities) {
            if (player == null || player.equals(mc.player))
                continue;

            this.updatePlayerCache(player.getGameProfile().getId().toString(), player);
        }
    }

    @Listener
    public void onRenderWorld(EventRender3D event) {
        final Minecraft mc = Minecraft.getMinecraft();

        for (String uuid : this.logoutCache.keySet()) {
            final PlayerData data = this.logoutCache.get(uuid);

            if (this.isOutOfRange(data)) {
                this.logoutCache.remove(uuid);
                continue;
            }

            data.ghost.prevLimbSwingAmount = 0;
            data.ghost.limbSwing = 0;
            data.ghost.limbSwingAmount = 0;

            GlStateManager.pushMatrix();
            GlStateManager.enableLighting();
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.enableDepth();
            GlStateManager.color(1, 1, 1, 1);
            mc.getRenderManager().renderEntity(data.ghost, data.position.getX() - mc.getRenderManager().renderPosX, data.position.getY() - mc.getRenderManager().renderPosY, data.position.getZ() - mc.getRenderManager().renderPosZ, data.ghost.rotationYaw, mc.getRenderPartialTicks(), false);
            GlStateManager.disableLighting();
            GlStateManager.disableBlend();
            GlStateManager.disableAlpha();
            GlStateManager.popMatrix();
        }
    }

    @Listener
    public void onRender2D(EventRender2D event) {
        final Minecraft mc = Minecraft.getMinecraft();

        for (String uuid : this.logoutCache.keySet()) {
            final PlayerData data = this.logoutCache.get(uuid);

            if (this.isOutOfRange(data)) {
                this.logoutCache.remove(uuid);
                continue;
            }

            final GLUProjection.Projection projection = GLUProjection.getInstance().project(data.position.getX() - mc.getRenderManager().renderPosX, data.position.getY() - mc.getRenderManager().renderPosY, data.position.getZ() - mc.getRenderManager().renderPosZ, GLUProjection.ClampMode.NONE, true);
            if (projection != null && projection.isType(GLUProjection.Projection.Type.INSIDE)) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(projection.getX(), projection.getY(), 0);
                String text = data.profile.getName() + " logout";
                float textWidth = mc.fontRenderer.getStringWidth(text);
                mc.fontRenderer.drawStringWithShadow(text, -(textWidth / 2), 0, -1);
                GlStateManager.translate(-projection.getX(), -projection.getY(), 0);
                GlStateManager.popMatrix();
            }
        }
    }

    @Listener
    public void onPlayerLeave(EventPlayerLeave event) {
        final Minecraft mc = Minecraft.getMinecraft();

        for (String uuid : this.playerCache.keySet()) {
            if (!uuid.equals(event.getUuid())) // not matching uuid
                continue;

            final EntityPlayer player = this.playerCache.get(uuid);

            final PlayerData data = new PlayerData(player.getPosition(), player.getGameProfile(), player);

            if (!this.hasPlayerLogged(uuid)) {
                this.logoutCache.put(uuid, data);
            }
        }

        this.playerCache.clear();
    }

    @Listener
    public void onPlayerJoin(EventPlayerJoin event) {
        final Minecraft mc = Minecraft.getMinecraft();

        for (String uuid : this.logoutCache.keySet()) {
            if (!uuid.equals(event.getUuid())) // not matching uuid
                continue;

            this.logoutCache.remove(uuid);
        }

        this.playerCache.clear();
    }

    private void cleanLogoutCache(String uuid) {
        this.logoutCache.remove(uuid);
    }

    private void updatePlayerCache(String uuid, EntityPlayer player) {
        this.playerCache.put(uuid, player);
    }

    private boolean hasPlayerLogged(String uuid) {
        return this.logoutCache.containsKey(uuid);
    }

    private boolean isOutOfRange(PlayerData data) {
        BlockPos position = data.position;
        return Minecraft.getMinecraft().player.getDistance(position.getX(), position.getY(), position.getZ()) > this.removeDistance.getInt();
    }

    private class PlayerData {
        BlockPos position;
        GameProfile profile;
        EntityPlayer ghost;

        public PlayerData(BlockPos position, GameProfile profile, EntityPlayer ghost) {
            this.position = position;
            this.profile = profile;
            this.ghost = ghost;
        }
    }
}
