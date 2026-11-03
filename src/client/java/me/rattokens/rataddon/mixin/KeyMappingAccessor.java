package me.rattokens.rataddon.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("clickCount")
	int rataddon$getClickCount();

	@Accessor("clickCount")
	void rataddon$setClickCount(int value);
}
