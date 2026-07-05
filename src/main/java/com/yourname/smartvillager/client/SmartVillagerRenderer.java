package com.yourname.smartvillager.client;

import com.yourname.smartvillager.SmartVillagerMod;
import com.yourname.smartvillager.entity.SmartVillager;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Placeholder renderer for the Smart Villager: a plain humanoid model with the vanilla wide-Steve
 * texture. The mod's own model/texture is a separate work track (design document section 6);
 * until then this keeps the entity visible without shipping any assets.
 */
public class SmartVillagerRenderer extends MobRenderer<SmartVillager, HumanoidModel<SmartVillager>> {

    /** Model layer for the Smart Villager's (placeholder) humanoid body. */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(SmartVillagerMod.MOD_ID, "smart_villager"), "main");

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/entity/player/wide/steve.png");

    public SmartVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(LAYER)), 0.5F);
    }

    /** Builds the placeholder humanoid mesh (same layout as the vanilla base humanoid). */
    public static LayerDefinition createBodyLayer() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64);
    }

    @Override
    public ResourceLocation getTextureLocation(SmartVillager entity) {
        return TEXTURE;
    }
}
