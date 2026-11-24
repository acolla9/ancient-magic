package com.ancientstudios.ancientmagic;

import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(AncientMagic.MODID)
public class AncientMagic {
    public static final String MODID = "ancientmagic";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    // Decorative / lore blocks, one per staff. Textures: textures/block/block_of_hate.png and block_of_determination.png
    public static final DeferredBlock<Block> BLOCK_OF_HATE = BLOCKS.registerBlock("block_of_hate", props -> new AuraBlock(props, true), BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_RED).strength(5.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK).lightLevel(state -> 7));
    public static final DeferredItem<BlockItem> BLOCK_OF_HATE_ITEM = ITEMS.registerSimpleBlockItem("block_of_hate", BLOCK_OF_HATE, new Item.Properties().rarity(Rarity.EPIC));

    public static final DeferredBlock<Block> BLOCK_OF_DETERMINATION = BLOCKS.registerBlock("block_of_determination", props -> new AuraBlock(props, false), BlockBehaviour.Properties.of()
            .mapColor(MapColor.SNOW).strength(5.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.AMETHYST).lightLevel(state -> 12));
    public static final DeferredItem<BlockItem> BLOCK_OF_DETERMINATION_ITEM = ITEMS.registerSimpleBlockItem("block_of_determination", BLOCK_OF_DETERMINATION, new Item.Properties().rarity(Rarity.EPIC));

    // Sky Block: renders the live sky (see SkyBlockRenderer + shaders/core/sky_block.*)
    public static final DeferredBlock<Block> SKY_BLOCK = BLOCKS.registerBlock("sky_block", SkyBlock::new, BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1.5F, 6.0F).sound(SoundType.GLASS).noOcclusion());
    public static final DeferredItem<BlockItem> SKY_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("sky_block", SKY_BLOCK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SkyBlockEntity>> SKY_BLOCK_ENTITY = BLOCK_ENTITIES.register("sky_block",
            () -> BlockEntityType.Builder.of(SkyBlockEntity::new, SKY_BLOCK.get()).build(null));

    // Registry ids are kept as-is so existing worlds keep their staffs ("admin_staff" is the Staff of Hate).
    public static final DeferredItem<AdminStaffItem> ADMIN_STAFF = ITEMS.register("admin_staff",
            () -> new AdminStaffItem(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC), StaffType.HATE));

    public static final DeferredItem<AdminStaffItem> STAFF_OF_DETERMINATION = ITEMS.register("staff_of_determination",
            () -> new AdminStaffItem(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC), StaffType.DETERMINATION));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ANCIENT_TAB = CREATIVE_MODE_TABS.register("ancientmagic", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ancientmagic"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> ADMIN_STAFF.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ADMIN_STAFF.get());
                output.accept(STAFF_OF_DETERMINATION.get());
                output.accept(BLOCK_OF_HATE_ITEM.get());
                output.accept(BLOCK_OF_DETERMINATION_ITEM.get());
                output.accept(SKY_BLOCK_ITEM.get());
            }).build());

    public AncientMagic(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(SpellFxPayload::register);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);

        // Needed for the @SubscribeEvent command registration below.
        NeoForge.EVENT_BUS.register(this);

        modEventBus.addListener(this::addCreative);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(BLOCK_OF_HATE_ITEM);
            event.accept(BLOCK_OF_DETERMINATION_ITEM);
            event.accept(SKY_BLOCK_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ADMIN_STAFF);
            event.accept(STAFF_OF_DETERMINATION);
        }
    }

    private static int give(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets,
                            DeferredItem<AdminStaffItem> item, String name) {
        for (ServerPlayer target : targets) {
            ItemStack staff = item.toStack();
            if (!target.getInventory().add(staff)) {
                target.drop(staff, false);
            }
        }
        context.getSource().sendSuccess(() -> Component.literal("Given " + name + " to " + targets.size() + " player(s)."), true);
        return targets.size();
    }

    private static Collection<ServerPlayer> self(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return List.of(context.getSource().getPlayerOrException());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        // Everything under /ancientmagic is operator-only (permission level 2).
        // Normal players cast through CastSpellPayload, which re-validates the held staff, cooldown and config on the server.
        event.getDispatcher().register(Commands.literal("ancientmagic")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("give")
                        .executes(context -> give(context, self(context), ADMIN_STAFF, "Staff of Hate"))
                        .then(Commands.literal("hate")
                                .executes(context -> give(context, self(context), ADMIN_STAFF, "Staff of Hate"))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(context -> give(context, EntityArgument.getPlayers(context, "targets"), ADMIN_STAFF, "Staff of Hate"))))
                        .then(Commands.literal("determination")
                                .executes(context -> give(context, self(context), STAFF_OF_DETERMINATION, "Staff of Determination"))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(context -> give(context, EntityArgument.getPlayers(context, "targets"), STAFF_OF_DETERMINATION, "Staff of Determination")))))
                .then(Commands.literal("cast")
                        .then(Commands.argument("spell", IntegerArgumentType.integer(1, StaffType.COUNT))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    int spell = IntegerArgumentType.getInteger(context, "spell") - 1;
                                    return CastSpellPayload.tryCast(player, -1, spell, true) ? 1 : 0;
                                }))));
    }
}