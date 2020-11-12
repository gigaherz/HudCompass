package dev.gigaherz.hudcompass.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.datafixers.util.Pair;
import dev.gigaherz.hudcompass.icons.BasicIconData;
import dev.gigaherz.hudcompass.waypoints.BasicWaypoint;
import dev.gigaherz.hudcompass.waypoints.PointInfo;
import dev.gigaherz.hudcompass.waypoints.PointsOfInterest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.FocusableGui;
import net.minecraft.client.gui.IGuiEventListener;
import net.minecraft.client.gui.IRenderable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraftforge.client.gui.ScrollPanel;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class ClientWaypointManagerScreen extends Screen
{
    private static final ITextComponent TITLE = new TranslationTextComponent("text.hudcompass.waypoint_editor.title");

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
    public void onClose()
    {
        super.onClose();
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
        pois.getAllWorlds().stream().sorted(Comparator.comparing(w -> w.getWorldKey().getLocation())).forEach(world -> {
            WorldListItem worldItem = addWorld(world.getWorldKey(), world.getDimensionTypeKey());

            for (PointInfo<?> point : world.getPoints())
            {
                if (point instanceof BasicWaypoint)
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
        addListener(scrollPanel);

        addButton(saveButton = new Button(8,height-28,120,20, new TranslationTextComponent("text.hudcompass.waypoint_editor.save"), (button) -> {
            scrollPanel.saveAll();
            pois.sendUpdateFromGui(
                    toAdd.stream().map(i -> Pair.<ResourceLocation, PointInfo<?>>of(i.worldItem.worldKey.getLocation(), i.pointInfo)).collect(ImmutableList.toImmutableList()),
                    toUpdate.stream().map(i -> Pair.<ResourceLocation, PointInfo<?>>of(i.worldItem.worldKey.getLocation(), i.pointInfo)).collect(ImmutableList.toImmutableList()),
                    toRemove.stream().map(i -> i.pointInfo.getInternalId()).collect(ImmutableList.toImmutableList())
            );
            closeScreen();
        }));
        addButton(new Button(width-128,height-28,120,20, new TranslationTextComponent("text.hudcompass.waypoint_editor.cancel"), (button) -> {
            closeScreen();
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

    private Vector3d getPlayerPositionScaled(WorldListItem world)
    {
        ClientPlayerEntity player = minecraft.player;
        Vector3d pos = player.getPositionVec();
        if (player.world.getDimensionKey() != world.worldKey)
        {
            if (world.dimensionTypeKey != null)
            {
                DynamicRegistries dyn = player.connection.func_239165_n_();
                DimensionType type = dyn.func_230520_a_().getOrThrow(world.dimensionTypeKey);
                double scale = DimensionType.getCoordinateDifference(player.world.getDimensionType(), type);
                pos = new Vector3d(pos.x * scale, pos.y, pos.z * scale);
            }
            else if (player.world.getDimensionKey() == World.THE_NETHER && world.worldKey != World.THE_NETHER)
            {
                pos = new Vector3d(pos.x*8, pos.y, pos.z*8);
            }
            else if (player.world.getDimensionKey() != World.THE_NETHER && world.worldKey == World.THE_NETHER)
            {
                pos = new Vector3d(pos.x/8, pos.y, pos.z/8);
            }
        }
        return pos;
    }

    private WorldListItem addWorld(RegistryKey<World> worldKey, RegistryKey<DimensionType> dimensionTypeKey)
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
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks)
    {
        renderBackground(matrixStack);

        scrollPanel.setPartialTicks(partialTicks);
        scrollPanel.render(matrixStack, mouseX, mouseY, partialTicks);

        super.render(matrixStack, mouseX, mouseY, partialTicks);

        drawCenteredString(matrixStack, minecraft.fontRenderer, title, width/2, 7, 0xFFFFFFFF);

        int nameWidth = Math.max(scrollPanel.getContentWidth() - (61*2+41+23+23+3), 50);
        int x = scrollPanel.getLeft() + 6;
        int y = scrollPanel.getTop() - 10;
        drawString(matrixStack, minecraft.fontRenderer,  new TranslationTextComponent("text.hudcompass.waypoint_editor.header_label"), x, y, 0xFFFFFFFF);
        x+=nameWidth+3;
        drawString(matrixStack, minecraft.fontRenderer,  new TranslationTextComponent("text.hudcompass.waypoint_editor.header_x"), x, y, 0xFFFFFFFF);
        x+=61;
        drawString(matrixStack, minecraft.fontRenderer,  new TranslationTextComponent("text.hudcompass.waypoint_editor.header_y"), x, y, 0xFFFFFFFF);
        x+=41;
        drawString(matrixStack, minecraft.fontRenderer,  new TranslationTextComponent("text.hudcompass.waypoint_editor.header_z"), x, y, 0xFFFFFFFF);
    }

    private class WorldListItem extends CompositeListItem
    {
        private final TranslationTextComponent title;
        private final RegistryKey<World> worldKey;
        private final List<WaypointListItem> waypoints = Lists.newArrayList();
        private final RegistryKey<DimensionType> dimensionTypeKey;
        private boolean folded;

        public void setNewWaypoint(NewWaypointListItem newWaypoint)
        {
            this.newWaypoint = newWaypoint;
        }

        private NewWaypointListItem newWaypoint;

        public WorldListItem(Minecraft minecraft, RegistryKey<World> key, RegistryKey<DimensionType> dimensionTypeKey)
        {
            super(minecraft, 22);

            this.title = new TranslationTextComponent("text.hudcompass.waypoint_editor.world", key.getLocation());
            this.worldKey = key;
            this.dimensionTypeKey = dimensionTypeKey;
        }

        @Override
        public void init()
        {
            super.init();

            addWidget(new Button(1,1,20,20, new TranslationTextComponent("text.hudcompass.waypoint_editor.fold"), (button) -> {
                folded = !folded;
                if (folded) {
                    button.setMessage(new TranslationTextComponent("text.hudcompass.waypoint_editor.unfold"));
                }
                else
                {
                    button.setMessage(new TranslationTextComponent("text.hudcompass.waypoint_editor.fold"));
                }
                waypoints.forEach(w -> w.setVisible(!folded));
                newWaypoint.setVisible(!folded);
                scrollPanel.recalculateHeight();
            }));
        }

        @Override
        public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks)
        {
            super.render(matrixStack, mouseX, mouseY, partialTicks);

            drawString(matrixStack, minecraft.fontRenderer, title, 4+20, 10, 0xFFFFFFFF);
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

            addWidget(new Button(getWidth()-121,1,120,20, new TranslationTextComponent("text.hudcompass.waypoint_editor.new_waypoint"), (button) -> {
                createNewPoint(owner);
            }));
        }
    }

    private class WaypointListItem extends CompositeListItem
    {
        private final BasicWaypoint pointInfo;
        private final WorldListItem worldItem;
        private TextFieldWidget label;
        private TextFieldWidget xCoord;
        private TextFieldWidget yCoord;
        private TextFieldWidget zCoord;
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

            Vector3d pos = pointInfo.getPosition();

            int nameWidth = Math.max(getWidth() - (61*2+41+23+23+3), 50);

            int x = 2;
            addWidget(label = new TextFieldWidget(minecraft.fontRenderer, x+1, 2, nameWidth, 16, new TranslationTextComponent("text.hudcompass.waypoint_editor.header_label")));
            x+=nameWidth+3;
            addWidget(xCoord = new TextFieldWidget(minecraft.fontRenderer, x+1, 2, 60, 16, new TranslationTextComponent("text.hudcompass.waypoint_editor.header_x")));
            x+=61;
            addWidget(yCoord = new TextFieldWidget(minecraft.fontRenderer, x+1, 2, 40, 16, new TranslationTextComponent("text.hudcompass.waypoint_editor.header_y")));
            x+=41;
            addWidget(zCoord = new TextFieldWidget(minecraft.fontRenderer, x+1, 2, 60, 16, new TranslationTextComponent("text.hudcompass.waypoint_editor.header_z")));
            x+=63;
            addWidget(changeSymbol = new Button(x, 0, 20, 20, new TranslationTextComponent("text.hudcompass.waypoint_editor.change_symbol"), (button) -> {
            }));
            x+=21;
            addWidget(delete = new Button(x, 0, 20, 20, new TranslationTextComponent("text.hudcompass.waypoint_editor.delete"), (button) -> {
                deletePoint(this);
            }));

            changeSymbol.active = false;

            label.setMaxStringLength(1024);

            label.setText(pointInfo.getLabelText());
            xCoord.setText(String.format(Locale.ROOT, "%1.2f", pos.x));
            yCoord.setText(String.format(Locale.ROOT, "%1.2f", pos.y));
            zCoord.setText(String.format(Locale.ROOT, "%1.2f", pos.z));

            xCoord.setValidator(COORD_VALIDATOR.asPredicate());
            yCoord.setValidator(COORD_VALIDATOR.asPredicate());
            zCoord.setValidator(COORD_VALIDATOR.asPredicate());

            label.setResponder(str -> { this.labelText = str != null ? str : ""; this.setDirty(); });
            xCoord.setResponder(str -> { this.xText = str != null ? str : ""; this.setDirty(); });
            yCoord.setResponder(str -> { this.yText = str != null ? str : ""; this.setDirty(); });
            zCoord.setResponder(str -> { this.zText = str != null ? str : ""; this.setDirty(); });
        }

        @Override
        public void save()
        {
            if (isDirty())
            {
                labelText = label.getText();
                xText = xCoord.getText();
                yText = yCoord.getText();
                zText = zCoord.getText();

                if (COORD_FORMAT_MATCHER.test(xText) && COORD_FORMAT_MATCHER.test(yText) && COORD_FORMAT_MATCHER.test(zText))
                {
                    pointInfo.setLabelText(labelText == null ? "" : labelText);
                    pointInfo.setPosition(new Vector3d(Double.parseDouble(xText), Double.parseDouble(yText), Double.parseDouble(zText)));
                }

                toUpdate.add(this);
            }
        }
    }

    private static class CompositeListItem extends ListItem
    {
        private final List<IRenderable> renderables = Lists.newArrayList();
        private final List<IGuiEventListener> listeners = Lists.newArrayList();

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

        public CompositeListItem addWidget(IRenderable widget)
        {
            if (widget instanceof IGuiEventListener)
                addListener((IGuiEventListener) widget);
            renderables.add(widget);
            return this;
        }

        public CompositeListItem addListener(IGuiEventListener listener)
        {
            listeners.add(listener);
            return this;
        }

        @Override
        public List<? extends IGuiEventListener> getEventListeners()
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
        public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks)
        {
            if (!isVisible())
                return;
            int actualMouseX = getActualX(mouseX);
            int actualMouseY = getActualY(mouseY);
            if ( actualMouseX >= 0 && actualMouseX < getWidth() && actualMouseY >= 0 && actualMouseY <= getHeight())
            {
                fill(matrixStack, 0, 0, getWidth(), getHeight(), 0x1fFFFFFF);
            }
            for (IRenderable i : renderables)
            {
                i.render(matrixStack, actualMouseX, actualMouseY, partialTicks);
            }
        }

        @Override
        public Optional<IGuiEventListener> getEventListenerForPos(double mouseX, double mouseY) {
            if (!isVisible())
                return Optional.empty();
            double actualMouseX = getActualX(mouseX);
            double actualMouseY = getActualY(mouseY);
            for(IGuiEventListener iguieventlistener : this.getEventListeners()) {
                if (iguieventlistener.isMouseOver(actualMouseX, actualMouseY)) {
                    return Optional.of(iguieventlistener);
                }
            }

            return Optional.empty();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isVisible())
                return false;
            double actualMouseX = getActualX(mouseX);
            double actualMouseY = getActualY(mouseY);
            for(IGuiEventListener iguieventlistener : this.getEventListeners()) {
                if (iguieventlistener.mouseClicked(actualMouseX, actualMouseY, button)) {
                    this.setListener(iguieventlistener);
                    if (button == 0) {
                        this.setDragging(true);
                    }

                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (!isVisible())
                return false;
            double actualMouseX = getActualX(mouseX);
            double actualMouseY = getActualY(mouseY);
            this.setDragging(false);
            return this.getEventListenerForPos(mouseX, mouseY).filter((listener) -> {
                return listener.mouseReleased(actualMouseX, actualMouseY, button);
            }).isPresent();
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (!isVisible())
                return false;
            double actualMouseX = getActualX(mouseX);
            double actualMouseY = getActualY(mouseY);
            return this.getListener() != null && this.isDragging() && button == 0
                    && this.getListener().mouseDragged(actualMouseX, actualMouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (!isVisible())
                return false;
            double actualMouseX = getActualX(mouseX);
            double actualMouseY = getActualY(mouseY);
            return this.getEventListenerForPos(mouseX, mouseY).filter((listener) -> {
                return listener.mouseScrolled(actualMouseX, actualMouseY, delta);
            }).isPresent();
        }

        @Override
        public boolean changeFocus(boolean focus)
        {
            if (!isVisible())
                return false;
            IGuiEventListener iguieventlistener = this.getListener();
            boolean flag = iguieventlistener != null;
            if (flag && iguieventlistener.changeFocus(focus))
            {
                return true;
            }
            else
            {
                List<? extends IGuiEventListener> list = this.getEventListeners();
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

                ListIterator<? extends IGuiEventListener> listiterator = list.listIterator(i);
                BooleanSupplier booleansupplier = focus ? listiterator::hasNext : listiterator::hasPrevious;
                Supplier<? extends IGuiEventListener> supplier = focus ? listiterator::next : listiterator::previous;

                while (booleansupplier.getAsBoolean())
                {
                    IGuiEventListener iguieventlistener1 = supplier.get();
                    if (iguieventlistener1.changeFocus(focus))
                    {
                        this.setListener(iguieventlistener1);
                        return true;
                    }
                }

                this.setListener((IGuiEventListener) null);
                return false;
            }
        }
    }

    private static abstract class ListItem extends FocusableGui implements IRenderable
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
        public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks)
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

    private class ItemsScrollPanel extends ScrollPanel
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
            addItem(index >= 0 ? (index+1) : items.size(), item);
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
        protected void drawPanel(MatrixStack mStack, int entryRight, int relativeY, Tessellator tess, int mouseX, int mouseY)
        {
            mStack.push();
            mStack.translate(left, relativeY, 0);
            for(ListItem item : items)
            {
                if (item.isVisible())
                {
                    mStack.push();
                    mStack.translate(0, item.getTop(), 0);
                    item.render(mStack, mouseX, mouseY, partialTicks);
                    mStack.pop();
                }
            }
            mStack.pop();
        }

        @Override
        public List<? extends IGuiEventListener> getEventListeners()
        {
            return items;
        }

        public void recalculateHeight()
        {
            int totalHeight = 0;
            for(ListItem item : items)
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
            int baseY = this.top + border - (int)this.scrollDistance;
            return baseY;
        }

        public void saveAll()
        {
            items.forEach(item -> item.save());
        }

        public int getContentWidth()
        {
            return right-left-6;
        }

        public void removeItem(ListItem item)
        {
            item.setParent(null);
            items.remove(item);
            recalculateHeight();
        }

        public void scrollToItem(ListItem item)
        {
            int scrollOffset = item.getTop() - height/2 - item.getHeight();
            this.scrollDistance = MathHelper.clamp(scrollOffset, 0, Math.max(0, getContentHeight() - (height-border)));
        }

        @Override
        public void render(MatrixStack matrix, int mouseX, int mouseY, float partialTicks)
        {
            this.scrollDistance = MathHelper.clamp(scrollDistance, 0, Math.max(0, getContentHeight() - (height-border)));
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
    }
}
