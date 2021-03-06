package sonar.flux.client.gui;

import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import sonar.core.client.gui.GuiSelectionGrid;
import sonar.core.helpers.FontHelper;
import sonar.flux.FluxTranslate;
import sonar.flux.api.network.IFluxNetwork;
import sonar.flux.common.containers.ContainerAdminConfigurator;
import sonar.flux.network.ClientNetworkCache;

import java.util.List;

import static sonar.flux.connection.NetworkSettings.*;

public class GuiAdminConfigurator extends GuiSelectionGrid<IFluxNetwork> {

	public static final ResourceLocation bground = new ResourceLocation("fluxnetworks:textures/gui/admin_configurator.png");
	public static final ResourceLocation bars = new ResourceLocation("fluxnetworks:textures/gui/admin_configurator_bars.png");

	public GuiAdminConfigurator(EntityPlayer player) {
		super(new ContainerAdminConfigurator(player));
		yPos = 24;
		xPos = 3;
		eWidth = 250;
		eHeight = 32;
		gWidth = 1;
		gHeight = 12;
	}

	@Override
	public void onGridClicked(IFluxNetwork element, int x, int y, int pos, int button, boolean empty) {}

	@Override
	public void renderGridElement(IFluxNetwork element, int x, int y, int slot) {
		this.bindTexture(bars);
		this.drawTexturedModalRect(xPos+ x, yPos+y*eHeight, 0, 0, 250, 32);
		FontHelper.text(NETWORK_NAME.getValue(element), xPos+ x + 4, yPos+y*eHeight +6, NETWORK_COLOUR.getValue(element).getRGB());
		FontHelper.text(FluxTranslate.NETWORK_OWNER.t() + ": " + NETWORK_CACHED_NAME.getValue(element), xPos+ x + 4, yPos+y*eHeight +18, 0);
		FontHelper.text("" + NETWORK_ACCESS.getValue(element), xPos+ x + 4 + 200, yPos+y*eHeight +6, 0);
		FontHelper.text(FluxTranslate.UUID.t() + ": " + NETWORK_ID.getValue(element), xPos+ x + 4 + 200, yPos+y*eHeight +18, 0);
		RenderHelper.disableStandardItemLighting();
	}

	@Override
	public void renderStrings(int x, int y) {
		FontHelper.textCentre(FontHelper.translate("item.AdminConfigurator.name"), xSize, 10, GuiAbstractTab.midBlue);
	}

	@Override
	public void renderElementToolTip(IFluxNetwork element, int x, int y) {
		
	}

	@Override
	public List<IFluxNetwork> getGridList() {
		return ClientNetworkCache.instance().getAllNetworks();
	}

	@Override
	public ResourceLocation getBackground() {
		return bground;
	}

}
