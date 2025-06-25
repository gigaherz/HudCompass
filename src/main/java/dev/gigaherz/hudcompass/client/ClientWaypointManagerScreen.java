package dev.gigaherz.hudcompass.client;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.vertex.Tesselator;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import gigaherz.hudcompass.waypoints.PointAddRemoveEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.gui.widget.ScrollPanel;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;
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
                if (!point.isDynamic() && point instanceof BasicWaypoint wp)
                {
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

        scrollPanel = addRenderableWidget(new ItemsScrollPanel(minecraft, width - MARGIN_RIGHT - MARGIN_LEFT, height - MARGIN_TOP - MARGIN_BOTTOM, MARGIN_TOP, MARGIN_LEFT));

        saveButton = addRenderableWidget(Button.builder(Component.translatable("text.hudcompass.waypoint_editor.save"), (button) -> {
            scrollPanel.saveAll();
            pois.updateFromGui(
                    toAdd.stream().map(i -> new PointAddRemoveEntry(i.worldItem.worldKey.location(), i.pointInfo)).toList(),
                    toUpdate.stream().map(i -> new PointAddRemoveEntry(i.worldItem.worldKey.location(), i.pointInfo)).toList(),
                    toRemove.stream().map(i -> i.pointInfo.getInternalId()).toList()
            );
            onClose();
        }).pos(8, height - 28).size(120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("text.hudcompass.waypoint_editor.cancel"), (button) -> {
            onClose();
        }).pos(width - 128, height - 28).size(120, 20).build());

        loadWaypoints();

        scrollPanel.scrollTop();

        saveButton.active = false;
    }

    private void createNewPoint(WorldListItem worldItem)
    {
        BasicWaypoint wp = new BasicWaypoint(getPlayerPositionScaled(worldItem), "", BasicIconData.mapDecoration("player_off_limits"));
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
        if (player.level().dimension() == world.worldKey)
        {
            return pos;
        }

        if (world.dimensionTypeKey != null)
        {
            RegistryAccess dyn = player.connection.registryAccess();
            var type = dyn.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(world.dimensionTypeKey);
            double scale = DimensionType.getTeleportationScale(player.level().dimensionType(), type.value());
            return new Vec3(pos.x * scale, pos.y, pos.z * scale);
        }

        if (player.level().dimension() == Level.NETHER && world.worldKey != Level.NETHER)
        {
            return new Vec3(pos.x * 8, pos.y, pos.z * 8);
        }

        if (player.level().dimension() != Level.NETHER && world.worldKey == Level.NETHER)
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks)
    {
        //renderBackground(graphics, mouseX, mouseY, partialTicks);

        scrollPanel.setPartialTicks(partialTicks);
        scrollPanel.render(graphics, mouseX, mouseY, partialTicks);

        super.render(graphics, mouseX, mouseY, partialTicks);

        graphics.drawCenteredString(minecraft.font, title, width / 2, 7, 0xFFFFFFFF);

        int nameWidth = Math.max(scrollPanel.getContentWidth() - (61 * 2 + 41 + 23 + 23 + 3), 50);
        int x = scrollPanel.getLeft() + 6;
        int y = scrollPanel.getTop() - 10;
        graphics.drawString(minecraft.font, Component.translatable("text.hudcompass.waypoint_editor.header_label"), x, y, 0xFFFFFFFF, false);
        x += nameWidth + 3;
        graphics.drawString(minecraft.font, Component.translatable("text.hudcompass.waypoint_editor.header_x"), x, y, 0xFFFFFFFF, false);
        x += 61;
        graphics.drawString(minecraft.font, Component.translatable("text.hudcompass.waypoint_editor.header_y"), x, y, 0xFFFFFFFF, false);
        x += 41;
        graphics.drawString(minecraft.font, Component.translatable("text.hudcompass.waypoint_editor.header_z"), x, y, 0xFFFFFFFF, false);
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

            this.title = Component.translatable("text.hudcompass.waypoint_editor.world", key.location().toString());
            this.worldKey = key;
            this.dimensionTypeKey = dimensionTypeKey;
        }

        @Override
        public void init()
        {
            super.init();

            addWidget(Button.builder(Component.translatable("text.hudcompass.waypoint_editor.fold"), (button) -> {
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
            }).pos(1, 1).size(20, 20).build());
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks)
        {
            super.render(graphics, mouseX, mouseY, partialTicks);

            graphics.drawString(minecraft.font, title, 4 + 20, 10, 0xFFFFFFFF, false);
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

            addWidget(Button.builder(Component.translatable("text.hudcompass.waypoint_editor.new_waypoint"), (button) -> {
                createNewPoint(owner);
            }).pos(getWidth() - 121, 1).size(120, 20).build());
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
            label = addWidget(new EditBox(minecraft.font, x + 1, 2, nameWidth, 16, Component.translatable("text.hudcompass.waypoint_editor.header_label")));
            x += nameWidth + 3;
            xCoord = addWidget(new EditBox(minecraft.font, x + 1, 2, 60, 16, Component.translatable("text.hudcompass.waypoint_editor.header_x")));
            x += 61;
            yCoord = addWidget(new EditBox(minecraft.font, x + 1, 2, 40, 16, Component.translatable("text.hudcompass.waypoint_editor.header_y")));
            x += 41;
            zCoord = addWidget(new EditBox(minecraft.font, x + 1, 2, 60, 16, Component.translatable("text.hudcompass.waypoint_editor.header_z")));
            x += 63;
            changeSymbol = addWidget(Button.builder(Component.translatable("text.hudcompass.waypoint_editor.change_symbol"), (button) -> {
            }).pos(x, 0).size(20, 20).build());
            x += 21;
            delete = addWidget(Button.builder(Component.translatable("text.hudcompass.waypoint_editor.delete"), (button) -> {
                deletePoint(this);
            }).pos(x, 0).size(20, 20).build());

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
        private final List<AbstractWidget> renderables = Lists.newArrayList();

        public CompositeListItem(Minecraft minecraft, int height)
        {
            super(minecraft, height);
        }

        @Override
        public void init()
        {
            super.init();

            renderables.clear();
        }

        public <T extends AbstractWidget> T addWidget(T widget)
        {
            renderables.add(widget);
            return widget;
        }

        @Override
        public List<? extends GuiEventListener> children()
        {
            return renderables;
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
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks)
        {
            if (!isVisible())
                return;
            int actualMouseX = getActualX(mouseX);
            int actualMouseY = getActualY(mouseY);
            if (actualMouseX >= 0 && actualMouseX < getWidth() && actualMouseY >= 0 && actualMouseY <= getHeight())
            {
                graphics.fill(0, 0, getWidth(), getHeight(), 0x1fFFFFFF);
            }
            for (AbstractWidget i : renderables)
            {
                i.render(graphics, actualMouseX, actualMouseY, partialTicks);
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
            for (GuiEventListener item : this.children())
            {
                if (item.mouseClicked(actualMouseX, actualMouseY, button))
                {
                    this.setFocused(item);
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
            return this.getChildAt(mouseX, mouseY)
                    .filter((listener) -> listener.mouseReleased(actualMouseX, actualMouseY, button))
                    .isPresent();
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
        public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY)
        {
            if (!isVisible())
                return false;
            double actualMouseX = getActualX(mouseX);
            double actualMouseY = getActualY(mouseY);
            return this.getChildAt(mouseX, mouseY)
                    .filter((listener) -> listener.mouseScrolled(actualMouseX, actualMouseY, deltaX, deltaY))
                    .isPresent();
        }

        @Override
        public void setFocused(boolean focused)
        {
            if (!focused)
            {
                children().forEach(item -> item.setFocused(false));
            }
        }

        @Override
        public NarrationPriority narrationPriority()
        {
            return NarrationPriority.NONE;
        }

        @Override
        public void updateNarration(NarrationElementOutput narrationElementOutput)
        {
            children().forEach(item ->
            {
                if (item instanceof NarratableEntry narratableEntry)
                    narratableEntry.updateNarration(narrationElementOutput.nest());
            });
        }
    }

    private interface HierarchyParent
    {
        void recalculateHeight();

        void setDirty();

        int getLeft();

        int getContentTop();

        void focusChanged(GuiEventListener newFocus, ListItem itemParent);
    }

    private static abstract class ListItem implements ContainerEventHandler, NarratableEntry
    {
        protected final Minecraft minecraft;

        @Nullable
        private HierarchyParent parent;
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
        public HierarchyParent getParent()
        {
            return parent;
        }

        public void setParent(@Nullable HierarchyParent parent)
        {
            this.parent = parent;
        }

        public void init()
        {
        }

        public abstract void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks);

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

        // AbstractContainerEventHandler
        @Nullable
        private GuiEventListener focused;
        private boolean isDragging;

        @Override
        public final boolean isDragging() {
            return this.isDragging;
        }

        @Override
        public final void setDragging(boolean pDragging) {
            this.isDragging = pDragging;
        }

        @Override
        @Nullable
        public GuiEventListener getFocused() {
            return this.focused;
        }

        @Override
        public void setFocused(@Nullable GuiEventListener newFocus) {
            if (parent != null)
                parent.focusChanged(newFocus, this);

            if (this.focused != null) {
                this.focused.setFocused(false);
            }

            if (newFocus != null) {
                newFocus.setFocused(true);
            }

            this.focused = newFocus;
        }
    }

    private class ItemsScrollPanel extends ScrollPanel implements NarratableEntry, HierarchyParent
    {
        private final List<ListItem> items = Lists.newArrayList();

        private int contentHeight;
        private float partialTicks;

        public ItemsScrollPanel(Minecraft client, int width, int height, int top, int left)
        {
            super(client, width, height, top, left);
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
        protected void drawBackground(GuiGraphics graphics, float partialTick)
        {
            super.drawBackground(graphics, partialTick);
            this.partialTicks = partialTick;
        }

        @Override
        protected void drawPanel(GuiGraphics graphics, int entryRight, int relativeY, int mouseX, int mouseY)
        {
            var mStack = graphics.pose();
            mStack.pushMatrix();
            mStack.translate(left, relativeY);
            for (ListItem item : items)
            {
                if (item.isVisible())
                {
                    mStack.pushMatrix();
                    mStack.translate(0, item.getTop());
                    item.render(graphics, mouseX, mouseY, partialTicks);
                    mStack.popMatrix();
                }
            }
            mStack.popMatrix();
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
            return this.top + border - (int) this.scrollDistance;
        }

        @Override
        public void focusChanged(GuiEventListener newFocus, ListItem itemParent)
        {
            if (getFocused() != itemParent)
            {
                setFocused(itemParent);
            }

            this.bringIntoView(itemParent);
        }

        public void saveAll()
        {
            items.forEach(ListItem::save);
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

        public void bringIntoView(ListItem item)
        {
            if(item.top < this.scrollDistance)
            {
                this.scrollDistance = Math.clamp(item.top, 0, Math.max(0, getContentHeight() - (height - border)));
            }
            else if((item.top + item.height) > (this.scrollDistance + this.height))
            {
                var scrollOffset = item.top + item.height - this.height;
                this.scrollDistance = Math.clamp(scrollOffset, 0, Math.max(0, getContentHeight() - (height - border)));
            }
        }

        public void scrollToItem(ListItem item)
        {
            int scrollOffset = item.getTop() - height / 2 - item.getHeight();
            this.scrollDistance = Mth.clamp(scrollOffset, 0, Math.max(0, getContentHeight() - (height - border)));
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks)
        {
            this.scrollDistance = Mth.clamp(scrollDistance, 0, Math.max(0, getContentHeight() - (height - border)));
            super.render(graphics, mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button)
        {
            if (super.mouseClicked(mouseX, mouseY, button))
                return true;

            for (GuiEventListener item : this.children())
            {
                if (item.mouseClicked(mouseX, mouseY, button))
                {
                    //this.setFocused(item);
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
            if (super.mouseReleased(mouseX, mouseY, button))
                return true;

            this.setDragging(false);
            return this.getChildAt(mouseX, mouseY)
                    .filter((listener) -> listener.mouseReleased(mouseX, mouseY, button))
                    .isPresent();
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
        {
            if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY))
                return true;

            return this.getFocused() != null
                    && this.isDragging()
                    && button == 0
                    && this.getFocused().mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY)
        {
            if (super.mouseScrolled(mouseX, mouseY, deltaX, deltaY))
                return true;

            return this.getChildAt(mouseX, mouseY)
                    .filter((listener) -> listener.mouseScrolled(mouseX, mouseY, deltaX, deltaY))
                    .isPresent();
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

        public void setDirty()
        {
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
            items.forEach(item -> item.updateNarration(narrationElementOutput.nest()));
        }
    }

}
