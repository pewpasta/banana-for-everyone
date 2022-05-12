package bananaplus.modules.combat;

import bananaplus.modules.AddModule;
import bananaplus.utils.BPlusEntityUtils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.AbstractBlockAccessor;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.Dir;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

public class HoleESPPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General

    private final Setting<Integer> horizontalRadius = sgGeneral.add(new IntSetting.Builder()
            .name("horizontal-radius")
            .description("Horizontal radius in which to search for holes.")
            .defaultValue(10)
            .min(0)
            .sliderMax(32)
            .build());

    private final Setting<Integer> verticalRadius = sgGeneral.add(new IntSetting.Builder()
            .name("vertical-radius")
            .description("Vertical radius in which to search for holes.")
            .defaultValue(5)
            .min(0)
            .sliderMax(32)
            .build());

    private final Setting<Integer> holeHeight = sgGeneral.add(new IntSetting.Builder()
            .name("min-height")
            .description("Minimum hole height required to be rendered.")
            .defaultValue(2)
            .min(1)
            .build());

    private final Setting<Boolean> doubles = sgGeneral.add(new BoolSetting.Builder()
            .name("doubles")
            .description("Highlights double holes that can be stood across.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> ignoreOwn = sgGeneral.add(new BoolSetting.Builder()
            .name("ignore-own")
            .description("Ignores rendering the hole you are currently standing in.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> webs = sgGeneral.add(new BoolSetting.Builder()
            .name("webs")
            .description("Whether to show holes that have webs inside of them.")
            .defaultValue(false)
            .build());

    // Render

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Lines)
            .build());

    private final Setting<Double> height = sgRender.add(new DoubleSetting.Builder()
            .name("height")
            .description("The height of rendering.")
            .defaultValue(0.25)
            .min(0)
            .build());

    private final Setting<Boolean> topQuad = sgRender.add(new BoolSetting.Builder()
            .name("top-quad")
            .description("Whether to render a quad at the top of the hole.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> bottomQuad = sgRender.add(new BoolSetting.Builder()
            .name("bottom-quad")
            .description("Whether to render a quad at the bottom of the hole.")
            .defaultValue(false)
            .build());

    private final Setting<SettingColor> bedrockColorTop = sgRender.add(new ColorSetting.Builder()
            .name("bedrock-top")
            .description("The top color for holes that are completely bedrock.")
            .defaultValue(new SettingColor(100, 255, 0, 0))
            .build());

    private final Setting<SettingColor> bedrockColorBottom = sgRender.add(new ColorSetting.Builder()
            .name("bedrock-bottom")
            .description("The bottom color for holes that are completely bedrock.")
            .defaultValue(new SettingColor(100, 255, 0, 200))
            .build());

    private final Setting<SettingColor> otherColorTop = sgRender.add(new ColorSetting.Builder()
            .name("other-top")
            .description("The top color for holes that are completely blastproof.")
            .defaultValue(new SettingColor(255, 0, 0, 0))
            .build());

    private final Setting<SettingColor> otherColorBottom = sgRender.add(new ColorSetting.Builder()
            .name("other-bottom")
            .description("The bottom color for holes that are completely blastproof.")
            .defaultValue(new SettingColor(255, 0, 0, 200))
            .build());

    private final Setting<SettingColor> mixedColorTop = sgRender.add(new ColorSetting.Builder()
            .name("mixed-top")
            .description("The top color for holes that have mixed bedrock and blastproof.")
            .defaultValue(new SettingColor(255, 127, 0, 0))
            .build());

    private final Setting<SettingColor> mixedColorBottom = sgRender.add(new ColorSetting.Builder()
            .name("mixed-bottom")
            .description("The bottom color for holes that have mixed bedrock and blastproof.")
            .defaultValue(new SettingColor(255, 127, 0, 200))
            .build());

    private final Pool<Hole> holePool = new Pool<>(Hole::new);
    private final List<Hole> holes = new ArrayList<>();

    private final byte NULL = 0;

    public HoleESPPlus() {
        super(AddModule.COMBAT, "hole-esp+", "Displays holes that you will take less damage in.");
    }
    
    // A way to improve this to not cause fps drops would be to use the searching for hole method from void esp, but we'll do that when fps issue is real

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        for (Hole hole : holes) holePool.free(hole);
        holes.clear();

        BlockIterator.register(horizontalRadius.get(), verticalRadius.get(), (blockPos, blockState) -> {
            if (!validHole(blockPos)) return;

            int bedrock = 0, blastproof = 0;
            Direction air = null;

            for (Direction direction : Direction.values()) {
                if (direction == Direction.UP) continue;

                if (BPlusEntityUtils.isBlastResistant(blockPos.offset(direction), BPlusEntityUtils.BlastResistantType.Unbreakable)) bedrock++;
                else if (BPlusEntityUtils.isBlastResistant(blockPos.offset(direction), BPlusEntityUtils.BlastResistantType.Mineable)) blastproof++;
                else if (direction == Direction.DOWN) return;
                else if (validHole(blockPos.offset(direction)) && air == null) {
                    for (Direction dir : Direction.values()) {
                        if (dir == direction.getOpposite() || dir == Direction.UP) continue;

                        if (BPlusEntityUtils.isBlastResistant(blockPos.offset(direction).offset(dir), BPlusEntityUtils.BlastResistantType.Unbreakable)) bedrock++;
                        else if (BPlusEntityUtils.isBlastResistant(blockPos.offset(direction).offset(dir), BPlusEntityUtils.BlastResistantType.Mineable)) blastproof++;
                        else return;
                    }

                    air = direction;
                }
            }

            if (blastproof + bedrock == 5 && air == null) {
                holes.add(holePool.get().set(blockPos, blastproof == 5 ? Hole.Type.Other : (bedrock == 5 ? Hole.Type.Bedrock : Hole.Type.Mixed), NULL));
            }
            else if (blastproof + bedrock == 8 && doubles.get() && air != null) {
                holes.add(holePool.get().set(blockPos, blastproof == 8 ? Hole.Type.Other : (bedrock == 8 ? Hole.Type.Bedrock : Hole.Type.Mixed), Dir.get(air)));
            }
        });
    }

    private boolean validHole(BlockPos pos) {
        if ((ignoreOwn.get() && (mc.player.getBlockPos().equals(pos)))) return false;

        if (!webs.get() && mc.world.getBlockState(pos).getBlock() == Blocks.COBWEB) return false;

        if (((AbstractBlockAccessor) mc.world.getBlockState(pos).getBlock()).isCollidable()) return false;

        for (int i = 0; i < holeHeight.get(); i++) {
            if (((AbstractBlockAccessor) mc.world.getBlockState(pos.up(i)).getBlock()).isCollidable()) return false;
        }

        return true;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (HoleESPPlus.Hole hole : holes) hole.render(event.renderer, shapeMode.get(), height.get(), topQuad.get(), bottomQuad.get());
    }

    private static class Hole {
        public BlockPos.Mutable blockPos = new BlockPos.Mutable();
        public byte exclude;
        public Type type;

        public Hole set(BlockPos blockPos, Type type, byte exclude) {
            this.blockPos.set(blockPos);
            this.exclude = exclude;
            this.type = type;

            return this;
        }

        public Color getTopColor() {
            return switch (this.type) {
                case Other -> Modules.get().get(HoleESPPlus.class).otherColorTop.get();
                case Bedrock  -> Modules.get().get(HoleESPPlus.class).bedrockColorTop.get();
                default       -> Modules.get().get(HoleESPPlus.class).mixedColorTop.get();
            };
        }

        public Color getBottomColor() {
            return switch (this.type) {
                case Other -> Modules.get().get(HoleESPPlus.class).otherColorBottom.get();
                case Bedrock  -> Modules.get().get(HoleESPPlus.class).bedrockColorBottom.get();
                default       -> Modules.get().get(HoleESPPlus.class).mixedColorBottom.get();
            };
        }

        public void render(Renderer3D renderer, ShapeMode mode, double height, boolean topQuad, boolean bottomQuad) {
            int x = blockPos.getX();
            int y = blockPos.getY();
            int z = blockPos.getZ();

            Color top = getTopColor();
            Color bottom = getBottomColor();

            int originalTopA = top.a;
            int originalBottompA = bottom.a;

            if (mode.lines()) {
                if (Dir.isNot(exclude, Dir.WEST) && Dir.isNot(exclude, Dir.NORTH)) renderer.line(x, y, z, x, y + height, z, bottom, top);
                if (Dir.isNot(exclude, Dir.WEST) && Dir.isNot(exclude, Dir.SOUTH)) renderer.line(x, y, z + 1, x, y + height, z + 1, bottom, top);
                if (Dir.isNot(exclude, Dir.EAST) && Dir.isNot(exclude, Dir.NORTH)) renderer.line(x + 1, y, z, x + 1, y + height, z, bottom, top);
                if (Dir.isNot(exclude, Dir.EAST) && Dir.isNot(exclude, Dir.SOUTH)) renderer.line(x + 1, y, z + 1, x + 1, y + height, z + 1, bottom, top);

                if (Dir.isNot(exclude, Dir.NORTH)) renderer.line(x, y, z, x + 1, y, z, bottom);
                if (Dir.isNot(exclude, Dir.NORTH)) renderer.line(x, y + height, z, x + 1, y + height, z, top);
                if (Dir.isNot(exclude, Dir.SOUTH)) renderer.line(x, y, z + 1, x + 1, y, z + 1, bottom);
                if (Dir.isNot(exclude, Dir.SOUTH)) renderer.line(x, y + height, z + 1, x + 1, y + height, z + 1, top);

                if (Dir.isNot(exclude, Dir.WEST)) renderer.line(x, y, z, x, y, z + 1, bottom);
                if (Dir.isNot(exclude, Dir.WEST)) renderer.line(x, y + height, z, x, y + height, z + 1, top);
                if (Dir.isNot(exclude, Dir.EAST)) renderer.line(x + 1, y, z, x + 1, y, z + 1, bottom);
                if (Dir.isNot(exclude, Dir.EAST)) renderer.line(x + 1, y + height, z, x + 1, y + height, z + 1, top);
            }

            if (mode.sides()) {
                top.a = originalTopA * 2;
                bottom.a = originalBottompA / 2;

                if (Dir.isNot(exclude, Dir.UP) && topQuad) renderer.quad(x, y + height, z, x, y + height, z + 1, x + 1, y + height, z + 1, x + 1, y + height, z, top); // Top
                if (Dir.isNot(exclude, Dir.DOWN) && bottomQuad) renderer.quad(x, y, z, x, y, z + 1, x + 1, y, z + 1, x + 1, y, z, bottom); // Bottom

                if (Dir.isNot(exclude, Dir.NORTH)) renderer.gradientQuadVertical(x, y, z, x + 1, y + height, z, top, bottom); // North
                if (Dir.isNot(exclude, Dir.SOUTH)) renderer.gradientQuadVertical(x, y, z + 1, x + 1, y + height, z + 1, top, bottom); // South

                if (Dir.isNot(exclude, Dir.WEST)) renderer.gradientQuadVertical(x, y, z, x, y + height, z + 1, top, bottom); // West
                if (Dir.isNot(exclude, Dir.EAST)) renderer.gradientQuadVertical(x + 1, y, z, x + 1, y + height, z + 1, top, bottom); // East

                top.a = originalTopA;
                bottom.a = originalBottompA;
            }
        }

        public enum Type {
            Bedrock,
            Other,
            Mixed
        }
    }
}