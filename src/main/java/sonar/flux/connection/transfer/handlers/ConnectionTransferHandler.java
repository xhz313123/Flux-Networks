package sonar.flux.connection.transfer.handlers;

import com.google.common.collect.Lists;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import sonar.core.api.energy.EnergyType;
import sonar.core.api.energy.ITileEnergyHandler;
import sonar.core.api.utils.ActionType;
import sonar.flux.FluxNetworks;
import sonar.flux.api.energy.internal.IFluxTransfer;
import sonar.flux.api.energy.internal.ITransferHandler;
import sonar.flux.api.tiles.IFlux;
import sonar.flux.connection.NetworkSettings;
import sonar.flux.connection.transfer.ConnectionTransfer;
import sonar.flux.connection.transfer.ISidedTransfer;
import sonar.flux.connection.transfer.PhantomTransfer;
import sonar.flux.connection.transfer.SidedPhantomTransfer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

public class ConnectionTransferHandler extends FluxTransferHandler implements ITransferHandler {

	public final TileEntity tile;
	public final List<EnumFacing> validFaces;
	public boolean hasTransfers;
	public boolean wasChanged = true;

	public ConnectionTransferHandler(TileEntity tile, IFlux flux, List<EnumFacing> validFaces) {
		super(flux);
		this.tile = tile;
		this.validFaces = validFaces;
	}

	public Map<EnumFacing, IFluxTransfer> transfers = new HashMap<>();
	{
		for (EnumFacing face : EnumFacing.VALUES) {
			transfers.put(face, null);
		}
	}

	@Override
	public void onStartServerTick() {
		super.onStartServerTick();
		transfers.entrySet().stream().filter(E -> E.getValue() != null).forEach(E -> E.getValue().onStartServerTick());
	}

	@Override
	public void onEndWorldTick() {
		super.onEndWorldTick();
		transfers.entrySet().stream().filter(E -> E.getValue() != null).forEach(E -> E.getValue().onEndWorldTick());
	}

	public IFluxTransfer getValidPhantomTransfer(EnumFacing from, EnergyType energy_type, ActionType type) {
		if (getNetwork().isFakeNetwork()) {
			return null;
		}
		IFluxTransfer transfer = transfers.get(from);
		TileEntity expected_source = from == null ? null : tile.getWorld().getTileEntity(tile.getPos().offset(from));
		if (from == null || expected_source == null || (transfer instanceof ISidedTransfer && ((ISidedTransfer) transfer).getTile() != expected_source)) {
			if (type.shouldSimulate()) {
				transfer = transfers.getOrDefault(null, new PhantomTransfer(energy_type));
			} else {
				if (energy_type == EnergyType.EU) {
					Optional<Entry<EnumFacing, IFluxTransfer>> firstEUTransfer = transfers.entrySet().stream().filter(E -> E.getValue() != null && E.getValue().getEnergyType() == EnergyType.EU).findAny();
					if (firstEUTransfer.isPresent()) {
						return firstEUTransfer.get().getValue();
					}
				}
				transfer = transfers.computeIfAbsent(null, E -> new PhantomTransfer(energy_type));
			}

		} else if (transfer == null) {
			ITileEnergyHandler handler = FluxNetworks.TRANSFER_HANDLER.getTileHandler(expected_source, from);
			if (handler != null) {
				transfer = transfers.computeIfAbsent(from, E -> new ConnectionTransfer(this, handler, expected_source, from));
			} else {
				transfer = transfers.computeIfAbsent(from, E -> new SidedPhantomTransfer(energy_type, expected_source, from));
			}
		}
		return transfer;
	}

	public long addPhantomEnergyToNetwork(EnumFacing from, long maxReceive, EnergyType energy_type, ActionType type) {
		if(!flux.isActive()){
			return 0;
		}
		IFluxTransfer transfer = getValidPhantomTransfer(from, energy_type, type);
		if (transfer != null && getNetwork().canTransfer(energy_type) && getNetwork().canConvert(energy_type, getNetwork().getSetting(NetworkSettings.NETWORK_ENERGY_TYPE))) {
			long added = addToBuffer(maxReceive, energy_type, type.shouldSimulate());
            if (!type.shouldSimulate()) {
				transfer.addedToNetwork(added, energy_type);
			}
			return added;
		}
		return 0;
	}

	/* WE ONLY SUPPORT A PUSH BASED MODEL
	public long removePhantomEnergyFromNetwork(EnumFacing from, long maxReceive, EnergyType energy_type, ActionType type) {
		IFluxTransfer transfer = getValidPhantomTransfer(from, energy_type, type);
		if (transfer != null && getNetwork().canTransfer(energy_type)) {
			// FIXME this could override priority!!!!
			long removed = flux.getNetwork().removePhantomEnergyFromNetwork(getValidRemoval(maxReceive, energy_type), energy_type, type);
			if (!type.shouldSimulate()) {
				transfer.removedFromNetwork(removed, energy_type);
				max_remove -= FluxNetworks.TRANSFER_HANDLER.convert(removed, energy_type, getNetwork().getDefaultEnergyType());
			}
			return removed;
		}
		return 0;
	}
	*/

	@Override
	public List<IFluxTransfer> getTransfers() {
		return Lists.newArrayList(transfers.values());
	}

	public void setTransfer(EnumFacing face, TileEntity tile) {
		IFluxTransfer transfer = transfers.get(face);
		ITileEnergyHandler handler;
		if (tile == null || (handler = FluxNetworks.TRANSFER_HANDLER.getTileHandler(tile, face.getOpposite())) == null) {
			transfers.put(face, null);
		} else if (!(transfer instanceof ConnectionTransfer) || ((ConnectionTransfer) transfer).getTile() != tile) {
			ConnectionTransfer newTransfer = new ConnectionTransfer(this, handler, tile, face);
			transfers.put(face, newTransfer);
		} else if (transfer.isInvalid()) {
			transfers.put(face, null);
		}
	}

	public void updateTransfers(EnumFacing... faces) {
		boolean change = false;
		for (EnumFacing face : faces) {
			if (validFaces.contains(face)) {
				BlockPos neighbour_pos = tile.getPos().offset(face);
				TileEntity neighbour_tile = tile.getWorld().getTileEntity(neighbour_pos);

				boolean wasConnected = transfers.get(face) != null;
				setTransfer(face, neighbour_tile);
				boolean isConnected = transfers.get(face) != null;
				if (wasConnected != isConnected) {
					change = true;
				}
			}
		}
		wasChanged = change;
	}

	@Override
	public boolean hasTransfers() {
		return hasTransfers;
	}

}
