package dev.gigaherz.hudcompass.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.datafixers.util.Pair;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Widget;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.widget.ScrollPanel;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class ClientWaypointManagerScreen extends Screen
{
    private static final Component TITLE = Component.translatable("text.hudcompass.waypoint_editor.title");

    private static final Pattern COORD_VALIDATOR = Pattern.compile("^-?[0-9]*\\.?[0-9]*$");
    private static final Pattern COORD_FORMAT = Pattern.compile("^-?[0-9]+\\.?[0-9]+$");
    private static final Predicate<String> COORD_FORMAT_MATCHER = COORD_FORMAT.asPredicate();

    private static final int MARGIN_LEFT = 8;
    private static final int MARGIN_TOP = 32;
    private static final int MARGIN_RIGHT = 8;
    private static final int MARGIN_BOTTOM = 34;

    private final List<WaypointListItem> toAdd = new ArrayList<>();
    private final List<WaypointListItem> toUpdate = new ArrayList<>();
    private final List<WaypointListItem> toRemove = new ArrayList<>();
    private final PointsOfInterest pois;

    private ItemsScrollPanel scrollPanel;
    private Button saveButton;

    protected ClientWaypointManagerScreen(PointsOfInterest pois)
    {
        super(TITLE);
        this.pois = pois;
        pois.addListener(this::onSyncReceived);
    }

    @Override
    public void removed()
    {
        super.removed();
        pois.removeListener(this::onSyncReceived);
    }

    private void setDirty()
    {
        saveButton.active = true;
    }

    private void onSyncReceived()
    {
        scrollPanel.clear();

        loadWaypoints();

        scrollPanel.scrollTop();
    }

    private void loadWaypoints()
    {
        pois.getAllWorlds().stream().sorted(Comparator.comparing(w -> w.getWorldKey().location())).forEach(world -> {
            WorldListItem worldItem = addWorld(world.getWorldKey(), world.getDimensionTypeKey());

            for (PointInfo<?> point : world.getPoints())
            {
                if (!point.isDynamic() && point instanceof BasicWaypoint)
                {
                    BasicWaypoint wp = (BasicWaypoint) point;
                    addPoint(worldItem, wp);
                }
            }

            addNewWaypointItem(worldItem);
        });
    }

    @Override
    protected void init()
    {
        super.init();

        scrollPanel = new ItemsScrollPanel(minecraft, width - MARGIN_RIGHT - MARGIN_LEFT, height - MARGIN_TOP - MARGIN_BOTTOM, MARGIN_TOP, MARGIN_LEFT);
        addWidget(scrollPanel);

        addRenderableWidget(saveButton = new Button(8, height - 28, 120, 20, Component.translatable("text.hudcompass.waypoint_editor.save"), (button) -> {
            scrollPanel.saveAll();
            pois.updateFromGui(
                    toAdd.stream().map(i -> Pair.<ResourceLocation, PointInfo<?>>of(i.worldItem.worldKey.location(), i.pointInfo)).collect(ImmutableList.toImmutableList()),
                    toUpdate.stream().map(i -> Pair.<ResourceLocation, PointInfo<?>>of(i.worldItem.worldKey.location(), i.pointInfo)).collect(ImmutableList.toImmutableList()),
                    toRemove.stream().map(i -> i.pointInfo.getInternalId()).collect(ImmutableList.toImmutableList())
            );
            onClose();
        }));
        addRenderableWidget(new Button(width - 128, height - 28, 120, 20, Component.translatable("text.hudcompass.waypoint_editor.cancel"), (button) -> {
            onClose();
        }));

        loadWaypoints();

        scrollPanel.scrollTop();

        saveButton.active = false;
    }

    private void createNewPoint(WorldListItem worldItem)
    {
        BasicWaypoint wp = new BasicWaypoint(getPlayerPositionScaled(worldItem), "", BasicIconData.mapMarker(7));
        WaypointListItem item = new WaypointListItem(minecraft, wp, worldItem);
        int index = worldItem.waypoints.size() - 1;
        ListItem after = index >= 0 ? worldItem.waypoints.get(index) : worldItem;
        scrollPanel.insertAfter(item, after);
        worldItem.addWaypoint(item);
        toAdd.add(item);
        scrollPanel.scrollToItem(item);
        setDirty();
    }

    private Vec3 getPlayerPositionScaled(WorldListItem world)
    {
        LocalPlayer player = minecraft.player;
        Vec3 pos = player.position();
        if (player.level.dimension() == world.worldKey)
        {
            return pos;
        }

        if (world.dimensionTypeKey != null)
        {
            RegistryAccess dyn = player.connection.registryAccess();
            DimensionType type = dyn.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY).getOrThrow(world.dimensionTypeKey);
            double scale = DimensionType.getTeleportationScale(player.level.dimensionType(), type);
            return new Vec3(pos.x * scale, pos.y, pos.z * scale);
        }

        if (player.level.dimension() == Level.NETHER && world.worldKey != Level.NETHER)
        {
            return new Vec3(pos.x * 8, pos.y, pos.z * 8);
        }

        if (player.level.dimension() != Level.NETHER && world.worldKey == Level.NETHER)
        {
            return new Vec3(pos.x / 8, pos.y, pos.z / 8);
        }

        return pos;
    }

    private WorldListItem addWorld(ResourceKey<Level> worldKey, ResourceKey<DimensionType> dimensionTypeKey)
    {
        WorldListItem item = new WorldListItem(minecraft, worldKey, dimensionTypeKey);
        scrollPanel.addItem(item);
        return item;
    }

    private void addNewWaypointItem(WorldListItem item)
    {
        NewWaypointListItem newWaypoint = new NewWaypointListItem(minecraft, item);
        item.setNewWaypoint(newWaypoint);
        scrollPanel.addItem(newWaypoint);
    }

    private void addPoint(WorldListItem worldItem, BasicWaypoint wp)
    {
        WaypointListItem item = new WaypointListItem(minecraft, wp, worldItem);
        worldItem.addWaypoint(item);
        scrollPanel.addItem(item);
    }

    private void deletePoint(WaypointListItem item)
    {
        if (!toAdd.remove(item))
            toRemove.add(item);
        scrollPanel.removeItem(item);
        item.worldItem.removeWaypoint(item);
        setDirty();
    }

    @Override
    public void render(PoseStack matrixStack, int mouseX, int mouseY, float partialTicks)
    {
        renderBackground(matrixStack);

        scrollPanel.setPartialTicks(partialTicks);
        scrollPanel.render(matrixStack, mouseX, mouseY, partialTicks);

        super.render(matrixStack, mouseX, mouseY, partialTicks);

        drawCenteredString(matrixStack, minecraft.font, title, width / 2, 7, 0xFFFFFFFF);

        int nameWidth = Math.max(scrollPanel.getContentWidth() - (61 * 2 + 41 + 23 + 23 + 3), 50);
        int x = scrollPanel.getLeft() + 6;
        int y = scrollPanel.getTop() - 10;
        drawString(matrixStack, minecraft.font, Component.translatable("text.hudcompass.waypoint_editor.header_label"), x, y, 0xFFFFFFFF);
        x += nameWidth + 3;
        drawString(matrixStack, minecraft.font, Component.translatable("text.hudcompass.waypoint_editor.header_x"), x, y, 0xFFFFFFFF);
        x += 61;
        drawString(matrixStack, minecraft.font, Component.translatable("text.hudcompass.waypoint_editor.header_y"), x, y, 0xFFFFFFFF);
        x += 41;
        drawString(matrixStack, minecraft.font, Component.translatable("text.hudcompass.waypoint_editor.header_z"), x, y, 0xFFFFFFFF);
    }

    private class WorldListItem extends CompositeListItem
    {
        private final Component title;
        private final ResourceKey<Level> worldKey;
        private final List<WaypointListItem> waypoints = Lists.newArrayList();
        private final ResourceKey<DimensionType> dimensionTypeKey;
        private boolean folded;

        public void setNewWaypoint(NewWaypointListItem newWaypoint)
        {
            this.newWaypoint = newWaypoint;
        }

        private NewWaypointListItem newWaypoint;

        public WorldListItem(Minecraft minecraft, ResourceKey<Level> key, ResourceKey<DimensionType> dimensionTypeKey)
        {
            super(minecraft, 22);

            this.title = Component.translatable("text.hudcompass.waypoint_editor.world", key.location());
            this.worldKey = key;
            this.dimensionTypeKey = dimensionTypeKey;
        }

        @Override
        public void init()
        {
            super.init();

            addWidget(new Button(1, 1, 20, 20, Component.translatable("text.hudcompass.waypoint_editor.fold"), (button) -> {
                folded = !folded;
                if (folded)
                {
                    button.setMessage(Component.translatable("text.hudcompass.waypoint_editor.unfold"));
                }
                else
                {
                    button.setMessage(Component.translatable("text.hudcompass.waypoint_editor.fold"));
                }
                waypoints.forEach(w -> w.setVisible(!folded));
                newWaypoint.setVisible(!folded);
                scrollPanel.recalculateHeight();
            }));
        }

        @Override
        public void render(PoseStack matrixStack, int mouseX, int mouseY, float partialTicks)
        {
            super.render(matrixStack, mouseX, mouseY, partialTicks);

            drawString(matrixStack, minecraft.font, title, 4 + 20, 10, 0xFFFFFFFF);
        }

        public void addWaypoint(WaypointListItem item)
        {
            this.waypoints.add(item);
        }

        public void removeWaypoint(WaypointListItem item)
        {
            this.waypoints.remove(item);
        }
    }

    private class NewWaypointListItem extends CompositeListItem
    {
        WorldListItem owner;

        public NewWaypointListItem(Minecraft minecraft, WorldListItem owner)
        {
            super(minecraft, 24);

            this.owner = owner;
        }

        @Override
        public void init()
        {
            super.init();

            addWidget(new Button(getWidth() - 121, 1, 120, 20, Component.translatable("text.hudcompass.waypoint_editor.new_waypoint"), (button) -> {
                createNewPoint(owner);
            }));
        }
    }

    private class WaypointListItem extends CompositeListItem
    {
        private final BasicWaypoint pointInfo;
        private final WorldListItem worldItem;
        private EditBox label;
        private EditBox xCoord;
        private EditBox yCoord;
        private EditBox zCoord;
        private Button changeSymbol;
        private Button delete;
        private String labelText;
        private String xText;
        private String yText;
        private String zText;

        public WaypointListItem(Minecraft minecraft, BasicWaypoint pointInfo, WorldListItem worldItem)
        {
            super(minecraft, 22);

            this.pointInfo = pointInfo;
            this.worldItem = worldItem;
        }

        @Override
        public void init()
        {
            super.init();

            Vec3 pos = pointInfo.getPosition();

            int nameWidth = Math.max(getWidth() - (61 * 2 + 41 + 23 + 23 + 3), 50);

            int x = 2;
            addWidget(label = new EditBox(minecraft.font, x + 1, 2, nameWidth, 16, Component.translatable("text.hudcompass.waypoint_editor.header_label")));
            x += nameWidth + 3;
            addWidget(xCoord = new EditBox(minecraft.font, x + 1, 2, 60, 16, Component.translatable("text.hudcompass.waypoint_editor.header_x")));
            x += 61;
            addWidget(yCoord = new EditBox(minecraft.font, x + 1, 2, 40, 16, Component.translatable("text.hudcompass.waypoint_editor.header_y")));
            x += 41;
            addWidget(zCoord = new EditBox(minecraft.font, x + 1, 2, 60, 16, Component.translatable("text.hudcompass.waypoint_editor.header_z")));
            x += 63;
            addWidget(changeSymbol = new Button(x, 0, 20, 20, Component.translatable("text.hudcompass.waypoint_editor.change_symbol"), (button) -> {
            }));
            x += 21;
            addWidget(delete = new Button(x, 0, 20, 20, Component.translatable("text.hudcompass.waypoint_editor.delete"), (button) -> {
                deletePoint(this);
            }));

            changeSymbol.active = false;

            label.setMaxLength(1024);

            label.setValue(pointInfo.getLabelText());
            xCoord.setValue(String.format(Locale.ROOT, "%1.2f", pos.x));
            yCoord.setValue(String.format(Locale.ROOT, "%1.2f", pos.y));
            zCoord.setValue(String.format(Locale.ROOT, "%1.2f", pos.z));

            xCoord.setFilter(COORD_VALIDATOR.asPredicate());
            yCoord.setFilter(COORD_VALIDATOR.asPredicate());
            zCoord.setFilter(COORD_VALIDATOR.asPredicate());

            label.setResponder(str -> {
                this.labelText = str != null ? str : "";
                this.setDirty();
            });
            xCoord.setResponder(str -> {
                this.xText = str != null ? str : "";
                this.setDirty();
            });
            yCoord.setResponder(str -> {
                this.yText = str != null ? str : "";
                this.setDirty();
            });
            zCoord.setResponder(str -> {
                this.zText = str != null ? str : "";
                this.setDirty();
            });
        }

        @Override
        public void save()
        {
            if (isDirty())
            {
                labelText = label.getValue();
                xText = xCoord.getValue();
                yText = yCoord.getValue();
                zText = zCoord.getValue();

                if (COORD_FORMAT_MATCHER.test(xText) && COORD_FORMAT_MATCHER.test(yText) && COORD_FORMAT_MATCHER.test(zText))
                {
                    pointInfo.setLabelText(labelText == null ? "" : labelText);
                    pointInfo.setPosition(new Vec3(Double.parseDouble(xText), Double.parseDouble(yText), Double.parseDouble(zText)));
                }

                if (!toAdd.contains(this))
                    toUpdate.add(this);
            }
        }
    }

    private static class CompositeListItem extends ListItem
    {
        private final List<Widget> renderables = Lists.newArrayList();
        private final List<GuiEventListener> listeners = Lists.newArrayList();

        public CompositeListItem(Minecraft minecraft, int height)
        {
            super(minecraft, height);
        }

        @Override
        public void init()
        {
            super.init();

            renderables.clear();
            listeners.clear();
        }

        public CompositeListItem addWidget(Widget widget)
        {
            if (widget instanceof GuiEventListener)
                addListener((GuiEventListener) widget);
            renderables.add(widget);
            return this;
        }

        public CompositeListItem addListener(GuiEventListener listener)
        {
            listeners.add(listener);
            return this;
        }

        @Override
        public List<? extends GuiEventListener> children()
        {
            return listeners;
        }


        private int getActualX(int mouseX)
        {
            int x = mouseX;
            if (getParent() != null) x -= getParent().getLeft();
            return x;
        }

        private double getActualX(double mouseX)
        {
            double x = mouseX;
            if (getParent() != null) x -= getParent().getLeft();
            return x;
        }

        private int getActualY(int mouseY)
        {
            int y = mouseY - getTop();
            if (getParent() != null) y -= getParent().getContentTop();
            return y;
        }

        private double getActualY(double mouseY)
        {
            double y = mouseY - getTop();
            if (getParent() != null) y -= getParent().getContentTop();
            return y;
        }

        @Override
        public void render(PoseStack matrixStack, int mouseX, int mouseY, float partialTicks)
        {
            if (!isVisible())
                return;
            int actualMouseX = getActualX(mouseX);
            int actualMouseY = getActualY(mouseY);
            if (actualMouseX >= 0 && actualMouseX < getWidth() && actualMouseY >= 0 && actualMouseY <= getHeight())
            {
                fill(matrixStack, 0, 0, getWidth(), getHeight(), 0x1fFFFFFF);
            }
            for (Widget i : renderables)
            {
                i.render(matrixStack, actualMouseX, actualMouseY, partialTicks);
            }
        }

        @Override
        public Optional<GuiEventListener> getChildAt(double mouseX, double mouseY)
        {
            if (!isVisible())
                return Optional.empty();
            double actualMouseX = getActualX(mouseX);
            double actualMouseY = getActualY(mouseY);
            for (GuiEventListener iguieventlistener : this.children())
            {
                if (iguieventlistener.isMouseOver(actualMouseX, actualMouseY))
                {
                    return Optional.of(iguieventlistener);
                }
            }

            return Optional.empty();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button)
        {
            if (!isVisible())
                return false;
            double actualMouseX = getActualX(mouseX);
            double actualMouseY = getActualY(mouseY);
            for (GuiEventListener iguieventlistener : this.children())
            {
                if (iguieventlistener.mouseClicked(actualMouseX, actualMouseY, button))
                {
                    this.setFocused(iguieventlistener);
                    if (button == 0)
                    {
                        this.setDragging(true);
                    }

                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button)
        {
            if (!isVisible())
                return false;
            double actualMouseX = getActualX(mouseX);
            double actualMouseY = getActualY(mouseY);
            this.setDragging(false);
            return this.getChildAt(mouseX, mouseY).filter((listener) -> {
                return listener.mouseReleased(actualMouseX, actualMouseY, button);
            }).isPresent();
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
        {
            if (!isVisible())
                return false;
            double actualMouseX = getActualX(mouseX);
            double actualMouseY = getActualY(mouseY);
            return this.getFocused() != null && this.isDragging() && button == 0
                    && this.getFocused().mouseDragged(actualMouseX, actualMouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta)
        {
            if (!isVisible())
                return false;
            double actualMouseX = getActualX(mouseX);
            double actualMouseY = getActualY(mouseY);
            return this.getChildAt(mouseX, mouseY).filter((listener) -> {
                return listener.mouseScrolled(actualMouseX, actualMouseY, delta);
            }).isPresent();
        }

        @Override
        public boolean changeFocus(boolean focus)
        {
            if (!isVisible())
                return false;
            GuiEventListener iguieventlistener = this.getFocused();
            boolean flag = iguieventlistener != null;
            if (flag && iguieventlistener.changeFocus(focus))
            {
                return true;
            }
            else
            {
                List<? extends GuiEventListener> list = this.children();
                int j = list.indexOf(iguieventlistener);
                int i;
                if (flag && j >= 0)
                {
                    i = j + (focus ? 1 : 0);
                }
                else if (focus)
                {
                    i = 0;
                }
                else
                {
                    i = list.size();
                }

                ListIterator<? extends GuiEventListener> listiterator = list.listIterator(i);
                BooleanSupplier booleansupplier = focus ? listiterator::hasNext : listiterator::hasPrevious;
                Supplier<? extends GuiEventListener> supplier = focus ? listiterator::next : listiterator::previous;

                while (booleansupplier.getAsBoolean())
                {
                    GuiEventListener iguieventlistener1 = supplier.get();
                    if (iguieventlistener1.changeFocus(focus))
                    {
                        this.setFocused(iguieventlistener1);
                        return true;
                    }
                }

                this.setFocused((GuiEventListener) null);
                return false;
            }
        }
    }

    private static abstract class ListItem extends AbstractContainerEventHandler implements Widget
    {
        protected final Minecraft minecraft;

        private ItemsScrollPanel parent;
        private int height;
        private int width;
        private int top;
        private boolean visible = true;

        public ListItem(Minecraft minecraft, int height)
        {
            this.minecraft = minecraft;
            this.height = height;
        }

        public int getTop()
        {
            return top;
        }

        public void setTop(int top)
        {
            this.top = top;
        }

        public int getHeight()
        {
            return height;
        }

        public void setHeight(int height)
        {
            this.height = height;
            if (parent != null)
                parent.recalculateHeight();
        }

        public int getWidth()
        {
            return width;
        }

        public void setWidth(int width)
        {
            this.width = width;
        }

        @Nullable
        protected ItemsScrollPanel getParent()
        {
            return parent;
        }

        public void setParent(ItemsScrollPanel parent)
        {
            this.parent = parent;
        }

        public void init()
        {
        }

        @Override
        public void render(PoseStack matrixStack, int mouseX, int mouseY, float partialTicks)
        {

        }

        public void save()
        {
        }

        private boolean dirty;

        protected boolean isDirty()
        {
            return dirty;
        }

        protected void setDirty()
        {
            dirty = true;
            if (parent != null)
                parent.setDirty();
        }

        public boolean isVisible()
        {
            return visible;
        }

        public void setVisible(boolean visible)
        {
            if (this.visible != visible)
            {
                this.visible = visible;
            }
        }
    }

    private class ItemsScrollPanel extends ScrollPanel implements NarratableEntry
    {
        private final List<ListItem> items = Lists.newArrayList();
        private final Minecraft minecraft;

        private int contentHeight;
        private float partialTicks;

        public ItemsScrollPanel(Minecraft client, int width, int height, int top, int left)
        {
            super(client, width, height, top, left);
            this.minecraft = client;
        }

        public void addItem(ListItem item)
        {
            addItem(items.size(), item);
        }

        public void insertAfter(WaypointListItem item, ListItem after)
        {
            int index = items.indexOf(after);
            addItem(index >= 0 ? (index + 1) : items.size(), item);
        }

        public void addItem(int index, ListItem item)
        {
            items.add(index, item);
            item.setParent(this);
            recalculateHeight();
            item.setWidth(getContentWidth());
            item.init();
        }


        @Override
        protected int getContentHeight()
        {
            return contentHeight;
        }

        public void setPartialTicks(float partialTicks)
        {
            this.partialTicks = partialTicks;
        }

        @Override
        protected void drawPanel(PoseStack mStack, int entryRight, int relativeY, Tesselator tess, int mouseX, int mouseY)
        {
            mStack.pushPose();
            mStack.translate(left, relativeY, 0);
            for (ListItem item : items)
            {
                if (item.isVisible())
                {
                    mStack.pushPose();
                    mStack.translate(0, item.getTop(), 0);
                    item.render(mStack, mouseX, mouseY, partialTicks);
                    mStack.popPose();
                }
            }
            mStack.popPose();
        }

        @Override
        public List<? extends GuiEventListener> children()
        {
            return items;
        }

        public void recalculateHeight()
        {
            int totalHeight = 0;
            for (ListItem item : items)
            {
                if (item.isVisible())
                {
                    item.setTop(totalHeight);
                    totalHeight += item.getHeight();
                }
            }
            contentHeight = totalHeight;
        }

        public int getLeft()
        {
            return left;
        }

        public int getTop()
        {
            return top;
        }

        public int getContentTop()
        {
            int baseY = this.top + border - (int) this.scrollDistance;
            return baseY;
        }

        public void saveAll()
        {
            items.forEach(item -> item.save());
        }

        public int getContentWidth()
        {
            return right - left - 6;
        }

        public void removeItem(ListItem item)
        {
            item.setParent(null);
            items.remove(item);
            recalculateHeight();
        }

        public void scrollToItem(ListItem item)
        {
            int scrollOffset = item.getTop() - height / 2 - item.getHeight();
            this.scrollDistance = Mth.clamp(scrollOffset, 0, Math.max(0, getContentHeight() - (height - border)));
        }

        @Override
        public void render(PoseStack matrix, int mouseX, int mouseY, float partialTicks)
        {
            this.scrollDistance = Mth.clamp(scrollDistance, 0, Math.max(0, getContentHeight() - (height - border)));
            super.render(matrix, mouseX, mouseY, partialTicks);
        }

        public void scrollTop()
        {
            scrollDistance = 0;
        }

        public void clear()
        {
            items.forEach(item -> item.setParent(null));
            items.clear();
        }


        private boolean dirty;

        protected void setDirty()
        {
            dirty = true;
            ClientWaypointManagerScreen.this.setDirty();
        }

        @Override
        public NarrationPriority narrationPriority()
        {
            return NarrationPriority.NONE;
        }

        @Override
        public void updateNarration(NarrationElementOutput narrationElementOutput)
        {

        }
    }
}
