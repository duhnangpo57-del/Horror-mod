package com.observer.client;

import com.observer.ObserverMod;
import com.observer.entity.ObserverEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** Fullbright glowing eyes, drawn on top of the skin in the dark. */
public class ObserverEyesLayer extends EyesLayer<ObserverEntity, HumanoidModel<ObserverEntity>> {
    private static final RenderType EYES = RenderType.eyes(
            new ResourceLocation(ObserverMod.MODID, "textures/entity/the_observer_eyes.png"));

    public ObserverEyesLayer(RenderLayerParent<ObserverEntity, HumanoidModel<ObserverEntity>> parent) {
        super(parent);
    }

    @Override
    public RenderType renderType() {
        return EYES;
    }
}
